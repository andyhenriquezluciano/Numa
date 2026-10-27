#include "listening_party.h"
#include "party_protocol.h"
#include "auth/auth_manager.h"
#include "playback/playback_controller.h"
#include <QJsonArray>
#include <QJsonDocument>
#include <QNetworkReply>
#include <QScopedValueRollback>
#include <QUrlQuery>
#include <QClipboard>
#include <QGuiApplication>
#include <QDateTime>

ListeningParty::ListeningParty(PlaybackController *playback, AuthManager *auth, QObject *parent)
    : QObject(parent), m_playback(playback), m_auth(auth) {
  playback->setProperty("listeningParty", QVariant::fromValue(static_cast<QObject *>(this)));
  m_publishSoon.setSingleShot(true);
  m_publishSoon.setInterval(40);
  connect(&m_publishSoon, &QTimer::timeout, this, &ListeningParty::publish);
  connect(playback, &PlaybackController::stateChanged, this, [this] {
    if (m_host && m_connected && !m_applying &&
        (m_publishedId != m_playback->track().value("id").toString() || m_publishedPlaying != m_playback->playing()) && !m_publishSoon.isActive()) m_publishSoon.start();
  });
  connect(playback, &PlaybackController::queueChanged, this, [this] {
    if (m_host && m_connected && !m_applying && !m_publishSoon.isActive()) m_publishSoon.start();
  });
  m_tick.setInterval(250);
  connect(&m_tick, &QTimer::timeout, this, [this] {
    if (m_host) { if (QDateTime::currentMSecsSinceEpoch() - m_lastPublish >= 2500) publish(); return; }
    if (!m_connected) return;
    const auto now = QDateTime::currentMSecsSinceEpoch();
    if (now - m_lastProbe > 5000) {
      m_lastProbe = now;
      send({{"type", "party:request"}, {"payload", QJsonObject{{"action", "sync-clock"}, {"clientSentAt", now}}}});
    }
    if (!m_latestState.isEmpty() && !m_playback->loading()) applyState(m_latestState);
  });
  m_handshake.setSingleShot(true);
  m_handshake.setInterval(10000);
  connect(&m_handshake, &QTimer::timeout, this, [this] { reset(tr("La conexión a la sala agotó el tiempo de espera.")); });
  connect(&m_socket, &QWebSocket::connected, this, [this] {
    m_handshake.stop();
    m_connected = true;
    emit noticeRequested(m_host ? tr("Sala creada. Comparte el código %1.").arg(code()) : tr("Te has unido a la sala compartida."));
    emit changed();
    m_tick.start();
    publish();
  });
  connect(&m_socket, &QWebSocket::textMessageReceived, this, &ListeningParty::receive);
  connect(&m_socket, &QWebSocket::disconnected, this, [this] {
    if (m_active) { reset(tr("La sala se desconectó.")); emit noticeRequested(tr("La sala se desconectó.")); }
  });
  connect(&m_socket, &QWebSocket::errorOccurred, this, [this](QAbstractSocket::SocketError) {
    if (m_active) reset(tr("No se pudo conectar a la sala compartida."));
  });
}

ListeningParty::~ListeningParty() {
  if (m_host && m_connected) send({{"type", "party:close"}});
  m_playback->setProperty("listeningParty", QVariant());
  m_socket.close();
}

void ListeningParty::create() { start("/rooms", {{"hostName", m_auth->userName().isEmpty() ? "NÜMA PC" : m_auth->userName()}}); }

void ListeningParty::join(const QString &value) {
  const auto code = party_protocol::cleanCode(value);
  if (code.size() != 6) { m_message = tr("Introduce el código de seis caracteres."); emit changed(); return; }
  start("/rooms/" + code + "/join", {{"name", m_auth->userName().isEmpty() ? "NÜMA PC" : m_auth->userName()}});
}

void ListeningParty::start(const QString &path, const QJsonObject &body) {
  if (m_active) return;
  if (!m_auth->isSignedIn()) { m_message = tr("Inicia sesión en YouTube Music para escuchar con otros dispositivos."); emit changed(); return; }
  if (m_playback->remoteActive()) { m_message = tr("Selecciona la reproducción local antes de compartir una sala."); emit changed(); return; }
  reset();
  m_active = true;
  const auto generation = m_generation;
  emit changed();
  QNetworkRequest request(QUrl(QStringLiteral("https://numa-party.peluchito281.workers.dev") + path));
  request.setHeader(QNetworkRequest::ContentTypeHeader, "application/json");
  request.setTransferTimeout(10000);
  auto *reply = m_network.post(request, QJsonDocument(body).toJson(QJsonDocument::Compact));
  connect(reply, &QNetworkReply::finished, this, [this, reply, generation] {
    reply->deleteLater();
    if (generation != m_generation) return;
    const auto response = QJsonDocument::fromJson(reply->readAll()).object();
    if (reply->error() != QNetworkReply::NoError || !response.value("ok").toBool()) {
      reset(tr("No se pudo abrir la sala. Comprueba el código y la conexión.")); return;
    }
    const auto data = response.value("data").toObject();
    m_room = data.value("room").toObject();
    m_participant = data.value("participant").toObject();
    m_host = m_participant.value("role").toString() == "host";
    const auto id = m_participant.value("id").toString();
    const auto token = m_participant.value("token").toString();
    if (code().isEmpty() || id.isEmpty() || token.isEmpty()) { reset(tr("El servicio devolvió una sala no válida.")); return; }
    QUrl url(m_room.value("socketUrl").toString());
    if (url.isEmpty()) url = QUrl("wss://numa-party.peluchito281.workers.dev/rooms/" + code() + "/socket");
    if (url.scheme() != "wss" || url.host() != "numa-party.peluchito281.workers.dev") { reset(tr("Dirección de sala no válida.")); return; }
    QUrlQuery query(url);
    query.addQueryItem("participantId", id);
    query.addQueryItem("token", token);
    url.setQuery(query);
    m_handshake.start();
    m_socket.open(url);
    emit changed();
  });
}

void ListeningParty::send(const QJsonObject &message) {
  if (!m_connected) return;
  const auto bytes = message.value("type") == "party:update" ? party_protocol::boundedMessage(message)
      : QJsonDocument(message).toJson(QJsonDocument::Compact);
  if (!bytes.isEmpty() && bytes.size() <= 60000) m_socket.sendTextMessage(QString::fromUtf8(bytes));
}

void ListeningParty::leave() {
  if (!m_active) return;
  if (!m_host || !m_connected) { reset(); emit noticeRequested(tr("Has salido de la sala compartida.")); return; }
  send({{"type", "party:close"}});
  const auto generation = m_generation;
  QTimer::singleShot(1500, this, [this, generation] { if (generation == m_generation) { reset(); emit noticeRequested(tr("La sala compartida ha finalizado.")); } });
}

void ListeningParty::transferHost(const QString &id) {
  if (m_host && !id.isEmpty()) send({{"type", "party:host-transfer"}, {"participantId", id}});
}

void ListeningParty::copyCode() {
  if (!code().isEmpty()) QGuiApplication::clipboard()->setText(code());
}

void ListeningParty::reset(const QString &message) {
  ++m_generation;
  m_active = m_connected = m_host = false;
  m_tick.stop();
  m_handshake.stop();
  m_socket.abort();
  if (m_guestPlayback) {
    QScopedValueRollback<bool> applying(m_applying, true);
    m_playback->stop();
    m_playback->clearQueue();
  }
  m_guestPlayback = false;
  m_playback->setPartyPlaybackRate(1.0);
  m_publishSoon.stop();
  m_latestState = m_clockReply = {};
  m_clockOffset = m_lastProbe = 0;
  m_lastPublish = m_lastCorrection = 0;
  m_bestRtt = 10001;
  m_room = m_participant = {};
  m_peers.clear();
  m_message = message;
  emit changed();
}

void ListeningParty::receive(const QString &text) {
  if (!m_active) return;
  const auto message = QJsonDocument::fromJson(text.toUtf8()).object();
  const auto type = message.value("type").toString();
  if (type == "party:welcome") {
    const auto room = message.value("room").toObject();
    if (!room.isEmpty()) m_room = room;
    const auto role = message.value("role").toString();
    if (!role.isEmpty()) m_host = role == "host";
    m_peers = message.value("peers").toArray().toVariantList();
    if (!m_host) { m_latestState = message.value("lastState").toObject().value("payload").toObject(); applyState(m_latestState); }
    else publish();
  } else if (type == "peer:joined") {
    const auto peer = message.value("peer").toObject().toVariantMap();
    if (!peer.value("id").toString().isEmpty()) m_peers.append(peer);
    emit noticeRequested(tr("%1 se ha unido a la sala.").arg(peer.value("name", tr("Un participante")).toString()));
    publish();
  } else if (type == "peer:left") {
    const auto id = message.value("participantId").toString();
    if (!m_host && party_protocol::hostDeparted(id, m_room.value("hostId").toString())) {
      reset(); emit noticeRequested(tr("El anfitrión ha salido. La sala compartida se ha cerrado.")); return;
    }
    for (qsizetype i = m_peers.size(); i-- > 0;) if (m_peers[i].toMap().value("id").toString() == id) m_peers.removeAt(i);
  } else if (type == "party:update" && !m_host) {
    m_latestState = message.value("payload").toObject();
    const auto probe = m_latestState.value("clockProbe").toObject();
    if (probe.value("participantId") == m_participant.value("id")) {
      const auto now = QDateTime::currentMSecsSinceEpoch();
      const auto sent = static_cast<qint64>(probe.value("clientSentAt").toDouble());
      const auto rtt = now - sent;
      if (sent > 0 && rtt >= 0 && rtt <= m_bestRtt) {
        m_bestRtt = rtt;
        m_clockOffset = static_cast<qint64>(m_latestState.value("sentAt").toDouble()) - (sent + rtt / 2);
      }
    }
    applyState(m_latestState);
  } else if (type == "party:request" && m_host) {
    auto payload = message.value("payload").toObject();
    if (!party_protocol::guestRequestAllowed(payload.value("action").toString())) return;
    if (payload.value("action") == "sync-clock") m_clockReply = QJsonObject{{"participantId", message.value("from")}, {"clientSentAt", payload.value("clientSentAt")}};
    publish();
  } else if (type == "party:host-changed") {
    const auto id = message.value("hostId").toString();
    m_room.insert("hostId", id);
    m_host = id == m_participant.value("id").toString();
    if (m_host) m_playback->setPartyPlaybackRate(1.0);
    for (auto &value : m_peers) { auto peer = value.toMap(); peer["role"] = peer.value("id").toString() == id ? "host" : "guest"; value = peer; }
    publish();
  } else if (type == "party:closed") {
    reset(); emit noticeRequested(tr("La sala compartida ha finalizado.")); return;
  } else if (type == "error") {
    m_message = tr("El servicio no pudo completar la solicitud de la sala.");
  }
  emit changed();
}
