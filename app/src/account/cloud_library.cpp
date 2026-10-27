#include "account/cloud_library.h"
#include "account/cloud_json.h"
#include "account/numa_account.h"
#include "playback/playback_controller.h"
#include "home/home_controller.h"
#include "library/library_actions.h"
#include <QCryptographicHash>
#include <QDateTime>
#include <QDir>
#include <QFile>
#include <QJsonArray>
#include <QJsonDocument>
#include <QNetworkReply>
#include <QSaveFile>
#include <QStandardPaths>
#include <QUuid>
#include <algorithm>

namespace {
QJsonObject read(const QString &path) { QFile file(path); if (!file.exists()) return {}; if (!file.open(QIODevice::ReadOnly)) return {}; return QJsonDocument::fromJson(file.readAll()).object(); }
bool write(const QString &path, const QJsonObject &value) { QSaveFile out(path); return out.open(QIODevice::WriteOnly) && out.write(QJsonDocument(value).toJson(QJsonDocument::Compact)) >= 0 && out.commit(); }
QString hash(const QString &id) { return QString::fromLatin1(QCryptographicHash::hash(id.toUtf8(), QCryptographicHash::Sha256).toHex()); }
}
CloudLibrary::CloudLibrary(NumaAccount *account, PlaybackController *playback, HomeController *home, LibraryActions *actions, QObject *parent)
    : QObject(parent), m_account(account), m_playback(playback), m_home(home),
      m_events(account, nullptr, QStringLiteral("/sync/events"), QStringLiteral("numa-library.1")) {
    m_root = QStandardPaths::writableLocation(QStandardPaths::AppDataLocation) + "/account-sync"; QDir().mkpath(m_root);
    auto install = read(m_root + "/install.json"); m_owner = install.value("owner").toString(); m_client = install.value("client").toString();
    if (m_client.isEmpty()) m_client = QUuid::createUuid().toString(QUuid::WithoutBraces);
    m_data = read(accountPath());
    m_client = m_data.value("client").toString(QUuid::createUuid().toString(QUuid::WithoutBraces)); m_data.insert("client", m_client);
    write(m_root + "/install.json", {{"owner", m_owner}, {"client", m_client}});
    m_status = QStringLiteral("Inicia sesión en NÜMA para sincronizar tu biblioteca.");
    m_debounce.setSingleShot(true); m_debounce.setInterval(2000); m_poll.setInterval(60000);
    m_flush.setSingleShot(true); m_flush.setInterval(1000); m_sample.setInterval(1000); m_elapsed.start();
    connect(&m_debounce, &QTimer::timeout, this, &CloudLibrary::synchronize);
    connect(&m_poll, &QTimer::timeout, this, &CloudLibrary::synchronize);
    connect(&m_flush, &QTimer::timeout, this, &CloudLibrary::save);
    connect(&m_sample, &QTimer::timeout, this, &CloudLibrary::sample);
    connect(account, &NumaAccount::statusChanged, this, &CloudLibrary::bind);
    connect(account, &NumaAccount::userChanged, this, &CloudLibrary::bind);
    connect(&m_events, &ConnectHubSocket::opened, this, &CloudLibrary::retry);
    connect(&m_events, &ConnectHubSocket::message, this, [this](const QString &text) {
        if (QJsonDocument::fromJson(text.toUtf8()).object().value("type") == "library.changed") retry();
    });
    connect(home, &HomeController::libraryChanged, this, &CloudLibrary::observeLibrary);
    connect(home, &HomeController::playlistsLoaded, this, &CloudLibrary::observePlaylists);
    connect(actions, &LibraryActions::trackLikeChanged, this, [this](const QVariantMap &track, const QString &, const QString &, bool liked) { setSaved("song", track, liked); });
    connect(actions, &LibraryActions::playlistDeleted, this, [this](const QString &id) { setSaved("playlist", {{"id", id}}, false); });
    connect(playback, &PlaybackController::stateChanged, this, &CloudLibrary::sample);
    connect(playback, &PlaybackController::listenStarted, this, [this] { sample(); m_heard = 0; m_counted = false; m_wasPlaying = false; });
    m_sample.start(); m_poll.start(); bind();
}
CloudLibrary::~CloudLibrary() { sample(); save(); }
QString CloudLibrary::accountPath() const { return m_root + "/" + hash(m_owner) + ".json"; }
QString CloudLibrary::assetPath(const QString &digest) const { const auto dir = m_root + "/" + hash(m_owner) + "-covers"; QDir().mkpath(dir); return dir + "/" + digest + ".webp"; }
QString CloudLibrary::coverKey(const QString &id) const { return hash(cloud::playlistId(id)); }
void CloudLibrary::bind() {
    const auto owner = m_account->isSignedIn() ? m_account->userId() : QString();
    if (owner.isEmpty()) {
        m_events.stop();
        ++m_generation; if (m_reply) m_reply->abort(); m_busy = false;
        m_status = QStringLiteral("Inicia sesión en NÜMA para sincronizar tu biblioteca."); emit changed(); return;
    }
    if (owner != m_owner) {
        m_events.stop();
        ++m_generation; if (m_reply) m_reply->abort(); save();
        const bool adopt = m_owner.isEmpty(); m_owner = owner;
        if (!adopt) m_data = read(accountPath());
        else {
            const auto uploads = m_data.value("uploads").toObject();
            for (auto it = uploads.begin(); it != uploads.end(); ++it) recoverCover(it.key());
        }
        m_client = m_data.value("client").toString(QUuid::createUuid().toString(QUuid::WithoutBraces)); m_data.insert("client", m_client);
        m_busy = false; write(m_root + "/install.json", {{"owner", m_owner}, {"client", m_client}}); save();
        ++m_revision; emit changed();
    }
    m_events.start(); retry();
}
bool CloudLibrary::save() { const bool ok = write(accountPath(), m_data); if (!ok) fail(QStringLiteral("No se pudo guardar la copia local. Tus cambios no se subirán todavía.")); return ok; }
void CloudLibrary::retry() { m_debounce.start(); }
void CloudLibrary::syncNow() {
    if (m_busy) return;
    if (!m_account->isSignedIn()) { fail(QStringLiteral("Inicia sesión en tu cuenta NÜMA para sincronizar. La sesión de YouTube Music es independiente.")); return; }
    if (m_owner != m_account->userId()) bind();
    m_debounce.stop();
    synchronize();
}
void CloudLibrary::fail(const QString &message) { m_busy = false; m_status = message; emit changed(); }
void CloudLibrary::setSaved(const QString &kind, const QVariantMap &media, bool saved) {
    auto local = m_data.value("local").toObject(), row = cloud::fromDesktop(media, kind); const auto id = row.value("id").toString(); if (id.isEmpty()) return;
    QJsonArray rows; for (auto value : local.value(cloud::field(kind)).toArray()) if ((kind == "playlist" ? cloud::playlistId(value.toObject().value("id").toString()) : value.toObject().value("id").toString()) != id) rows.append(value);
    if (kind == "playlist") {
        QJsonArray hidden;
        for (auto entry : local.value("hiddenPlaylistIds").toArray()) if (cloud::playlistId(entry.toString()) != id) hidden.append(entry);
        if (!saved) hidden.append(id);
        local.insert("hiddenPlaylistIds", hidden);
    }
    if (saved) rows.append(row); local.insert(cloud::field(kind), rows); m_data.insert("local", local); save(); ++m_revision; emit changed(); retry();
    if (kind == "playlist" && !saved) emit playlistRemoved(id);
}
void CloudLibrary::toggleSaved(const QString &kind, const QVariantMap &media) {
    const auto id = cloud::fromDesktop(media, kind).value("id").toString(); setSaved(kind, media, !isSaved(kind, id));
}
bool CloudLibrary::isSaved(const QString &kind, const QString &id) const {
    for (auto value : m_data.value("local").toObject().value(cloud::field(kind)).toArray()) if ((kind == "playlist" ? cloud::playlistId(value.toObject().value("id").toString()) : value.toObject().value("id").toString()) == (kind == "playlist" ? cloud::playlistId(id) : id)) return kind != "playlist" || !isHiddenPlaylist(id); return false;
}
QVariantList CloudLibrary::visiblePlaylists(const QVariantList &items) const {
    return cloud::visibleMedia(items, m_data.value("local").toObject().value("hiddenPlaylistIds").toArray(), true);
}
QVariantList CloudLibrary::visibleSections(const QVariantList &sections) const {
    return cloud::visibleSections(sections, m_data.value("local").toObject().value("hiddenPlaylistIds").toArray());
}
bool CloudLibrary::isHiddenPlaylist(const QString &id) const {
    for (auto value : m_data.value("local").toObject().value("hiddenPlaylistIds").toArray()) if (cloud::playlistId(value.toString()) == cloud::playlistId(id)) return true;
    return false;
}
void CloudLibrary::restoreDefaultPlaylists() {
    const auto local = m_data.value("local").toObject();
    const auto restored = cloud::restoreDefaultPlaylists(local);
    if (restored == local) return;
    m_data.insert("local", restored); save(); ++m_revision; emit changed(); retry();
}
void CloudLibrary::removePlaylist(const QString &id) { setSaved("playlist", {{"id", id}}, false); }
void CloudLibrary::observeLibrary() {
    if (m_home->libraryLoading() || !m_home->libraryError().isEmpty()) return;
    auto local = m_data.value("local").toObject(), observed = m_data.value("nativeObserved").toObject();
    for (const auto &value : m_home->librarySections()) {
        const auto section = value.toMap(); if (!section.value("error").toString().isEmpty()) continue;
        const auto key = section.value("key").toString(); QString kind;
        if (key == "artists") kind = "artist"; else if (key == "albums") kind = "album"; else if (key == "songs") kind = "song"; else continue;
        QJsonObject current; for (const auto &item : section.value("items").toList()) { auto row = cloud::fromDesktop(item.toMap(), kind); if (!row.value("id").toString().isEmpty()) current.insert(row.value("id").toString(), row); }
        const auto previous = observed.value(key).toObject();
        const auto rows = cloud::mergeRows(previous, current, local.value(cloud::field(kind)).toArray());
        local.insert(cloud::field(kind), rows); observed.insert(key, current);
    }
    m_data.insert("local", local); m_data.insert("nativeObserved", observed); save(); ++m_revision; emit changed(); retry();
}
void CloudLibrary::observePlaylists() {
    if (m_home->playlistsLoading() || !m_home->playlistsError().isEmpty()) return;
    auto local = m_data.value("local").toObject(), observed = m_data.value("nativeObserved").toObject();
    QJsonObject cached, current;
    for (auto value : local.value("savedPlaylists").toArray()) cached.insert(value.toObject().value("id").toString(), value);
    for (const auto &value : m_home->playlists()) {
        const auto media = value.toMap(); auto row = cloud::fromDesktop(media, "playlist");
        const auto id = row.value("id").toString(); if (id.isEmpty()) continue;
        const auto existing = cached.value(id).toObject();
        if (!media.contains("tracks") && existing.contains("tracks")) row.insert("tracks", existing.value("tracks"));
        current.insert(id, row);
    }
    const auto rows = cloud::mergeRows(observed.value("playlists").toObject(), current, local.value("savedPlaylists").toArray());
    local.insert("savedPlaylists", rows); observed.insert("playlists", current);
    m_data.insert("nativeObserved", observed);
    if (local == m_data.value("local").toObject()) { save(); return; }
    m_data.insert("local", local); save(); ++m_revision; emit changed(); retry();
}
void CloudLibrary::cacheCollection(const QVariantMap &detail) {
    const auto id = cloud::fromDesktop(detail, "playlist").value("id").toString();
    if (!detail.contains("tracks") || !isSaved("playlist", id)) return;
    auto local = m_data.value("local").toObject(); QJsonArray rows;
    for (auto value : local.value("savedPlaylists").toArray()) {
        auto row = value.toObject();
        if (cloud::playlistId(row.value("id").toString()) == id) row.insert("tracks", cloud::fromDesktop(detail, "playlist").value("tracks"));
        rows.append(row);
    }
    local.insert("savedPlaylists", rows);
    if (local == m_data.value("local").toObject()) return;
    m_data.insert("local", local); save(); ++m_revision; emit changed(); retry();
}
QVariantList CloudLibrary::section(const QString &kind) const {
    QVariantList out; auto local = m_data.value("local").toObject();
    auto rows = local.value(kind == "metrics" ? "playedTracks" : cloud::field(kind)).toArray();
    const auto counts = local.value("playCounts").toObject();
    const auto type = kind == "artists" ? "artist" : kind == "albums" ? "album" : kind == "playlists" ? "playlist" : "song";
    for (auto value : rows) {
        auto row = cloud::toDesktop(value.toObject(), type); const auto id = row.value("id").toString();
        if (type == "playlist" && isHiddenPlaylist(id)) continue;
        if (kind == "metrics") { const auto count = counts.value(id).toInt(); if (count < 1) continue; row.insert("listenCount", count); row.insert("subtitle", QString::number(count) + QStringLiteral(" escuchas · ") + row.value("artist").toString()); }
        row.insert("thumbnail", artworkFor(id, row.value("thumbnail").toString())); out.append(row);
    }
    if (kind == "metrics") std::stable_sort(out.begin(), out.end(), [](const QVariant &a, const QVariant &b) { return a.toMap().value("listenCount").toInt() > b.toMap().value("listenCount").toInt(); });
    return out;
}
