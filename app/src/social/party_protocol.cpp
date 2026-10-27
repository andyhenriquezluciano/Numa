#include "party_protocol.h"
#include <QJsonArray>
#include <QJsonDocument>
#include <algorithm>
#include <cmath>

namespace party_protocol {
bool guestRequestAllowed(const QString &action) { return action == QLatin1String("sync-clock"); }
bool hostDeparted(const QString &participantId, const QString &hostId) {
  return !hostId.isEmpty() && participantId == hostId;
}
double correctionRate(double errorSeconds, bool playing) {
  if (!playing || std::abs(errorSeconds) <= 0.01 || std::abs(errorSeconds) > 0.55) return 1.0;
  return std::clamp(1.0 + errorSeconds / 2.0, 0.98, 1.02);
}
QString cleanCode(const QString &value) {
  QString result;
  for (const QChar ch : value.trimmed().toUpper())
    if ((ch >= u'A' && ch <= u'Z') || (ch >= u'2' && ch <= u'9')) result += ch;
  return result;
}

QJsonObject encodeTrack(const QVariantMap &track) {
  QJsonObject result;
  for (const auto *key : {"id", "title", "artist", "artists", "album", "albumId", "thumbnail",
                          "durationSeconds", "explicit", "musicVideoType", "musicVideoId"})
    if (track.contains(QLatin1String(key))) result.insert(QLatin1String(key), QJsonValue::fromVariant(track.value(QLatin1String(key))));
  result.insert("type", "song");
  if (!result.contains("durationSeconds")) {
    double seconds = 0;
    for (const auto &part : track.value("duration").toString().split(':')) seconds = seconds * 60 + part.toDouble();
    result.insert("durationSeconds", seconds);
  }
  result.insert("artworkUrl", track.value("thumbnail").toString());
  if (result.value("artist").toString().isEmpty()) {
    const auto artists = QJsonValue::fromVariant(track.value("artists")).toArray();
    result.insert("artist", artists.isEmpty() ? track.value("subtitle").toString() : artists.first().toString());
  }
  return result;
}

QVariantMap decodeTrack(const QJsonObject &track) {
  auto result = encodeTrack(track.toVariantMap()).toVariantMap();
  result.insert("thumbnail", track.value("artworkUrl").toString(track.value("thumbnail").toString()));
  result.insert("artists", QStringList{track.value("artist").toString()});
  const double seconds = track.value("durationSeconds").toDouble(track.value("durationMs").toDouble() / 1000.0);
  result.insert("durationSeconds", std::isfinite(seconds) ? std::max(0.0, seconds) : 0.0);
  return result;
}

QByteArray boundedMessage(QJsonObject message) {
  auto payload = message.value("payload").toObject();
  auto queue = payload.value("queue").toArray();
  auto history = payload.value("history").toArray();
  while (queue.size() > 100) queue.removeLast();
  while (history.size() > 30) history.removeFirst();
  for (;;) {
    payload.insert("queue", queue);
    payload.insert("history", history);
    message.insert("payload", payload);
    auto bytes = QJsonDocument(message).toJson(QJsonDocument::Compact);
    if (bytes.size() <= 60000) return bytes;
    if (!history.isEmpty()) history.removeFirst();
    else if (!queue.isEmpty()) queue.removeLast();
    else return {};
  }
}

double projectedPosition(const QJsonObject &state, qint64 now) {
  double position = state.value("currentTime").toDouble();
  const qint64 sent = static_cast<qint64>(state.value("sentAt").toDouble(now));
  if (state.value("isPlaying").toBool()) position += std::clamp((now - sent) / 1000.0, 0.0, 10.0);
  return std::isfinite(position) ? std::max(0.0, position) : 0.0;
}
}
