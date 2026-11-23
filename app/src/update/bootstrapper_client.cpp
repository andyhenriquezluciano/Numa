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
#include <QCryptographicHash>
#include <QDateTime>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QJsonDocument>
#include <QJsonArray>
#include <QJsonObject>
#include <QProcess>
#include <QDesktopServices>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QRegularExpression>
#include <QSettings>
#include <QStandardPaths>
#include <QTemporaryDir>
#include <QVersionNumber>
#include <memory>
#ifdef Q_OS_WIN
#include <qt_windows.h>
#endif

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
           state == QLatin1String("staging") || state == QLatin1String("authorizing");
}

#ifdef Q_OS_WIN
QString updateHelper()
{
    return QDir(QCoreApplication::applicationDirPath()).filePath(QStringLiteral("numa-windows-updater.exe"));
}

QDir updateWorkDir()
{
    return QDir(QDir(QStandardPaths::writableLocation(QStandardPaths::CacheLocation))
                    .filePath(QStringLiteral("windows-updates")));
}
#endif

} // namespace

BootstrapperClient::BootstrapperClient(QObject *parent) : QObject(parent)
{
    const QString bootstrapper = qEnvironmentVariable("NUMA_BOOTSTRAPPER");
    m_root = qEnvironmentVariable("NUMA_INSTALL_ROOT");
    if (bootstrapper.isEmpty() || m_root.isEmpty() || !QFileInfo::exists(bootstrapper)) {
        m_current = QCoreApplication::applicationVersion();
#ifdef Q_OS_WIN
        const auto jobs = updateWorkDir().entryInfoList({QStringLiteral("job-*")}, QDir::Dirs | QDir::NoSymLinks);
        for (const QFileInfo &job : jobs) {
            if (job.lastModified().daysTo(QDateTime::currentDateTime()) > 1)
                QDir(job.absoluteFilePath()).removeRecursively();
        }
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
    fetchStandaloneMetadata(network, QUrl(QStringLiteral("https://github.com/andyhenriquezluciano/Numa/releases/latest/download/latest-windows.json")), false);
}

void BootstrapperClient::fetchStandaloneMetadata(QNetworkAccessManager *network, const QUrl &url, bool fallback)
{
    QNetworkRequest request(url);
    request.setTransferTimeout(20000);
    request.setRawHeader("User-Agent", "Numa-Windows");
    if (fallback)
        request.setRawHeader("Accept", "application/octet-stream");
    request.setAttribute(QNetworkRequest::CacheLoadControlAttribute, QNetworkRequest::AlwaysNetwork);
    auto *reply = network->get(request);
    connect(reply, &QNetworkReply::finished, this, [this, network, reply, fallback] {
        const QJsonObject metadata = QJsonDocument::fromJson(reply->read(512 * 1024)).object();
        const QString version = metadata.value(QStringLiteral("version")).toString();
        const QString installer = metadata.value(QStringLiteral("installerUrl")).toString();
        const QVersionNumber parsed = QVersionNumber::fromString(version);
        const bool valid = reply->error() == QNetworkReply::NoError && !parsed.isNull() &&
            metadata.value(QStringLiteral("platform")).toString() == QLatin1String("windows") &&
            installer.startsWith(QLatin1String("https://github.com/andyhenriquezluciano/Numa/releases/download/")) &&
            QUrl(installer).path().endsWith(QLatin1String(".exe"));
        if (!valid) {
            if (!fallback) {
                reply->deleteLater();
                checkStandaloneFallback(network);
                return;
            }
            m_state = QStringLiteral("error");
            m_error = tr("Could not check for updates. Please try again.");
        } else {
            m_latest = version;
            m_installerUrl = installer;
            const auto digest = metadata.value(QStringLiteral("sha256")).toString();
            static const QRegularExpression sha256(QStringLiteral("\\A[0-9a-fA-F]{64}\\z"));
            m_installerHash = metadata.value(QStringLiteral("installProtocol")).toString() == QLatin1String("nsis-wait-v1") &&
                    sha256.match(digest).hasMatch() ? QByteArray::fromHex(digest.toLatin1()) : QByteArray();
            m_releaseNotes = metadata.value(QStringLiteral("releaseNotes")).toString();
            m_state = QVersionNumber::compare(parsed, QVersionNumber::fromString(m_current)) > 0
                ? QStringLiteral("available") : QStringLiteral("up-to-date");
        }
        emit changed();
        network->deleteLater();
    });
}

void BootstrapperClient::checkStandaloneFallback(QNetworkAccessManager *network)
{
    // The release download gateway can fail while the public API still serves
    // the same asset. Discover its ID so replacing an asset needs no app update.
    QNetworkRequest request(QUrl(QStringLiteral("https://api.github.com/repos/andyhenriquezluciano/Numa/releases/latest")));
    request.setTransferTimeout(20000);
    request.setRawHeader("User-Agent", "Numa-Windows");
    request.setRawHeader("Accept", "application/vnd.github+json");
    request.setAttribute(QNetworkRequest::CacheLoadControlAttribute, QNetworkRequest::AlwaysNetwork);
    auto *reply = network->get(request);
    connect(reply, &QNetworkReply::finished, this, [this, network, reply] {
        const auto release = QJsonDocument::fromJson(reply->read(512 * 1024)).object();
        if (reply->error() == QNetworkReply::NoError &&
            !release.value(QStringLiteral("draft")).toBool(true) &&
            !release.value(QStringLiteral("prerelease")).toBool(true)) {
            for (const auto &value : release.value(QStringLiteral("assets")).toArray()) {
                const auto asset = value.toObject();
                const auto id = asset.value(QStringLiteral("id")).toInteger();
                if (asset.value(QStringLiteral("name")).toString() == QLatin1String("latest-windows.json") &&
                    asset.value(QStringLiteral("state")).toString() == QLatin1String("uploaded") && id > 0) {
                    reply->deleteLater();
                    fetchStandaloneMetadata(network, QUrl(QStringLiteral("https://api.github.com/repos/andyhenriquezluciano/Numa/releases/assets/%1").arg(id)), true);
                    return;
                }
            }
        }
        m_state = QStringLiteral("error");
        m_error = tr("Could not check for updates. Please try again.");
        emit changed();
        network->deleteLater();
    });
}

void BootstrapperClient::downloadInstaller()
{
    if (m_state != QLatin1String("available") || m_installerUrl.isEmpty())
        return;
    if (installsInApp())
        downloadStandaloneUpdate();
    else
        QDesktopServices::openUrl(QUrl(m_installerUrl));
}

bool BootstrapperClient::installsInApp() const
{
#ifdef Q_OS_WIN
    if (!m_bootstrapper.isEmpty() || m_installerHash.isEmpty() || !QFileInfo(updateHelper()).isFile())
        return false;
    const QSettings registry(QStringLiteral("HKEY_LOCAL_MACHINE\\Software\\Numa"), QSettings::Registry32Format);
    const QString installedDir = registry.value(QStringLiteral("Install_Dir")).toString();
    return !installedDir.isEmpty() && QDir::cleanPath(QDir::fromNativeSeparators(installedDir))
        .compare(QCoreApplication::applicationDirPath(), Qt::CaseInsensitive) == 0;
#else
    return false;
#endif
}

void BootstrapperClient::downloadStandaloneUpdate()
{
#ifdef Q_OS_WIN
    const auto fail = [this](const QString &error) {
        m_state = QStringLiteral("available");
        m_error = error;
        emit changed();
    };
    const QDir directory = updateWorkDir();
    if (!QDir().mkpath(directory.absolutePath())) {
        fail(tr("Could not save the update installer."));
        return;
    }
    auto job = std::make_shared<QTemporaryDir>(directory.filePath(QStringLiteral("job-XXXXXX")));
    auto file = std::make_shared<QFile>(job->filePath(QStringLiteral("Numa-Setup.exe")));
    if (!job->isValid() || !file->open(QIODevice::WriteOnly | QIODevice::NewOnly)) {
        fail(tr("Could not save the update installer."));
        return;
    }

    m_downloaded = m_total = 0;
    m_error.clear();
    m_state = QStringLiteral("downloading");
    emit changed();
    auto *network = new QNetworkAccessManager(this);
    QNetworkRequest request{QUrl(m_installerUrl)};
    request.setTransferTimeout(30000);
    request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::UserVerifiedRedirectPolicy);
    auto *reply = network->get(request);
    reply->setReadBufferSize(256 * 1024);
    connect(reply, &QNetworkReply::redirected, reply, [reply](const QUrl &url) {
        if (url.scheme() == QLatin1String("https") && url.host() == QLatin1String("release-assets.githubusercontent.com") &&
            url.userInfo().isEmpty() && url.port(443) == 443)
            emit reply->redirectAllowed();
        else
            reply->abort();
    });

    constexpr qint64 maxInstallerSize = 2LL * 1024 * 1024 * 1024;
    auto hash = std::make_shared<QCryptographicHash>(QCryptographicHash::Sha256);
    const auto writeChunk = [reply, file, hash] {
        const auto data = reply->readAll();
        if (file->size() + data.size() > maxInstallerSize || file->write(data) != data.size())
            return false;
        hash->addData(data);
        return true;
    };
    connect(reply, &QNetworkReply::readyRead, this, [reply, writeChunk] {
        if (!writeChunk())
            reply->abort();
    });
    connect(reply, &QNetworkReply::downloadProgress, this, [this](qint64 done, qint64 total) {
        m_downloaded = done;
        m_total = total;
        emit changed();
    });
    connect(reply, &QNetworkReply::finished, this, [this, job, file, hash, network, reply, writeChunk, fail] {
        network->deleteLater();
        const bool saved = reply->error() == QNetworkReply::NoError && writeChunk() && file->flush();
        file->close();
        if (!saved) {
            fail(tr("Could not download the update. Please try again."));
            return;
        }
        if (hash->result() != m_installerHash) {
            fail(tr("The downloaded installer could not be verified."));
            return;
        }
        const QString helper = job->filePath(QStringLiteral("numa-windows-updater.exe"));
        const QString status = job->filePath(QStringLiteral("status"));
        qint64 helperPid = 0;
        if (!QFile::copy(updateHelper(), helper) || !QProcess::startDetached(helper, {
            file->fileName(), status, QCoreApplication::applicationFilePath(),
            QString::number(QCoreApplication::applicationPid()), QString::fromLatin1(m_installerHash.toHex())
        }, job->path(), &helperPid)) {
            fail(tr("Could not start the Windows updater."));
            return;
        }
        job->setAutoRemove(false);
        const std::shared_ptr<void> process(OpenProcess(SYNCHRONIZE, FALSE, DWORD(helperPid)), [](HANDLE handle) {
            if (handle)
                CloseHandle(handle);
        });
        m_state = QStringLiteral("authorizing");
        emit changed();
        auto *poll = new QTimer(this);
        connect(poll, &QTimer::timeout, this, [poll, job, status, process, fail] {
            QFile file(status);
            const QByteArray result = file.open(QIODevice::ReadOnly) ? file.read(1) : QByteArray();
            file.close();
            if (result.isEmpty() && process && WaitForSingleObject(process.get(), 0) == WAIT_TIMEOUT)
                return;
            poll->stop();
            poll->deleteLater();
            if (result == "1") {
                QCoreApplication::quit();
            } else {
                job->setAutoRemove(true);
                fail(result == "0" ? tr("Installation canceled. NÜMA is still open.")
                                   : tr("Could not start the Windows installer."));
            }
        });
        poll->start(250);
    });
#endif
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
