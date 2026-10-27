#pragma once
#include <QJsonObject>
#include <QVariantMap>

namespace party_protocol {
QString cleanCode(const QString &value);
QJsonObject encodeTrack(const QVariantMap &track);
QVariantMap decodeTrack(const QJsonObject &track);
QByteArray boundedMessage(QJsonObject message);
double projectedPosition(const QJsonObject &state, qint64 now);
double correctionRate(double errorSeconds, bool playing);
bool guestRequestAllowed(const QString &action);
bool hostDeparted(const QString &participantId, const QString &hostId);
}
