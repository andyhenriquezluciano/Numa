#pragma once
#include <QObject>
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QTimer>
#include <QVariantList>
#include <QWebSocket>

class PlaybackController;
class AuthManager;

class ListeningParty : public QObject {
  Q_OBJECT
  Q_PROPERTY(bool active READ active NOTIFY changed)
  Q_PROPERTY(bool connected READ connected NOTIFY changed)
  Q_PROPERTY(bool host READ host NOTIFY changed)
  Q_PROPERTY(QString code READ code NOTIFY changed)
  Q_PROPERTY(QString message READ message NOTIFY changed)
  Q_PROPERTY(QVariantList peers READ peers NOTIFY changed)
public:
  ListeningParty(PlaybackController *playback, AuthManager *auth, QObject *parent = nullptr);
  ~ListeningParty() override;
  bool active() const { return m_active; }
  bool connected() const { return m_connected; }
  bool host() const { return m_host; }
  QString code() const { return m_room.value("id").toString(); }
  QString message() const { return m_message; }
  QVariantList peers() const { return m_peers; }
  Q_INVOKABLE void create();
  Q_INVOKABLE void join(const QString &code);
  Q_INVOKABLE void leave();
  Q_INVOKABLE void transferHost(const QString &id);
  Q_INVOKABLE void copyCode();
  bool intercept(const QString &action, const QVariantMap &args);
signals:
  void changed();
  void noticeRequested(const QString &message);
private:
  void start(const QString &path, const QJsonObject &body);
  void reset(const QString &message = {});
  void receive(const QString &text);
  void send(const QJsonObject &message);
  void publish();
  void applyState(const QJsonObject &state);
  PlaybackController *m_playback;
  AuthManager *m_auth;
  QNetworkAccessManager m_network;
  QWebSocket m_socket;
  QTimer m_tick;
  QTimer m_handshake;
  QTimer m_publishSoon;
  QString m_publishedId;
  bool m_publishedPlaying = false;
  QJsonObject m_room, m_participant;
  QJsonObject m_latestState, m_clockReply;
  qint64 m_clockOffset = 0, m_lastProbe = 0, m_bestRtt = 10001;
  qint64 m_lastPublish = 0, m_lastCorrection = 0;
  QVariantList m_peers;
  QString m_message;
  quint64 m_generation = 0;
  bool m_active = false, m_connected = false, m_host = false, m_applying = false;
  bool m_guestPlayback = false;
};
