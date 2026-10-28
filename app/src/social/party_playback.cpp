#include "listening_party.h"
#include "party_protocol.h"
#include "playback/playback_controller.h"
#include <QDateTime>
#include <QJsonArray>
#include <QJsonDocument>
#include <QScopedValueRollback>
#include <cmath>

namespace {
QJsonArray encodeTracks(const QVariantList &tracks, int limit) {
  QJsonArray result;
  for (const auto &value : tracks.mid(0, limit)) result.append(party_protocol::encodeTrack(value.toMap()));
  return result;
}
QVariantList decodeTracks(const QJsonArray &tracks, int limit) {
  QVariantList result;
  for (const auto &value : tracks) {
    const auto track = party_protocol::decodeTrack(value.toObject());
    if (!track.value("id").toString().isEmpty()) result.append(track);
    if (result.size() >= limit) break;
  }
  return result;
}
bool sameOrder(const QVariantList &a, const QVariantList &b) {
  if (a.size() != b.size()) return false;
  for (qsizetype i = 0; i < a.size(); ++i)
    if (a[i].toMap().value("id") != b[i].toMap().value("id")) return false;
  return true;
}
}

void ListeningParty::publish() {
  if (!m_connected || !m_host || m_applying) return;
  m_lastPublish = QDateTime::currentMSecsSinceEpoch();
  m_publishedId = m_playback->track().value("id").toString();
  m_publishedPlaying = m_playback->playing();
  QJsonObject state{{"reason", "clock"}, {"mediaKind", "audio"},
                    {"track", party_protocol::encodeTrack(m_playback->track())},
                    {"queue", encodeTracks(m_playback->queue(), 100)},
                    {"history", encodeTracks(m_playback->history().mid(qMax<qsizetype>(0, m_playback->history().size() - 30)), 30)},
                    {"isPlaying", m_playback->playing()}, {"currentTime", m_playback->playing() ? m_playback->audiblePosition() : m_playback->position()},
                    {"duration", m_playback->duration()}, {"sentAt", QDateTime::currentMSecsSinceEpoch()}};
  if (!m_clockReply.isEmpty()) { state.insert("clockProbe", m_clockReply); m_clockReply = {}; }
  send({{"type", "party:update"}, {"payload", state}});
}

void ListeningParty::applyState(const QJsonObject &state) {
  const auto track = party_protocol::decodeTrack(state.value("track").toObject());
  if (track.value("id").toString().isEmpty()) return;
  QScopedValueRollback<bool> applying(m_applying, true);
  m_guestPlayback = !m_host;
  const auto queue = decodeTracks(state.value("queue").toArray(), 100);
  const auto history = decodeTracks(state.value("history").toArray(), 30);
  const double position = party_protocol::projectedPosition(state, QDateTime::currentMSecsSinceEpoch() + m_clockOffset);
  const bool playing = state.value("isPlaying").toBool();
  if (m_playback->crossfadeActive() &&
      m_playback->transitionTrack().value("id") == track.value("id")) return;
  if (m_playback->track().value("id") != track.value("id")) {
    m_playback->setPartyPlaybackRate(1.0);
    if (playing && !m_playback->queue().isEmpty() && m_playback->queue().first().toMap().value("id") == track.value("id")) {
      m_playback->next();
      return;
    }
    auto tracks = history;
    tracks.append(track);
    tracks.append(queue);
    m_playback->playFrom(tracks, history.size(), position, playing);
  } else {
    if (m_playback->loading()) return;
    if (!sameOrder(queue, m_playback->queue())) {
      m_playback->replacePartyQueue(queue);
    }
    const auto now = QDateTime::currentMSecsSinceEpoch();
    const double local = playing ? m_playback->audiblePosition() : m_playback->position();
    const double error = position - local;
    m_playback->setPartyPlaybackRate(party_protocol::correctionRate(error, playing));
    if (std::abs(error) > (playing ? 0.55 : 0.04) && now - m_lastCorrection >= 1000) {
      m_lastCorrection = now;
      m_playback->seek(std::max(0.0, m_playback->position() + error));
    }
    if (playing != m_playback->playing()) { if (playing) m_playback->play(); else m_playback->pause(); }
  }
}

bool ListeningParty::intercept(const QString &action, const QVariantMap &args) {
  if (m_connected && m_host && !m_applying) QTimer::singleShot(0, this, &ListeningParty::publish);
  if (!m_active || m_host || m_applying) return false;
  m_message = tr("Only the host can control playback. Ask them to give you control.");
  emit changed();
  return true;
}
