#include "social/party_protocol.h"
#include <QJsonArray>
#include <QJsonDocument>
#include <QtTest>

class PartyProtocolTest : public QObject {
  Q_OBJECT
private slots:
  void guestsAreReadOnlyUntilHostTransfer() {
    for (const auto *action : {"play", "pause", "next", "seek", "state", "play-track", "add-queue", "clear-queue", "remove-queue", "move-queue", "cycle-repeat", ""})
      QVERIFY(!party_protocol::guestRequestAllowed(QLatin1String(action)));
    QVERIFY(party_protocol::guestRequestAllowed(QStringLiteral("sync-clock")));
    QVERIFY(party_protocol::hostDeparted("host", "host"));
    QVERIFY(!party_protocol::hostDeparted("guest", "host"));
    QVERIFY(!party_protocol::hostDeparted("", ""));
  }
  void smallDriftUsesGentleRateCorrection() {
    QCOMPARE(party_protocol::correctionRate(0.005, true), 1.0);
    QVERIFY(party_protocol::correctionRate(0.03, true) > 1.0);
    QCOMPARE(party_protocol::correctionRate(0.20, true), 1.02);
    QCOMPARE(party_protocol::correctionRate(-0.20, true), 0.98);
    QCOMPARE(party_protocol::correctionRate(0.80, true), 1.0);
    QCOMPARE(party_protocol::correctionRate(0.20, false), 1.0);
  }
  void androidSnapshotUsesSecondsAndPublicArtwork() {
    const auto payload = QJsonDocument::fromJson("{\"id\":\"song-1\",\"artist\":\"Artista\",\"durationMs\":213000,\"artworkUrl\":\"https://example.com/cover.jpg\"}").object();
    const auto track = party_protocol::decodeTrack(payload);
    QCOMPARE(track.value("durationSeconds").toDouble(), 213.0);
    QCOMPARE(track.value("thumbnail").toString(), QStringLiteral("https://example.com/cover.jpg"));
    QCOMPARE(track.value("type").toString(), QStringLiteral("song"));
  }
  void desktopSnapshotMatchesAndroidTrackKeys() {
    const QVariantMap track{{"id", "song-1"}, {"title", "Canción"}, {"artists", QStringList{"Artista"}},
        {"duration", "3:33"}, {"thumbnail", "https://example.com/cover.jpg"},
        {"filePath", "C:/private/song.mp3"}, {"token", "secret"}};
    const auto wire = party_protocol::encodeTrack(track);
    QCOMPARE(wire.value("durationSeconds").toDouble(), 213.0);
    QCOMPARE(wire.value("artist").toString(), QStringLiteral("Artista"));
    QCOMPARE(wire.value("artworkUrl").toString(), track.value("thumbnail").toString());
    QVERIFY(!wire.contains("filePath"));
    QVERIFY(!wire.contains("token"));
  }
  void largeQueueFitsTheMobileSocketLimit() {
    QJsonArray queue;
    for (int i = 0; i < 150; ++i) queue.append(QJsonObject{{"id", QString::number(i)}, {"title", QString(1500, QChar(0x00DC))}});
    const auto bytes = party_protocol::boundedMessage({{"type", "party:update"},
        {"payload", QJsonObject{{"track", QJsonObject{{"id", "current"}}}, {"queue", queue}, {"history", queue}}}});
    QVERIFY(!bytes.isEmpty());
    QVERIFY(bytes.size() <= 60000);
    const auto state = QJsonDocument::fromJson(bytes).object().value("payload").toObject();
    QCOMPARE(state.value("track").toObject().value("id").toString(), QStringLiteral("current"));
    QVERIFY(state.value("queue").toArray().size() <= 100);
    QVERIFY(state.value("history").toArray().size() <= 30);
  }
  void pausedSnapshotsDoNotAdvanceAndStaleClocksAreBounded() {
    QCOMPARE(party_protocol::projectedPosition({{"currentTime", 12}, {"sentAt", 1000}}, 5000), 12.0);
    QCOMPARE(party_protocol::projectedPosition({{"currentTime", 12}, {"sentAt", 1000}, {"isPlaying", true}}, 3000), 14.0);
    QCOMPARE(party_protocol::projectedPosition({{"currentTime", 12}, {"sentAt", 1000}, {"isPlaying", true}}, 999999), 22.0);
  }
  void codesMatchAndroidNormalization() {
    QCOMPARE(party_protocol::cleanCode(" ab-23io01! "), QStringLiteral("AB23IO"));
  }
};
QTEST_GUILESS_MAIN(PartyProtocolTest)
#include "party_protocol_test.moc"
