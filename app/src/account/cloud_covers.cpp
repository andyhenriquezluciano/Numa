#include "account/cloud_library.h"
#include "account/cloud_json.h"
#include "account/cloud_cover_crop.h"
#include "account/cloud_cover_files.h"
#include "playback/playback_controller.h"
#include <QCryptographicHash>
#include <QJsonArray>
#include <QImageReader>
#include <QBuffer>
#include <QFile>
#include <QFileInfo>
#include <QSaveFile>
#include <QRegularExpression>

QString CloudLibrary::artworkFor(const QString &id, const QString &fallback) const {
    const auto local = m_data.value("local").toObject();
    const auto styles = local.value("coverStyles").toObject();
    const auto legacyKey = cloud::playlistId(id) == "FEmusic_liked_videos"
        ? QString::fromLatin1(QCryptographicHash::hash(QByteArray("LM"), QCryptographicHash::Sha256).toHex()) : coverKey(id);
    const auto style = styles.value(coverKey(id)).toObject(styles.value(legacyKey).toObject());
    if (style.value("kind") == "custom") {
        const auto digest = style.value("asset").toString();
        if (digest.size() == 64 && !digest.contains(QRegularExpression("[^a-f0-9]")) && QFile::exists(assetPath(digest))) return QUrl::fromLocalFile(assetPath(digest)).toString();
    }
    const auto key = cloud::playlistId(id);
    const auto automatic = local.value("automaticCovers").toObject();
    if (automatic.value("mode:" + key).toBool()) {
        const auto deviceArt = m_data.value("deviceAutomaticArtwork").toObject().value(key).toString();
        if (QUrl(deviceArt).isLocalFile() && QFile::exists(QUrl(deviceArt).toLocalFile())) return deviceArt;
        const auto url = automatic.value("art:" + key).toString(); if (url.startsWith("https://")) return url;
    }
    return fallback;
}
void CloudLibrary::setCover(const QString &id, const QUrl &url) { setCoverCrop(id, url, {}); }
bool CloudLibrary::setCoverCrop(const QString &id, const QUrl &url, const QRectF &crop) {
    auto reject = [this](const QString &message) { m_coverError = message; fail(message); return false; };
    if (!url.isLocalFile() || QFileInfo(url.toLocalFile()).size() > 16 * 1024 * 1024) { return reject(QStringLiteral("Selecciona una imagen local de hasta 16 MB.")); }
    QImageReader reader(url.toLocalFile()); const auto size = reader.size();
    if (!size.isValid() || qint64(size.width()) * size.height() > 16000000) { return reject(QStringLiteral("Imagen no válida o demasiado grande.")); }
    reader.setAutoTransform(true);
    const auto image = cloud::cropCover(reader.read(), crop);
    QByteArray bytes; QBuffer buffer(&bytes); buffer.open(QIODevice::WriteOnly);
    if (image.isNull() || !image.save(&buffer, "WEBP", 85) || bytes.size() > 1048576) { return reject(QStringLiteral("No se pudo preparar la carátula.")); }
    const auto hash = QString::fromLatin1(QCryptographicHash::hash(bytes, QCryptographicHash::Sha256).toHex());
    QSaveFile file(assetPath(hash)); if (!file.open(QIODevice::WriteOnly) || file.write(bytes) != bytes.size() || !file.commit()) { return reject(QStringLiteral("No se pudo guardar la imagen.")); }
    auto local = m_data.value("local").toObject(), styles = local.value("coverStyles").toObject(), uploads = m_data.value("uploads").toObject();
    styles.insert(coverKey(id), QJsonObject{{"kind", "custom"}, {"asset", hash}}); local.insert("coverStyles", styles); uploads.insert(hash, true);
    auto automatic = local.value("automaticCovers").toObject(); automatic.insert("mode:" + cloud::playlistId(id), false); local.insert("automaticCovers", automatic);
    const auto previous = m_data;
    m_data.insert("local", local); m_data.insert("uploads", uploads);
    if (!save()) { m_data = previous; return reject(QStringLiteral("No se pudo guardar la carátula. Inténtalo de nuevo.")); }
    m_coverError.clear(); ++m_revision; emit changed(); retry();
    return true;
}
void CloudLibrary::clearCover(const QString &id) {
    auto local = m_data.value("local").toObject(), styles = local.value("coverStyles").toObject();
    auto automatic = local.value("automaticCovers").toObject(); automatic.insert("mode:" + cloud::playlistId(id), false); local.insert("automaticCovers", automatic);
    styles.insert(coverKey(id), QJsonObject{{"kind", "default"}}); local.insert("coverStyles", styles); m_data.insert("local", local); save(); ++m_revision; emit changed(); retry();
}
void CloudLibrary::setAutomaticCover(const QString &id, bool enabled) {
    const auto key = cloud::playlistId(id);
    auto local = m_data.value("local").toObject(), automatic = local.value("automaticCovers").toObject();
    if (enabled) {
        auto styles = local.value("coverStyles").toObject(); styles.insert(coverKey(id), QJsonObject{{"kind", "default"}}); local.insert("coverStyles", styles);
    }
    automatic.insert("mode:" + key, enabled); local.insert("automaticCovers", automatic); m_data.insert("local", local);
    if (enabled) updateAutomaticCover(m_playback->shownTrack());
    save(); ++m_revision; emit changed(); retry();
}

bool CloudLibrary::automaticCover(const QString &id) const {
    return m_data.value("local").toObject().value("automaticCovers").toObject().value("mode:" + cloud::playlistId(id)).toBool();
}
void CloudLibrary::updateAutomaticCover(const QVariantMap &track) {
    auto local = m_data.value("local").toObject();
    const auto previous = local.value("automaticCovers").toObject();
    const auto origin = cloud::playlistId(track.value("queueOrigin").toMap().value("id").toString());
    if (origin.isEmpty() || !previous.value("mode:" + origin).toBool()) return;
    const auto automatic = cloud::automaticArtwork(previous, track);
    const auto art = cloud::fromDesktop(track, "song").value("artworkUrl").toString();
    auto device = m_data.value("deviceAutomaticArtwork").toObject();
    const auto oldDevice = device;
    if (QUrl(art).isLocalFile() && QFile::exists(QUrl(art).toLocalFile())) device.insert(origin, art);
    else if (art.startsWith("https://")) device.remove(origin);
    if (automatic == previous && device == oldDevice) return;
    local.insert("automaticCovers", automatic); m_data.insert("local", local); m_data.insert("deviceAutomaticArtwork", device);
    save(); ++m_revision; emit changed(); retry();
}

bool CloudLibrary::recoverCover(const QString &digest) const {
    const auto target = assetPath(digest);
    if (cloud::copyVerifiedCover(target, target, digest)) return true;
    const auto anonymous = QString::fromLatin1(QCryptographicHash::hash(QByteArray(), QCryptographicHash::Sha256).toHex());
    return cloud::copyVerifiedCover(m_root + "/" + anonymous + "-covers/" + digest + ".webp", target, digest);
}
