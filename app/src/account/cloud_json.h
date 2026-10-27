#pragma once
#include <QJsonObject>
#include <QJsonArray>
#include <QVariantMap>
#include <QVariantList>

namespace cloud {
QJsonObject mergeMap(const QJsonObject &base, const QJsonObject &local, const QJsonObject &remote);
QJsonArray mergeRows(const QJsonObject &base, const QJsonObject &local, const QJsonArray &remote);
QJsonObject reconcile(const QJsonObject &sent, const QJsonObject &local, const QJsonObject &remote);
QJsonObject countersDelta(const QJsonObject &local, const QJsonObject &sent);
QJsonObject addPending(const QJsonObject &snapshot, const QJsonObject &pending);
QJsonObject fromDesktop(const QVariantMap &media, const QString &kind);
QVariantMap toDesktop(const QJsonObject &media, const QString &kind);
QJsonObject restoreDefaultPlaylists(const QJsonObject &library);
QJsonObject automaticArtwork(const QJsonObject &automatic, const QVariantMap &track);
QVariantList visibleMedia(const QVariantList &items, const QJsonArray &hidden, bool playlists = false);
QVariantList visibleSections(const QVariantList &sections, const QJsonArray &hidden);
QString playlistId(const QString &id);
QString field(const QString &kind);
}
