#pragma once
#include <QObject>
#include <QJsonObject>
#include <QNetworkAccessManager>
#include <QPointer>
#include <QTimer>
#include <QElapsedTimer>
#include <QVariantList>
#include <QRectF>
#include <functional>
#include "connect/connect_hub_socket.h"

class NumaAccount;
class PlaybackController;
class HomeController;
class LibraryActions;
class QNetworkReply;

class CloudLibrary final : public QObject {
    Q_OBJECT
    Q_PROPERTY(QString status READ status NOTIFY changed)
    Q_PROPERTY(bool busy READ busy NOTIFY changed)
    Q_PROPERTY(QString coverError READ coverError NOTIFY changed)
    Q_PROPERTY(int revision READ revision NOTIFY changed)
    Q_PROPERTY(QString lastSynced READ lastSynced NOTIFY changed)
public:
    explicit CloudLibrary(NumaAccount *account, PlaybackController *playback, HomeController *home,
                          LibraryActions *actions, QObject *parent = nullptr);
    ~CloudLibrary() override;
    QString status() const { return m_status; }
    QString lastSynced() const { return m_lastSynced; }
    bool busy() const { return m_busy; }
    QString coverError() const { return m_coverError; }
    int revision() const { return m_revision; }
    Q_INVOKABLE void retry();
    Q_INVOKABLE void syncNow();
    void cacheCollection(const QVariantMap &detail);
    Q_INVOKABLE QVariantList section(const QString &kind) const;
    Q_INVOKABLE bool isSaved(const QString &kind, const QString &id) const;
    Q_INVOKABLE void toggleSaved(const QString &kind, const QVariantMap &media);
    Q_INVOKABLE QVariantList visiblePlaylists(const QVariantList &items) const;
    Q_INVOKABLE QVariantList visibleSections(const QVariantList &sections) const;
    Q_INVOKABLE bool isHiddenPlaylist(const QString &id) const;
    Q_INVOKABLE void restoreDefaultPlaylists();
    Q_INVOKABLE void removePlaylist(const QString &id);
    Q_INVOKABLE bool automaticCover(const QString &id) const;
    Q_INVOKABLE bool setCoverCrop(const QString &id, const QUrl &url, const QRectF &crop);
    Q_INVOKABLE void setCover(const QString &id, const QUrl &url);
    Q_INVOKABLE void clearCover(const QString &id);
    Q_INVOKABLE void setAutomaticCover(const QString &id, bool enabled);
    Q_INVOKABLE QString artworkFor(const QString &id, const QString &fallback) const;
signals:
    void changed();
    void playlistRemoved(const QString &id);
private:
    void bind();
    bool save();
    void observeLibrary();
    void observePlaylists();
    void setSaved(const QString &kind, const QVariantMap &media, bool saved);
    void sample();
    void updateAutomaticCover(const QVariantMap &track);
    void record(const QVariantMap &track, qint64 ms, bool listen);
    void synchronize();
    void finishSync(const QJsonObject &remote, const QJsonObject &sent, const QJsonObject &counters);
    void fetchCovers();
    bool recoverCover(const QString &digest) const;
    void request(const QString &path, const QByteArray &method, const QByteArray &body,
                 std::function<void(QByteArray)> done, bool image = false);
    void fail(const QString &message);
    QString accountPath() const;
    QString assetPath(const QString &hash) const;
    QString coverKey(const QString &id) const;
    NumaAccount *m_account;
    PlaybackController *m_playback;
    HomeController *m_home;
    ConnectHubSocket m_events;
    QNetworkAccessManager m_network;
    QPointer<QNetworkReply> m_reply;
    QTimer m_poll, m_debounce, m_flush, m_sample;
    QElapsedTimer m_elapsed;
    QJsonObject m_data;
    QString m_root, m_owner, m_client, m_status, m_lastSynced, m_coverError;
    QVariantMap m_lastTrack;
    qint64 m_heard{0};
    bool m_counted{false}, m_wasPlaying{false}, m_busy{false};
    quint64 m_generation{0};
    int m_revision{0};
};
