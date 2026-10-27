#include "account/cloud_json.h"
#include "account/cloud_cover_files.h"
#include <QTemporaryDir>
#include <QJsonArray>
#include <QtTest>

class CloudJsonTest : public QObject {
    Q_OBJECT
private slots:
    void acknowledgedRemoteOrderDoesNotCreateAnotherUpload() {
        const QJsonArray tracks{QJsonObject{{"id", "z"}}, QJsonObject{{"id", "a"}}};
        const QJsonArray days{QJsonObject{{"date", "2026-10-25"}, {"trackId", "z"}, {"listens", 1}, {"heardMs", 15000}},
                             QJsonObject{{"date", "2026-10-25"}, {"trackId", "a"}, {"listens", 1}, {"heardMs", 15000}}};
        const QJsonObject remote{{"likedTracks", tracks}, {"playedTracks", tracks}, {"recentlyPlayed", tracks},
            {"listeningDays", days}, {"hiddenPlaylistIds", QJsonArray{"z", "a"}}, {"playCounts", QJsonObject{}}, {"listeningTotalMs", 30000}};
        const auto merged = cloud::addPending(cloud::reconcile(remote, remote, remote), {});
        for (const auto *key : {"likedTracks", "playedTracks", "recentlyPlayed", "listeningDays", "hiddenPlaylistIds"})
            QCOMPARE(merged.value(key), remote.value(key));
    }
    void restoresMobileCollectionTracksForDesktopPlayback() {
        const auto row = cloud::toDesktop({{"id", "PL-shared"}, {"title", "Shared"},
            {"tracks", QJsonArray{QJsonObject{{"id", "song"}, {"artworkUrl", "https://image.test/song"}, {"durationMs", 123000}}}}}, "playlist");
        QCOMPARE(row.value("playlistId").toString(), QStringLiteral("PL-shared"));
        QCOMPARE(row.value("browsePayload").toMap().value("browseId").toString(), QStringLiteral("VLPL-shared"));
        QCOMPARE(row.value("totalTrackCount").toInt(), 1);
        const auto song = row.value("tracks").toList().first().toMap();
        QCOMPARE(song.value("durationSeconds").toDouble(), 123.);
        QCOMPARE(song.value("thumbnail").toString(), QStringLiteral("https://image.test/song"));
    }
    void recoversAdoptedCoverWithoutMovingOrTrustingUnverifiedBytes() {
        QTemporaryDir folder(QDir::currentPath() + "/build/tmp/cover-recovery-XXXXXX");
        QVERIFY(folder.isValid());
        const QByteArray bytes("saved-cover-content");
        const auto digest = QString::fromLatin1(QCryptographicHash::hash(bytes, QCryptographicHash::Sha256).toHex());
        const auto source = folder.filePath("unclaimed.webp"), target = folder.filePath("owner/covers/" + digest + ".webp");
        QFile input(source); QVERIFY2(input.open(QIODevice::WriteOnly), qPrintable(input.errorString())); QCOMPARE(input.write(bytes), bytes.size()); input.close();
        QVERIFY(cloud::copyVerifiedCover(source, target, digest));
        QVERIFY(QFile::exists(source));
        QFile restored(target); QVERIFY(restored.open(QIODevice::ReadOnly)); QCOMPARE(restored.readAll(), bytes);
        QVERIFY(!cloud::copyVerifiedCover(source, folder.filePath("bad.webp"), QString(64, '0')));
        QVERIFY(!QFile::exists(folder.filePath("bad.webp")));
        QVERIFY(!cloud::copyVerifiedCover(folder.filePath("missing.webp"), target, digest));
    }
    void normalizesPlaylistIdentity() {
        QCOMPARE(cloud::playlistId("VLLM"), QStringLiteral("FEmusic_liked_videos"));
        QCOMPARE(cloud::playlistId("VLPL123"), QStringLiteral("PL123"));
        QCOMPARE(cloud::fromDesktop({{"playlistId", "VLPL123"}}, "playlist").value("id").toString(), QStringLiteral("PL123"));
    }
    void preservesPlaylistRemovalAndExplicitRestore() {
        auto result = cloud::reconcile({}, {{"hiddenPlaylistIds", QJsonArray{"VLPL123"}}}, {{"hiddenPlaylistIds", QJsonArray{"other"}}});
        QVERIFY(result.value("hiddenPlaylistIds").toArray().contains("PL123"));
        QVERIFY(result.value("hiddenPlaylistIds").toArray().contains("other"));
        result = cloud::reconcile({{"hiddenPlaylistIds", QJsonArray{"PL123"}}}, {{"hiddenPlaylistIds", QJsonArray{}}}, {{"hiddenPlaylistIds", QJsonArray{"VLPL123", "other"}}});
        QCOMPARE(result.value("hiddenPlaylistIds").toArray(), QJsonArray{"other"});
    }
    void hidesRemovedListsAcrossHomeSidebarAndOffline() {
        const QJsonArray hidden{"PL123", "FEmusic_liked_videos"};
        const QVariantList lists{QVariantMap{{"browseId", "VLPL123"}}, QVariantMap{{"id", "offline-playlist:PL123"}}, QVariantMap{{"id", "LM"}}, QVariantMap{{"id", "keep"}}};
        const auto visible = cloud::visibleMedia(lists, hidden, true);
        QCOMPARE(visible.size(), 1);
        QCOMPARE(visible.first().toMap().value("id").toString(), QStringLiteral("keep"));
        const QVariantMap removed{{"type", "playlist"}, {"id", "PL123"}};
        const QVariantMap song{{"type", "song"}, {"id", "PL123"}};
        const QVariantList sections{QVariantMap{{"items", QVariantList{removed, song}}}, QVariantMap{{"key", "playlists"}, {"items", lists.mid(0, 3)}}};
        const auto feed = cloud::visibleSections(sections, hidden);
        QCOMPARE(feed.size(), 1);
        QCOMPARE(feed.first().toMap().value("items").toList(), QVariantList{song});
        QCOMPARE(cloud::visibleMedia(lists, {}, true), lists);
    }
    void restoresDefaultsWithoutRestoringOtherListsOrChangingSongs() {
        const QJsonObject original{{"hiddenPlaylistIds", QJsonArray{"LM", "VLLM", "FEmusic_liked_videos", "VLSE", "offline-playlist:SE", "PLcustom"}}, {"likedTracks", QJsonArray{QJsonObject{{"id", "song"}}}}, {"playCounts", QJsonObject{{"song", 7}}}};
        const auto restored = cloud::restoreDefaultPlaylists(original);
        QCOMPARE(restored.value("hiddenPlaylistIds").toArray(), QJsonArray{"PLcustom"});
        QCOMPARE(restored.value("likedTracks"), original.value("likedTracks"));
        QCOMPARE(restored.value("playCounts"), original.value("playCounts"));
        QCOMPARE(cloud::restoreDefaultPlaylists(restored), restored);
    }
    void automaticCoverUsesPlaybackOriginImmediately() {
        const QJsonObject modes{{"mode:PL123", true}, {"mode:FEmusic_liked_videos", true}};
        QVariantMap song{{"id", "song"}, {"thumbnail", "https://example.com/cover.jpg"}, {"queueOrigin", QVariantMap{{"id", "VLPL123"}}}};
        auto result = cloud::automaticArtwork(modes, song);
        QCOMPARE(result.value("art:PL123").toString(), QStringLiteral("https://example.com/cover.jpg"));
        QVERIFY(!result.contains("art:FEmusic_liked_videos"));
        song.insert("queueOrigin", QVariantMap{{"id", "unrelated"}});
        QCOMPARE(cloud::automaticArtwork(modes, song), modes);
        song.remove("queueOrigin");
        QCOMPARE(cloud::automaticArtwork(modes, song), modes);
        song.insert("queueOrigin", QVariantMap{{"id", "LM"}});
        QVERIFY(cloud::automaticArtwork(modes, song).contains("art:FEmusic_liked_videos"));
        song.insert("thumbnail", "");
        QCOMPARE(cloud::automaticArtwork(modes, song), modes);
    }
    void keepsConcurrentChangesAndDeletes() {
        const QJsonObject song{{"id", "song"}, {"title", "Song"}};
        const QJsonObject added{{"id", "new"}, {"title", "New"}};
        const QJsonObject base{{"likedTracks", QJsonArray{song}}};
        const QJsonObject local{{"likedTracks", QJsonArray{song, added}}};
        const QJsonObject remote{{"likedTracks", QJsonArray{}}, {"playCounts", QJsonObject{{"song", 7}}}};
        const auto result = cloud::reconcile(base, local, remote);
        QCOMPARE(result.value("likedTracks").toArray(), QJsonArray{added});
        QCOMPARE(result.value("playCounts").toObject().value("song").toInt(), 7);
    }
    void addsOnlyPendingCounters() {
        const QJsonObject sent{{"counts", QJsonObject{{"song", 1}}}, {"heard", QJsonObject{{"song", 15000}}}};
        const QJsonObject current{{"counts", QJsonObject{{"song", 1}, {"new", 1}}}, {"heard", QJsonObject{{"song", 19000}, {"new", 15000}}}};
        const QJsonObject remote{{"playCounts", QJsonObject{{"song", 5}}}, {"listeningTotalMs", 75000}};
        const auto result = cloud::addPending(remote, cloud::countersDelta(current, sent));
        QCOMPARE(result.value("playCounts").toObject().value("song").toInt(), 5);
        QCOMPARE(result.value("playCounts").toObject().value("new").toInt(), 1);
        QCOMPARE(result.value("listeningTotalMs").toInteger(), 94000);
    }
    void restoresAndroidArtistAndSong() {
        const auto artist = cloud::toDesktop({{"id", "artist"}, {"name", "Artist"}, {"artworkUrl", "https://example.com/artist.jpg"}}, "artist");
        QCOMPARE(artist.value("title").toString(), QStringLiteral("Artist"));
        QCOMPARE(cloud::fromDesktop(artist, "artist").value("name").toString(), QStringLiteral("Artist"));
        const auto song = cloud::toDesktop({{"id", "song"}, {"title", "Song"}, {"artist", "Artist"}, {"durationMs", 215000}}, "song");
        QCOMPARE(song.value("durationSeconds").toDouble(), 215.);
        QCOMPARE(cloud::fromDesktop(song, "song").value("durationMs").toInteger(), 215000);
    }
};
QTEST_GUILESS_MAIN(CloudJsonTest)
#include "cloud_json_test.moc"
