/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

#include "update/bootstrapper_client.h"

#include <QCoreApplication>
#include <QDateTime>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonDocument>
#include <QJsonObject>
#include <QProcess>
#include <QDesktopServices>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QVersionNumber>

namespace {

QJsonObject readJson(const QString &path)
{
    QFile file(path);
    if (!file.open(QIODevice::ReadOnly))
        return {};
    return QJsonDocument::fromJson(file.readAll()).object();
}

bool busy(const QString &state)
{
    return state == QLatin1String("checking") || state == QLatin1String("downloading") ||
           state == QLatin1String("staging");
}

} // namespace

BootstrapperClient::BootstrapperClient(QObject *parent) : QObject(parent)
{
    const QString bootstrapper = qEnvironmentVariable("NUMA_BOOTSTRAPPER");
    m_root = qEnvironmentVariable("NUMA_INSTALL_ROOT");
    if (bootstrapper.isEmpty() || m_root.isEmpty() || !QFileInfo::exists(bootstrapper)) {
        m_current = QCoreApplication::applicationVersion();
#ifdef Q_OS_WIN
        QTimer::singleShot(3000, this, &BootstrapperClient::checkForUpdates);
#endif
        return;
    }
    m_bootstrapper = bootstrapper;
    m_current = qEnvironmentVariable("NUMA_INSTALLED_VERSION");
    // Two small file reads; the background updater can finish while we run.
    m_poll.setInterval(30000);
    connect(&m_poll, &QTimer::timeout, this, &BootstrapperClient::refresh);
    m_poll.start();
    refresh();
}

void BootstrapperClient::refresh()
{
    const QJsonObject install = readJson(QDir(m_root).filePath(QStringLiteral("install.json")));
    const QJsonObject status = readJson(QDir(m_root).filePath(QStringLiteral("update-state.json")));
    const QString pending = install.value(QStringLiteral("pending")).toString();

    QString state = status.value(QStringLiteral("state")).toString(QStringLiteral("idle"));
    // A live run owns the state; otherwise install.json is the truth, since a
    // "ready" status outlives the restart that applied it. A busy state nobody
    // has touched for five minutes belongs to a run that was killed.
    const qint64 age = QDateTime::currentSecsSinceEpoch() - status.value(QStringLiteral("updated")).toInteger();
    const bool alive = busy(state) && age < 300;
    if (busy(state) && !alive)
        state = QStringLiteral("idle");
    if (!pending.isEmpty() && !alive)
        state = QStringLiteral("ready");
    else if (state == QLatin1String("ready") || state == QLatin1String("available"))
        state = QStringLiteral("up-to-date");

    m_state = state;
    m_latest = pending.isEmpty() ? status.value(QStringLiteral("latest")).toString() : pending;
    m_channel = install.value(QStringLiteral("channel")).toString(QStringLiteral("stable"));
    m_autoUpdate = install.value(QStringLiteral("autoUpdate")).toBool(true);
    m_error = state == QLatin1String("error") ? status.value(QStringLiteral("error")).toString() : QString();
    m_downloaded = status.value(QStringLiteral("done")).toInteger();
    m_total = status.value(QStringLiteral("total")).toInteger();
    m_poll.setInterval(alive ? 500 : 30000);
    emit changed();
}

void BootstrapperClient::run(const QStringList &arguments, bool detached)
{
    if (m_bootstrapper.isEmpty())
        return;
    if (detached) {
        QProcess::startDetached(m_bootstrapper, arguments);
        // Pick up the first progress write quickly.
        m_poll.setInterval(500);
        m_poll.start();
        return;
    }
    auto *process = new QProcess(this);
    connect(process, &QProcess::finished, this, [this, process] {
        process->deleteLater();
        refresh();
    });
    process->start(m_bootstrapper, arguments);
}

void BootstrapperClient::checkForUpdates()
{
    if (busy(m_state))
        return;
    m_state = QStringLiteral("checking");
    m_error.clear();
    emit changed();
    if (m_bootstrapper.isEmpty()) {
        checkStandalone();
        return;
    }
    run({QStringLiteral("--update")}, true);
}

void BootstrapperClient::checkStandalone()
{
    auto *network = new QNetworkAccessManager(this);
    QNetworkRequest request(QUrl(QStringLiteral("https://github.com/andyhenriquezluciano/Numa/releases/latest/download/latest-windows.json")));
    request.setTransferTimeout(20000);
    request.setRawHeader("User-Agent", "Numa-Windows");
    request.setAttribute(QNetworkRequest::CacheLoadControlAttribute, QNetworkRequest::AlwaysNetwork);
    auto *reply = network->get(request);
    connect(reply, &QNetworkReply::finished, this, [this, network, reply] {
        const QJsonObject metadata = QJsonDocument::fromJson(reply->read(512 * 1024)).object();
        const QString version = metadata.value(QStringLiteral("version")).toString();
        const QString installer = metadata.value(QStringLiteral("installerUrl")).toString();
        const QVersionNumber parsed = QVersionNumber::fromString(version);
        const bool valid = reply->error() == QNetworkReply::NoError && !parsed.isNull() &&
            metadata.value(QStringLiteral("platform")).toString() == QLatin1String("windows") &&
            installer.startsWith(QLatin1String("https://github.com/andyhenriquezluciano/Numa/releases/download/")) &&
            QUrl(installer).path().endsWith(QLatin1String(".exe"));
        if (!valid) {
            m_state = QStringLiteral("error");
            m_error = tr("Could not check for updates. Please try again.");
        } else {
            m_latest = version;
            m_installerUrl = installer;
            m_releaseNotes = metadata.value(QStringLiteral("releaseNotes")).toString();
            m_state = QVersionNumber::compare(parsed, QVersionNumber::fromString(m_current)) > 0
                ? QStringLiteral("available") : QStringLiteral("up-to-date");
        }
        emit changed();
        network->deleteLater();
    });
}

void BootstrapperClient::downloadInstaller()
{
    if (m_state == QLatin1String("available") && !m_installerUrl.isEmpty())
        QDesktopServices::openUrl(QUrl(m_installerUrl));
}

void BootstrapperClient::cancelDownload()
{
    run({QStringLiteral("--cancel")}, false);
}

void BootstrapperClient::restartToUpdate()
{
    if (m_bootstrapper.isEmpty())
        return;
    QProcess::startDetached(m_bootstrapper, {QStringLiteral("--update-on-exit"), QStringLiteral("--wait-pid"),
                                             QString::number(QCoreApplication::applicationPid())});
    QCoreApplication::quit();
}

void BootstrapperClient::setChannel(const QString &channel)
{
    if (m_bootstrapper.isEmpty() || channel == m_channel)
        return;
    m_channel = channel;
    emit changed();
    // Switch, then fetch the other channel's build right away.
    auto *process = new QProcess(this);
    connect(process, &QProcess::finished, this, [this, process] {
        process->deleteLater();
        refresh();
        checkForUpdates();
    });
    process->start(m_bootstrapper, {QStringLiteral("--channel"), channel});
}

void BootstrapperClient::setAutoUpdate(bool enabled)
{
    m_autoUpdate = enabled;
    emit changed();
    run({QStringLiteral("--auto-update"), enabled ? QStringLiteral("on") : QStringLiteral("off")}, false);
}
