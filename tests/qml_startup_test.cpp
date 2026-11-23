#include "account/cloud_cover_crop.h"
#include "local/local_library.h"
#include <QAbstractItemModel>
#include <QGuiApplication>
#include <QFontDatabase>
#include <QFont>
#include <QQuickWindow>
#include <QQuickItem>
#include <QMouseEvent>
#include <QKeyEvent>
#include <QImage>
#include <QBuffer>
#include <QPluginLoader>
#include <QDir>
#include <QThread>
#include <QTemporaryDir>
#include <QElapsedTimer>
#include <QColor>
#include <QQuickStyle>
#include <QFile>
#include <QQmlComponent>
#include <QQmlEngine>
#include <QJSValue>
#include <QQmlContext>
#include <QVariantMap>
#include <memory>
#include <QTextStream>
#include <QUrl>

int main(int argc, char *argv[]) {
  QGuiApplication application(argc, argv);
  QQuickStyle::setStyle(QStringLiteral("Basic"));
  for (const auto &name : {"regular", "medium", "semibold", "bold"})
    if (QFontDatabase::addApplicationFont(QStringLiteral(":/qt/qml/Numa/app/qml/assets/fonts/inter_%1.ttf").arg(name)) < 0) return 1;
  application.setFont(QFont("Inter"));
  QTextStream output(stderr);
  QFile manifest(QStringLiteral(":/qt/qml/Numa/qmldir"));
  if (!manifest.open(QIODevice::ReadOnly)) {
    output << "Numa QML module manifest is missing.\n";
    return 1;
  }
  const auto contents = manifest.readAll();
  if (contents.contains('\\')) {
    output << "Numa QML module manifest contains Windows path separators.\n";
    return 1;
  }

  QQmlEngine engine;
  QQmlComponent rowsComponent(&engine);
  rowsComponent.setData("import Numa\nPlaylistTrackModel {}", QUrl());
  std::unique_ptr<QObject> rowsObject(rowsComponent.create());
  auto *rows = qobject_cast<QAbstractItemModel *>(rowsObject.get());
  if (!rows) {
    output << rowsComponent.errorString() << '\n';
    return 1;
  }
  int inserted = 0;
  int firstInserted = -1;
  int resets = 0;
  QObject::connect(rows, &QAbstractItemModel::rowsInserted, [&](const QModelIndex &, int first, int last) {
    firstInserted = first;
    inserted += last - first + 1;
  });
  QObject::connect(rows, &QAbstractItemModel::modelReset, [&] { ++resets; });
  const QVariantMap firstTrack{{"id", "first"}};
  const QVariantMap nextTrack{{"id", "next"}};
  const QVariantList firstPage{firstTrack};
  const QVariantList bothPages{firstTrack, nextTrack};
  const auto updateRows = [&](const QVariantList &tracks) {
    return QMetaObject::invokeMethod(rows, "updateRows", Q_ARG(QVariantList, tracks));
  };
  if (!updateRows(firstPage)) return 1;
  const QPersistentModelIndex originalRow(rows->index(0, 0));
  if (!updateRows(bothPages) || !updateRows(bothPages) || !originalRow.isValid()
      || firstInserted != 1 || inserted != 2 || resets != 0 || rows->rowCount() != 2) {
    output << "Playlist pagination must append without recreating existing rows.\n";
    return 1;
  }
  if (!updateRows(QVariantList{nextTrack, firstTrack}) || resets != 1 || originalRow.isValid()) {
    output << "Playlist reordering must replace the previous order.\n";
    return 1;
  }
  QQmlComponent queueComponent(&engine);
  queueComponent.setData(R"(
    import Numa
    QueueListModel {
      items: Array.from({length: 1000}, (_, index) => ({id: String(index)}))
      function advance() { items = items.slice(1); reconcile(); }
      function replace() { items = [{id: "new"}]; reconcile(); }
    }
  )", QUrl());
  std::unique_ptr<QObject> queueObject(queueComponent.create());
  auto *queue = qobject_cast<QAbstractItemModel *>(queueObject.get());
  if (!queue) {
    output << queueComponent.errorString() << '\n';
    return 1;
  }
  int queueRemovals = 0;
  int queueMoves = 0;
  QObject::connect(queue, &QAbstractItemModel::rowsRemoved, [&] { ++queueRemovals; });
  QObject::connect(queue, &QAbstractItemModel::rowsMoved, [&] { ++queueMoves; });
  const QPersistentModelIndex nextInQueue(queue->index(1, 0));
  QMetaObject::invokeMethod(queue, "advance");
  if (queueRemovals != 1 || queueMoves != 0 || queue->rowCount() != 999 || nextInQueue.row() != 0) {
    output << "Advancing a large queue must preserve the remaining rows without moves.\n";
    return 1;
  }
  queue->setProperty("suspended", true);
  QMetaObject::invokeMethod(queue, "replace");
  if (queue->rowCount() != 999) return 1;
  queue->setProperty("suspended", false);
  application.processEvents();
  if (queue->rowCount() != 1 || nextInQueue.isValid()) {
    output << "A hidden queue must apply the latest update when it becomes visible.\n";
    return 1;
  }
  engine.rootContext()->setContextProperty(QStringLiteral("NumaAppearance"), QVariantMap{
      {"iconSource", "qrc:/qt/qml/Numa/app/qml/assets/numa-logo-rounded.svg"}, {"theme", "crystal"}, {"rosePine", false}, {"performanceMode", false}, {"borderColor", "#1cffffff"}, {"borderWidth", 1}, {"controlBorderWidth", 1}, {"bordersEnabled", true},
      {"textColor", "#f5f3ee"}, {"mutedColor", "#b4b8b1"}, {"accentColor", "#c4a7e7"}, {"surfaceColor", "#660e1210"}});
  QQmlComponent component(&engine,
      QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/Main.qml")),
      QQmlComponent::PreferSynchronous);
  if (!component.isReady()) {
    for (const auto &error : component.errors())
      output << error.toString() << '\n';
    output << "Numa could not compile its QML entry point.\n";
    return 1;
  }
  QQmlComponent appearanceSection(&engine,
      QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/components/settings/AppearanceSection.qml")));
  if (!appearanceSection.isReady()) {
    for (const auto &error : appearanceSection.errors()) output << error.toString() << '\n';
    return 1;
  }
  for (const auto &theme : {QStringLiteral("crystal"), QStringLiteral("rose_pine_dawn"), QStringLiteral("rose_pine")}) {
    const bool dawn = theme == QStringLiteral("rose_pine_dawn");
    const bool rose = theme != QStringLiteral("crystal");
    engine.rootContext()->setContextProperty(QStringLiteral("NumaAppearance"), QVariantMap{
        {"theme", theme}, {"rosePine", rose}, {"performanceMode", rose},
        {"playlistSongBackground", false},
        {"overlayColor", dawn ? "#f2e9e1" : "#26233a"}, {"highlightColor", dawn ? "#efd4cf" : "#403448"},
        {"borderColor", rose ? "#ebbcba" : "#1cffffff"}, {"borderWidth", rose ? 2 : 1},
        {"controlBorderWidth", rose ? 0 : 1},
        {"bordersEnabled", true},
        {"textColor", dawn ? "#575279" : rose ? "#e0def4" : "#f5f3ee"}, {"mutedColor", dawn ? "#797593" : "#908caa"},
        {"accentColor", dawn ? "#d7827e" : rose ? "#ebbcba" : "#c4a7e7"}, {"surfaceColor", dawn ? "#fffaf3" : "#1f1d2e"},
        {"baseColor", dawn ? "#faf4ed" : rose ? "#191724" : "#0d0f12"}});
  QQmlComponent frame(&engine);
  frame.setData("import QtQuick\nimport Numa\nRoundedSurfaceFrame {}", QUrl());
  std::unique_ptr<QObject> surface(frame.create());
  if (!surface) {
    for (const auto &error : frame.errors()) output << error.toString() << '\n';
    return 1;
  }
  auto *border = surface->property("border").value<QObject *>();
  if (!border || border->property("width").toInt() != (rose ? 0 : 1)) {
    output << "Controls must be borderless in Rose Pine.\n";
    return 1;
  }
  QQmlComponent skeletonComponent(&engine);
  skeletonComponent.setData("import Numa\nLoadingSkeleton { width: 640; trackRows: true }", QUrl());
  std::unique_ptr<QObject> skeleton(skeletonComponent.create());
  if (!skeleton) {
    output << skeletonComponent.errorString() << '\n';
    return 1;
  }
  const auto fill = skeleton->property("fill").value<QColor>();
  const QColor themeInk(dawn ? "#575279" : rose ? "#e0def4" : "#f5f3ee");
  if (fill.red() != themeInk.red() || fill.green() != themeInk.green()
      || fill.blue() != themeInk.blue() || fill.alpha() <= 0 || fill.alpha() >= 255) {
    output << "Loading skeletons must use translucent theme text colors.\n";
    return 1;
  }
  skeleton->setProperty("loading", true);
  QElapsedTimer loadDelay;
  loadDelay.start();
  while (loadDelay.elapsed() < 120) {
    application.processEvents();
    QThread::msleep(5);
  }
  if (skeleton->property("active").toBool()) {
    output << "Fast loads must not allocate skeleton graphics.\n";
    return 1;
  }
  while (!skeleton->property("active").toBool() && loadDelay.elapsed() < 1200) {
    application.processEvents();
    QThread::msleep(5);
  }
  if (!skeleton->property("active").toBool() || skeleton->property("height").toInt() <= 0) {
    output << "The loading skeleton did not appear for a slow load.\n";
    return 1;
  }
  const auto *layout = qobject_cast<QQuickItem *>(skeleton->property("item").value<QObject *>());
  int trackPlaceholders = 0;
  if (layout) {
    for (const auto *placeholder : layout->childItems()) {
      if (placeholder->objectName() != "skeletonTrackRow") continue;
      const auto *cover = placeholder->findChild<QQuickItem *>("skeletonTrackArtwork");
      if (placeholder->width() != 640 || placeholder->height() != 60
          || !cover || cover->width() != 44 || cover->height() != 44 || cover->x() != 58) {
        output << "Playlist placeholders must match the song row and its artwork slot.\n";
        return 1;
      }
      ++trackPlaceholders;
    }
  }
  if (trackPlaceholders != 3 || layout->height() != 180) return 1;
  skeleton->setProperty("loading", false);
  if (skeleton->property("active").toBool()) {
    output << "Completing a load must remove its skeleton immediately.\n";
    return 1;
  }
  for (bool active : {false, true}) {
    engine.rootContext()->setContextProperty(QStringLiteral("NumaParty"), QVariantMap{
      {"active", active}, {"connected", active}, {"host", active}, {"code", "ABC234"},
      {"message", ""}, {"peers", QVariantList{QVariantMap{{"id", "guest"}, {"name", "Android"}}}}});
    QQmlComponent party(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/components/player/ListeningPartyPanel.qml")));
    std::unique_ptr<QObject> panel(party.create());
    if (!panel) {
      for (const auto &error : party.errors()) output << error.toString() << '\n';
      return 1;
    }
    auto *code = panel->findChild<QObject *>(QStringLiteral("partyRoomCode"));
    if (!code) return 1;
    code->setProperty("text", QStringLiteral("ab23cd"));
    QMetaObject::invokeMethod(code, "textEdited");
    if (code->property("text").toString() != QStringLiteral("AB23CD")) {
      output << "Party room codes must display uppercase.\n";
      return 1;
    }
  }
  }
  QQmlComponent wheel(&engine);
  wheel.setData("import QtQuick\nimport Numa\nItem { width: 100; height: 100; Flickable { id: list; objectName: 'list'; width: 100; height: 100; contentHeight: 300 } WheelGlide { objectName: 'wheel'; view: list; overshootEnabled: false } }", QUrl());
  std::unique_ptr<QObject> scrollTest(wheel.create());
  if (!scrollTest) {
    for (const auto &error : wheel.errors()) output << error.toString() << '\n';
    return 1;
  }
  auto *glide = scrollTest->findChild<QObject *>("wheel");
  auto *list = scrollTest->findChild<QObject *>("list");
  if (!glide || !list) return 1;
  for (const auto [requested, expected] : {std::pair{-90.0, 0.0}, {290.0, 200.0}, {85.0, 85.0}}) {
    if (!QMetaObject::invokeMethod(glide, "place", Q_ARG(QVariant, QVariant(requested)))
        || list->property("contentY").toDouble() != expected) {
      output << "Bounded wheel scrolling must stay inside its viewport.\n";
      return 1;
    }
  }
  QImage source(600, 400, QImage::Format_RGB32);
  source.fill(Qt::red);
  for (int y = 0; y < source.height(); ++y)
    for (int x = 300; x < source.width(); ++x) source.setPixelColor(x, y, Qt::blue);
  const auto cropped = cloud::cropCover(source, QRectF(0.5, 0, 0.5, 0.75));
  if (cropped.size() != QSize(1024, 1024) || cropped.pixelColor(512, 512) != QColor(Qt::blue)
      || !cloud::cropCover(source, QRectF(2, 2, 0.5, 0.5)).isNull()) {
    output << "Playlist artwork must crop the selected region into a square.\n"; return 1;
  }
  const auto runtimeRoot = qEnvironmentVariable("NUMA_STAGED_RUNTIME_ROOT");
  QPluginLoader webpPlugin;
  if (!runtimeRoot.isEmpty()) {
    webpPlugin.setFileName(runtimeRoot + "/imageformats/qwebp.dll");
    if (!webpPlugin.load()) { output << webpPlugin.errorString() << '\n'; return 1; }
  }
  QByteArray webpBytes;
  QBuffer webpBuffer(&webpBytes); webpBuffer.open(QIODevice::WriteOnly);
  if (!cropped.save(&webpBuffer, "WEBP", 85) || !webpBytes.startsWith("RIFF")
      || QImage::fromData(webpBytes, "WEBP").size() != cropped.size()) {
    output << "Playlist covers require working WebP encoding and decoding.\n"; return 1;
  }
  QQmlComponent mockSync(&engine);
  mockSync.setData(R"(
    import QtQuick
    QtObject {
      property int revision: 0
      property var playlistItems: []
      function section(key) {
        if (key === "metrics")
          return [{id: "test", title: "Song", thumbnail: "https://example.invalid/cover.png", listenCount: 6}];
        return key === "playlists" ? playlistItems : [];
      }
      function metricArtistArtwork(name) { return "" }
      function listeningMetrics(days) {
        return {listens: 6, heardMs: 180000, songs: 1,
                tracks: [{id: "test", title: "Song", artist: "Artist", listenCount: 6}],
                artists: [{title: "Artist", listenCount: 6, songCount: 1}],
                albums: [{title: "Album", listenCount: 6, songCount: 1}]};
      }
      function visiblePlaylists(items) { return items }
      function artworkFor(id, fallback) { return fallback }
      property bool saved: false
      property bool automatic: false
      property bool busy: false
      property string status: "Ready"
      function syncNow() { busy = true; status = "Syncing" }
      property bool defaultsHidden: true
      property bool failArtworkSave: true
      property string coverError: "The image could not be saved."
      property rect savedCrop
      function setCoverCrop(id, url, area) { savedCrop = area; return !failArtworkSave }
      function isHiddenPlaylist(id) { return defaultsHidden }
      function restoreDefaultPlaylists() { defaultsHidden = false; revision++ }
      function isSaved(kind, id) { return saved }
      function toggleSaved(kind, media) { saved = !saved; revision++ }
      function removePlaylist(id) { saved = false; revision++ }
      function automaticCover(id) { return automatic }
      function setAutomaticCover(id, enabled) { automatic = enabled; revision++ }
    }
  )", QUrl());
  std::unique_ptr<QObject> sync(mockSync.create());
  if (!sync) return 1;
  engine.rootContext()->setContextProperty("NumaSync", sync.get());
  QQmlComponent networkMock(&engine);
  networkMock.setData(R"(
    import QtQuick
    QtObject {
      property bool offline: true
      property bool retrying: false
      property bool checking: false
      property int retries: 0
      function retry() { retries++; checking = true }
    }
  )", QUrl());
  std::unique_ptr<QObject> network(networkMock.create());
  if (!network) return 1;
  engine.rootContext()->setContextProperty("NumaNetwork", network.get());
  QQmlComponent offlineButtonComponent(&engine);
  offlineButtonComponent.setData("import QtQuick\nimport Numa\nOfflineStatusButton {}", QUrl());
  std::unique_ptr<QObject> offlineButton(offlineButtonComponent.create());
  if (!offlineButton) return 1;
  auto *offlineIcon = offlineButton->findChild<QObject *>("offlineConnectionIcon");
  if (!offlineIcon || !offlineButton->property("visible").toBool()) return 1;
  QMetaObject::invokeMethod(offlineButton.get(), "clicked");
  if (!network->property("checking").toBool() || offlineButton->property("enabled").toBool()) return 1;
  for (int i = 0; i < 20; ++i) { application.processEvents(); QThread::msleep(10); }
  network->setProperty("checking", false);
  auto *offlineBackground = offlineButton->property("background").value<QObject *>();
  if (offlineIcon->property("rotation").toDouble() != 0
      || offlineIcon->property("name").toString() != "wifi-off"
      || offlineIcon->property("color").value<QColor>() != QColor("#191724")
      || !offlineBackground || offlineBackground->property("color").value<QColor>() != QColor("#ebbcba")
      || !offlineButton->property("enabled").toBool()) {
    output << "The offline icon must return upright with its pink button after checking the connection."
           << " icon=" << offlineIcon->property("name").toString()
           << " rotation=" << offlineIcon->property("rotation").toDouble()
           << " ink=" << offlineIcon->property("color").value<QColor>().name()
           << " surface=" << (offlineBackground ? offlineBackground->property("color").value<QColor>().name() : QStringLiteral("missing"))
           << " enabled=" << offlineButton->property("enabled").toBool() << '\n';
    return 1;
  }
  network->setProperty("offline", false);
  if (offlineButton->property("visible").toBool()) return 1;
  network->setProperty("checking", true);
  if (offlineButton->property("visible").toBool()) return 1;
  network->setProperty("checking", false);
  network->setProperty("offline", true);
  QQmlContext searchContext(engine.rootContext());
  searchContext.setContextProperty("NumaPlaylist", QVariantMap{{"palette", QVariantMap{}}});
  searchContext.setContextProperty("NumaPlayback", QVariantMap{{"track", QVariantMap{{"id", ""}}}});
  QQmlComponent longSearchComponent(&engine);
  longSearchComponent.setData(R"(
    import QtQuick
    import Numa
    Window {
      width: 900
      height: 600
      visible: true
      SearchView {
        objectName: "longSearch"
        anchors.fill: parent
        filter: "songs"
        sections: [{key: filter, title: filter, items: Array.from({length: filter === "albums" ? 360 : 1000}, (_, index) => ({
          id: String(index), type: filter === "albums" ? "album" : "song", title: "Result " + index
        }))}]
      }
    }
  )", QUrl());
  std::unique_ptr<QObject> longSearch(longSearchComponent.create(&searchContext));
  if (!longSearch) {
    output << longSearchComponent.errorString() << '\n';
    return 1;
  }
  auto *searchView = longSearch->findChild<QObject *>("longSearch");
  if (!searchView) return 1;
  auto *searchScroll = searchView->property("navigationScroll").value<QObject *>();
  if (!searchScroll) return 1;
  const auto visibleResultIndexes = [&] {
    QList<int> indexes;
    const auto collect = [&indexes](auto &&self, const QQuickItem *item) -> void {
      if (item->objectName() == "searchResultContent") {
        indexes.append(item->parentItem()->property("index").toInt());
        return;
      }
      for (const auto *child : item->childItems())
        self(self, child);
    };
    collect(collect, qobject_cast<const QQuickItem *>(searchView));
    return indexes;
  };
  QElapsedTimer searchLayout;
  searchLayout.start();
  while (searchScroll->property("contentHeight").toInt() < 54000 && searchLayout.elapsed() < 3000) {
    application.processEvents();
    QThread::msleep(5);
  }
  const auto firstResults = visibleResultIndexes();
  if (searchScroll->property("contentHeight").toInt() < 54000
      || !firstResults.contains(0) || firstResults.size() > 30) {
    output << "A long search must reserve its scroll range while creating only nearby results. "
           << "Height: " << searchScroll->property("contentHeight").toInt()
           << ", cards: " << firstResults.size() << ".\n";
    return 1;
  }
  searchScroll->setProperty("contentY", 54 * 160);
  application.processEvents();
  const auto laterResults = visibleResultIndexes();
  if (!laterResults.contains(160) || laterResults.contains(0) || laterResults.size() > 32) {
    output << "Search results must follow the viewport and release distant cards.\n";
    return 1;
  }
  output << "Search viewport mounted " << firstResults.size() << " initial cards for 1000 results, and "
         << laterResults.size() << " after scrolling.\n";
  searchView->setProperty("filter", "albums");
  searchLayout.restart();
  while (!visibleResultIndexes().contains(0) && searchLayout.elapsed() < 3000) {
    application.processEvents();
    QThread::msleep(5);
  }
  searchScroll->setProperty("contentY", 7800);
  application.processEvents();
  const auto albumResults = visibleResultIndexes();
  if (albumResults.isEmpty() || albumResults.contains(0) || albumResults.size() > 40) {
    output << "Album search must also load its grid around the visible results.\n";
    return 1;
  }
  longSearch.reset();
  QTemporaryDir artworkDirectory;
  if (!artworkDirectory.isValid()) return 1;
  QImage firstCover(64, 64, QImage::Format_RGB32);
  firstCover.fill(Qt::red);
  QImage nextCover(64, 64, QImage::Format_RGB32);
  nextCover.fill(Qt::blue);
  const auto firstCoverPath = artworkDirectory.filePath("first.png");
  const auto nextCoverPath = artworkDirectory.filePath("next.png");
  if (!firstCover.save(firstCoverPath) || !nextCover.save(nextCoverPath)) return 1;
  const auto firstCoverUrl = QUrl::fromLocalFile(firstCoverPath);
  const auto nextCoverUrl = QUrl::fromLocalFile(nextCoverPath);
  QQmlComponent paletteComponent(&engine);
  paletteComponent.setData("import Numa\nArtworkPalette {}", QUrl());
  std::unique_ptr<QObject> palette(paletteComponent.create());
  if (!palette) {
    output << paletteComponent.errorString() << '\n';
    return 1;
  }
  const auto loadPalette = [&](const QUrl &url) {
    palette->setProperty("source", url);
    QElapsedTimer timer;
    timer.start();
    while (!palette->property("ready").toBool() && timer.elapsed() < 3000) {
      application.processEvents();
      QThread::msleep(2);
    }
    return palette->property("ready").toBool();
  };
  if (!loadPalette(firstCoverUrl)) return 1;
  for (int index = 0; index < 63; ++index) {
    const QString path = artworkDirectory.filePath(QStringLiteral("palette-%1.png").arg(index));
    if (!QFile::copy(nextCoverPath, path) || !loadPalette(QUrl::fromLocalFile(path))) return 1;
  }
  if (!loadPalette(firstCoverUrl) || !loadPalette(nextCoverUrl)) return 1;
  palette->setProperty("source", firstCoverUrl);
  if (!palette->property("ready").toBool()) {
    output << "Reaching the palette cache limit must retain recently used covers.\n";
    return 1;
  }
  QQmlComponent artworkComponent(&engine);
  artworkComponent.setData("import QtQuick\nimport Numa\nRoundedArtwork { width: 64; height: 64 }", QUrl());
  std::unique_ptr<QObject> artwork(artworkComponent.create());
  if (!artwork) return 1;
  QObject *artworkImage = nullptr;
  for (auto *child : artwork->children())
    if (child->metaObject()->indexOfProperty("retainWhileLoading") >= 0) artworkImage = child;
  if (!artworkImage) return 1;
  const auto waitForCover = [&](const QUrl &expected) {
    QElapsedTimer timeout;
    timeout.start();
    while (timeout.elapsed() < 3000) {
      application.processEvents();
      if (artworkImage->property("source").toUrl() == expected
          && artworkImage->property("status").toInt() == 1) return true;
      QThread::msleep(5);
    }
    return false;
  };
  artwork->setProperty("source", firstCoverUrl);
  if (!waitForCover(firstCoverUrl)) return 1;
  QMetaObject::invokeMethod(artwork.get(), "reload");
  if (!waitForCover(firstCoverUrl)) return 1;
  artwork->setProperty("source", nextCoverUrl);
  if (!waitForCover(nextCoverUrl)) {
    output << "Retrying a cover must preserve updates when the playing song changes.\n";
    return 1;
  }
  QMetaObject::invokeMethod(artwork.get(), "reload");
  artwork->setProperty("source", firstCoverUrl);
  if (!waitForCover(firstCoverUrl)) {
    output << "A pending cover retry must load the current song instead of an older one.\n";
    return 1;
  }
  for (int i = 0; i < 32; ++i) {
    QMetaObject::invokeMethod(artwork.get(), "reload");
    artwork->setProperty("source", i % 2 == 0 ? firstCoverUrl : nextCoverUrl);
    application.processEvents();
  }
  if (!waitForCover(nextCoverUrl) || !artwork->property("hasImage").toBool()) {
    output << "Rapid artwork changes must leave the latest thumbnail visible.\n";
    return 1;
  }
  QQmlComponent queuedArtwork(&engine);
  queuedArtwork.setData(R"(
    import QtQuick
    import Numa
    Item {
      id: viewport
      width: 200
      height: 200
      property alias cover: tile
      RoundedArtwork { id: tile; width: 64; height: 64; visible: false; loadViewport: viewport }
    }
  )", QUrl());
  std::unique_ptr<QObject> viewport(queuedArtwork.create());
  if (!viewport) {
    output << queuedArtwork.errorString() << '\n';
    return 1;
  }
  auto *queuedCover = viewport->property("cover").value<QObject *>();
  if (!queuedCover) return 1;
  queuedCover->setProperty("source", firstCoverUrl);
  application.processEvents();
  if (!queuedCover->property("grantedThumbnail").toString().isEmpty()) return 1;
  queuedCover->setProperty("visible", true);
  QElapsedTimer queuedTimeout;
  queuedTimeout.start();
  while (!queuedCover->property("hasImage").toBool() && queuedTimeout.elapsed() < 3000) {
    application.processEvents();
    QThread::msleep(5);
  }
  if (!queuedCover->property("hasImage").toBool()) {
    output << "A queued cover must load when its delegate becomes visible.\n";
    return 1;
  }
  QQmlComponent mockAccount(&engine);
  mockAccount.setData("import QtQuick\nQtObject { property bool isSignedIn: false }", QUrl());
  std::unique_ptr<QObject> account(mockAccount.create());
  if (!account) return 1;
  engine.rootContext()->setContextProperty("NumaAccount", account.get());
  QQmlComponent controls(&engine);
  controls.setData(R"(
    import QtQuick
    import QtQuick.Controls
    import Numa
    ApplicationWindow {
      id: fixture
      width: 700; height: 600
      color: "#181622"
      function playlistSearchOrder(query) {
        return quickSearch.computeMatches(query).map(match => match.track.id).join(",");
      }
      function replacePlaylistSearchTracks() {
        quickSearch.tracks = [{id: "replacement", title: "Nueva mañana"}];
      }
      PlaylistQuickSearch {
        id: quickSearch
        backdrop: fixture.contentItem
        detail: ({ author: "MaÑana" })
        tracks: [
          {id: "album", title: "Otra", artist: "Artista", album: "Mañana"},
          {id: "fallback", title: "Sin artista"},
          {id: "substring", title: "Supermañana"},
          {id: "word", title: "Supermañana Mañana"},
          {id: "prefix", title: "Mañana temprano"},
          {id: "exact", title: "Mañana"},
          {id: "tie", title: "Mañana"}
        ]
      }
      CloudCollectionAction { x: 50; y: 50; media: ({id: "PL123", title: "Playlist", type: "playlist"}) }
      SettingsSegmented {
        objectName: "testChoice"; x: 50; y: 120
        currentValue: "adaptive"
        model: [{value: "standard", label: "Standard"}, {value: "adaptive", label: "Adaptive", enabled: false}]
      }
      NumaAccountSettingForm {
        objectName: "accountSyncForm"
        x: 50; y: 300; width: 600
        signedIn: NumaAccount.isSignedIn
        userName: "Test account"
      }
      RestoreDefaultPlaylistsButton { objectName: "restoreDefaults"; x: 50; y: 180; width: 230 }
      SettingsInfoButton { objectName: "testInfo"; x: 280; y: 120; heading: "Crossfade"; message: "Blends consecutive songs." }
      AppearanceSlider {
        id: flatSlider
        objectName: "flatSettingsSlider"
        x: 50; y: 245; width: 235
        value: 0.4
        onMoved: nextValue => flatSlider.value = nextValue
      }
      SettingsSegmented {
        objectName: "singleChoice"; x: 330; y: 120
        currentValue: "apple_music"
        model: [{value: "apple_music", label: "Apple Music"}]
      }
    }
  )", QUrl());
  std::unique_ptr<QObject> controlsObject(controls.create());
  if (!controlsObject) {
    for (const auto &error : controls.errors()) output << error.toString() << '\n';
    return 1;
  }
  const auto playlistSearchOrder = [&](const QString &query) {
    QVariant result;
    QMetaObject::invokeMethod(controlsObject.get(), "playlistSearchOrder",
                              Q_RETURN_ARG(QVariant, result), Q_ARG(QVariant, query));
    return result.toString();
  };
  if (playlistSearchOrder(QStringLiteral("manana")) != "exact,tie,prefix,word,substring,fallback,album"
      || playlistSearchOrder(QStringLiteral("manana temprano")) != "prefix") {
    output << "Playlist search must preserve accent-insensitive ranking and the original order of ties.\n";
    return 1;
  }
  QMetaObject::invokeMethod(controlsObject.get(), "replacePlaylistSearchTracks");
  if (playlistSearchOrder(QStringLiteral("manana")) != "replacement") {
    output << "Playlist search must rebuild its index when its tracks change.\n";
    return 1;
  }
  auto *window = qobject_cast<QQuickWindow *>(controlsObject.get());
  auto *libraryButton = controlsObject->findChild<QObject *>("cloudLibraryButton");
  auto *artworkButton = controlsObject->findChild<QObject *>("playlistArtworkButton");
  auto *menu = controlsObject->findChild<QObject *>("playlistArtworkMenu");
  auto *choice = controlsObject->findChild<QObject *>("testChoice");
  if (!window || !libraryButton || !artworkButton || !menu || !choice) return 1;
  window->show();
  for (int i = 0; i < 12; ++i) { application.processEvents(); QThread::msleep(10); }
  auto *flatSetting = controlsObject->findChild<QObject *>("flatSettingsSlider");
  auto *flatSlider = flatSetting ? qobject_cast<QQuickItem *>(flatSetting->property("slider").value<QObject *>()) : nullptr;
  auto *flatHandle = flatSlider ? qobject_cast<QQuickItem *>(flatSlider->property("handle").value<QObject *>()) : nullptr;
  if (!flatSlider || !flatHandle || flatHandle->width() != 0 || flatHandle->height() != 0) {
    output << "Settings sliders must have no visible knob.\n";
    return 1;
  }
  const auto sendSliderMouse = [&](QEvent::Type type, double fraction, Qt::MouseButton changed, Qt::MouseButtons held) {
    const double x = flatSlider->property("leftPadding").toDouble() + fraction * flatSlider->property("availableWidth").toDouble();
    const QPointF point = flatSlider->mapToScene(QPointF(x, flatSlider->height() / 2));
    QMouseEvent event(type, point, QPointF(window->mapToGlobal(point.toPoint())), changed, held, Qt::NoModifier);
    QCoreApplication::sendEvent(window, &event);
    application.processEvents();
  };
  sendSliderMouse(QEvent::MouseButtonPress, 0.76, Qt::LeftButton, Qt::LeftButton);
  sendSliderMouse(QEvent::MouseButtonRelease, 0.76, Qt::LeftButton, Qt::NoButton);
  if (qAbs(flatSetting->property("value").toDouble() - 0.76) > 0.04) {
    output << "Clicking the slider rail must move its value.\n";
    return 1;
  }
  sendSliderMouse(QEvent::MouseButtonPress, 0.76, Qt::LeftButton, Qt::LeftButton);
  sendSliderMouse(QEvent::MouseMove, 0.25, Qt::NoButton, Qt::LeftButton);
  sendSliderMouse(QEvent::MouseButtonRelease, 0.25, Qt::LeftButton, Qt::NoButton);
  const double draggedValue = flatSetting->property("value").toDouble();
  if (qAbs(draggedValue - 0.25) > 0.04) {
    output << "Dragging the slider rail must move its value.\n";
    return 1;
  }
  flatSlider->forceActiveFocus(Qt::TabFocusReason);
  QKeyEvent sliderKey(QEvent::KeyPress, Qt::Key_Right, Qt::NoModifier);
  QCoreApplication::sendEvent(window, &sliderKey);
  if (flatSetting->property("value").toDouble() <= draggedValue) {
    output << "Settings sliders must remain adjustable by keyboard.\n";
    return 1;
  }
  auto *syncButton = controlsObject->findChild<QObject *>("syncLibraryNow");
  if (!syncButton || syncButton->property("enabled").toBool()) return 1;
  account->setProperty("isSignedIn", true);
  if (!syncButton->property("enabled").toBool()) return 1;
  QMetaObject::invokeMethod(syncButton, "clicked");
  if (!sync->property("busy").toBool() || syncButton->property("enabled").toBool()) return 1;
  sync->setProperty("busy", false);
  auto *restore = controlsObject->findChild<QObject *>("restoreDefaults");
  if (!restore || !restore->property("enabled").toBool()) return 1;
  QMetaObject::invokeMethod(restore, "clicked");
  if (sync->property("defaultsHidden").toBool() || restore->property("enabled").toBool()) return 1;
  QMetaObject::invokeMethod(libraryButton, "clicked");
  if (!sync->property("saved").toBool() || libraryButton->property("glyph").toString() != "check") return 1;
  auto *removeConfirmation = controlsObject->findChild<QObject *>("removePlaylistConfirmation");
  if (!removeConfirmation) return 1;
  QMetaObject::invokeMethod(libraryButton, "clicked");
  if (!sync->property("saved").toBool() || !removeConfirmation->property("visible").toBool()) {
    output << "Removing a saved playlist must ask for confirmation without changing the library.\n";
    return 1;
  }
  QMetaObject::invokeMethod(removeConfirmation, "close");
  if (!sync->property("saved").toBool()) return 1;
  QMetaObject::invokeMethod(libraryButton, "clicked");
  QMetaObject::invokeMethod(removeConfirmation, "accepted");
  QMetaObject::invokeMethod(removeConfirmation, "close");
  if (sync->property("saved").toBool() || libraryButton->property("glyph").toString() != "library-big") return 1;
  const QColor neutralIcon(engine.rootContext()->contextProperty("NumaAppearance").toMap().value("textColor").toString());
  if (libraryButton->property("width") != artworkButton->property("width")
      || libraryButton->property("glyphColor").value<QColor>() != neutralIcon
      || artworkButton->property("glyphColor").value<QColor>() != neutralIcon) {
    output << "Collection actions must share neutral icon colors and dimensions.\n"; return 1;
  }
  auto *changeEntry = controlsObject->findChild<QObject *>("playlistArtworkChange");
  auto *automaticEntry = controlsObject->findChild<QObject *>("playlistArtworkAutomatic");
  if (!changeEntry || !automaticEntry || changeEntry->property("leftPadding") != automaticEntry->property("leftPadding")) {
    output << "Playlist menu entries must share text alignment.\n"; return 1;
  }
  if (choice->property("currentIndex").toInt() != 1 || choice->property("displayText").toString() != "Adaptive") return 1;
  QMetaObject::invokeMethod(artworkButton, "clicked");
  menu->setProperty("currentIndex", 0);
  for (int i = 0; i < 25; ++i) { application.processEvents(); QThread::msleep(10); }
  auto *accountLayout = controlsObject->findChild<QQuickItem *>("accountSyncForm");
  auto *syncAction = qobject_cast<QQuickItem *>(syncButton);
  auto *signOut = accountLayout ? qobject_cast<QQuickItem *>(accountLayout->property("signOutButton").value<QObject *>()) : nullptr;
  auto *syncDescription = controlsObject->findChild<QQuickItem *>("syncDescription");
  if (!accountLayout || !syncAction || !signOut || !syncDescription) return 1;
  const auto syncCorner = syncAction->mapToItem(accountLayout, QPointF(syncAction->width(), 0));
  const auto profileCorner = signOut->mapToItem(accountLayout, QPointF(signOut->width(), signOut->height()));
  if (syncCorner.y() <= profileCorner.y() || qAbs(syncCorner.x() - accountLayout->width()) > 1
      || qAbs(syncCorner.x() - profileCorner.x()) > 1) {
    output << "The sync action must be below the profile and right-aligned with sign out. "
           << "Sync (" << syncCorner.x() << ", " << syncCorner.y() << "), profile ("
           << profileCorner.x() << ", " << profileCorner.y() << ").\n";
    return 1;
  }
  const auto textCorner = syncDescription->mapToItem(accountLayout, QPointF(syncDescription->width(), 0));
  if (textCorner.x() > syncCorner.x() - syncAction->width() - 20
      || textCorner.y() >= syncCorner.y() + syncAction->height()
      || textCorner.y() + syncDescription->height() <= syncCorner.y()) {
    output << "Account sync copy must wrap to the left of its button.\n";
    return 1;
  }
  accountLayout->setWidth(260);
  for (int i = 0; i < 8; ++i) { application.processEvents(); QThread::msleep(10); }
  const auto narrowButton = syncAction->mapToItem(accountLayout, QPointF(syncAction->width(), 0));
  const auto narrowText = syncDescription->mapToItem(accountLayout, QPointF(0, syncDescription->height()));
  if (narrowButton.y() < narrowText.y() || qAbs(narrowButton.x() - accountLayout->width()) > 1) {
    output << "A narrow account panel must keep the sync button below the copy and on the right.\n";
    return 1;
  }
  accountLayout->setWidth(600);
  if (!menu->property("visible").toBool() || menu->property("width").toInt() < 250 || menu->property("height").toInt() < 150) {
    output << "Playlist actions must open as a full-sized menu.\n"; return 1;
  }
  const auto captureDir = qEnvironmentVariable("NUMA_UI_CAPTURE_DIR");
  if (!captureDir.isEmpty()) {
    QDir().mkpath(captureDir);
    if (!window->grabWindow().save(captureDir + "/playlist-menu.png")) return 1;
  }
  QMetaObject::invokeMethod(menu, "close");
  auto *crop = controlsObject->findChild<QObject *>("playlistCropDialog");
  if (!crop) return 1;
  QImage image(600, 400, QImage::Format_RGB32); image.fill(QColor("#f5a080"));
  const auto testImage = QDir::tempPath() + "/numa-cover-crop-test.png";
  if (!image.save(testImage)) return 1;
  crop->setProperty("imageSource", QUrl::fromLocalFile(testImage));
  QMetaObject::invokeMethod(crop, "open");
  for (int i = 0; i < 25; ++i) { application.processEvents(); QThread::msleep(10); }
  if (!crop->property("visible").toBool() || crop->property("width").toInt() < 300) return 1;
  if (!captureDir.isEmpty() && !window->grabWindow().save(captureDir + "/playlist-crop.png")) return 1;
  auto *saveArtwork = crop->findChild<QObject *>("savePlaylistArtwork");
  if (!saveArtwork) return 1;
  QMetaObject::invokeMethod(saveArtwork, "clicked");
  if (!crop->property("visible").toBool() || crop->property("errorText").toString().isEmpty()) {
    output << "Failed artwork saves must keep the editor open with an error.\n"; return 1;
  }
  sync->setProperty("failArtworkSave", false);
  QMetaObject::invokeMethod(saveArtwork, "clicked");
  if (crop->property("visible").toBool() || !sync->property("savedCrop").toRectF().isValid()) return 1;
  QFile::remove(testImage);
  auto *singleChoice = controlsObject->findChild<QObject *>("singleChoice");
  auto *singlePicker = singleChoice ? singleChoice->findChild<QObject *>("settingsChoicePicker") : nullptr;
  if (!singlePicker || singlePicker->property("visible").toBool()
      || singleChoice->property("displayText").toString() != "Apple Music") {
    output << "A single available choice must display as text, without a dropdown.\n"; return 1;
  }
  auto *infoButton = controlsObject->findChild<QObject *>("testInfo");
  auto *information = controlsObject->findChild<QObject *>("settingsInformationPopup");
  if (!infoButton || !information) return 1;
  QMetaObject::invokeMethod(infoButton, "clicked");
  for (int i = 0; i < 25; ++i) { application.processEvents(); QThread::msleep(10); }
  if (!information->property("visible").toBool() || information->property("height").toDouble() < 150) {
    output << "Settings help must show the title, explanation and close button in one card.\n"; return 1;
  }
  if (!captureDir.isEmpty() && !window->grabWindow().save(captureDir + "/settings-information.png")) return 1;
  QMetaObject::invokeMethod(information, "close");
  window->close();
  if (!captureDir.isEmpty()) {
    engine.rootContext()->setContextProperty("NumaAppearance", QVariantMap{
      {"iconSource", "qrc:/qt/qml/Numa/app/qml/assets/numa-logo-rounded.svg"}, {"theme", "rose_pine_dawn"}, {"rosePine", true}, {"performanceMode", true}, {"layoutStyle", "glade"},
      {"baseColor", "#faf4ed"}, {"surfaceColor", "#fffaf3"}, {"overlayColor", "#f2e9e1"}, {"highlightColor", "#efd4cf"},
      {"textColor", "#575279"}, {"mutedColor", "#797593"}, {"accentColor", "#d7827e"},
      {"borderColor", "#d7827e"}, {"bordersEnabled", false}, {"borderWidth", 0}, {"controlBorderWidth", 0},
      {"animatedArtworkEnabled", false}, {"animatedCollageEnabled", false}, {"showBitrate", false}});
    engine.rootContext()->setContextProperty("NumaPlayback", QVariantMap{
      {"track", QVariantMap{{"id", "preview"}, {"title", "Let It Happen"}, {"artist", "Tame Impala"}, {"thumbnail", ""}}},
      {"transitionTrack", QVariantMap{{"thumbnail", ""}}}, {"crossfadeActive", false}, {"crossfadeProgress", 0},
      {"playing", true}, {"loading", false}, {"canGoNext", true}, {"canGoPrevious", true},
      {"shuffleEnabled", false}, {"repeatMode", "off"}, {"duration", 460}, {"displayDuration", 460}, {"displayPosition", 228}, {"errorMessage", ""}, {"bitrate", 0}});
    engine.rootContext()->setContextProperty("NumaHome", QVariantMap{{"volume", 0.7}, {"playlists", QVariantList{}}, {"playlistsError", ""}, {"playlistsLoading", false}});
    engine.rootContext()->setContextProperty("NumaLocal", QVariantMap{{"playlists", QVariantList{}}});
    engine.rootContext()->setContextProperty("NumaOffline", QVariantMap{{"playlists", QVariantList{}}});
    engine.rootContext()->setContextProperty("NumaPlaylist", QVariantMap{{"detail", QVariantMap{}}});
    engine.rootContext()->setContextProperty("NumaAuth", QVariantMap{{"isSignedIn", true}});
    engine.rootContext()->setContextProperty("NumaLibrary", QVariantMap{{"currentLiked", true}, {"likeBusy", false}});
    engine.rootContext()->setContextProperty("NumaMusicVideo", QVariantMap{{"status", ""}, {"available", false}});
    QQmlComponent dawnPreview(&engine);
    dawnPreview.setData(R"(
      import QtQuick
      import QtQuick.Controls
      import Numa
      ApplicationWindow {
        id: preview
        width: 1160; height: 600; color: NumaAppearance.baseColor
        Item { id: backdrop; anchors.fill: parent }
        NavigationSidebar { objectName: "dawnSidebar"; width: 220; height: 500; currentPage: "library" }
        SettingsSelect { x: 800; y: 270; width: 220; model: ["English", "Español"]; currentIndex: 1; Component.onCompleted: popup.open() }
        SettingsNavItem { x: 540; y: 270; width: 200; current: true; entry: ({key: "general", label: "General", icon: "sliders-horizontal"}) }
        SettingsNavItem { x: 540; y: 312; width: 200; entry: ({key: "accounts", label: "Cuentas", icon: "circle-user-round"}) }
        CollectionHero { x: 250; y: 20; width: 260; title: "Música que me gusta"; kind: "Lista de reproducción"; byline: "Edsen"; meta: ["211 canciones", "12 h 21 min"]; gradientCover: true }
        LibraryHeader { x: 540; y: 26; width: 590 }
        LibraryPill { x: 550; y: 110; text: "Todo"; checked: true }
        LibraryPill { x: 620; y: 110; text: "Métricas" }
        MediaCard { x: 550; y: 170; width: 290; presentation: "songlist"; media: ({id: "other", title: "Let It Happen", artist: "Tame Impala", type: "song"}) }
        PlayerBar { objectName: "dawnPlayer"; x: 20; y: 520; width: 1120; height: 64; backdrop: backdrop }
      }
    )", QUrl());
    std::unique_ptr<QObject> dawnObject(dawnPreview.create());
    auto *dawnWindow = qobject_cast<QQuickWindow *>(dawnObject.get());
    if (!dawnWindow) {
      for (const auto &error : dawnPreview.errors()) output << error.toString() << '\n';
      return 1;
    }
    auto *sidebar = dawnObject->findChild<QObject *>("dawnSidebar");
    QVariant same;
    QMetaObject::invokeMethod(sidebar, "samePlaylist", Q_RETURN_ARG(QVariant, same),
        Q_ARG(QVariant, QVariant(QVariantMap{{"id", "offline-playlist:LM"}})),
        Q_ARG(QVariant, QVariant(QVariantMap{{"id", "FEmusic_liked_videos"}})));
    if (!same.toBool()) { output << "Offline likes must keep the same playlist identity.\n"; return 1; }
    QVariant visible;
    QMetaObject::invokeMethod(sidebar, "visiblePlaylists", Q_RETURN_ARG(QVariant, visible),
        Q_ARG(QVariant, QVariant(QVariantList{QVariantMap{{"id", "offline-playlist:LM"}}, QVariantMap{{"id", "VLLM"}}})));
    if (visible.value<QJSValue>().property("length").toInt() != 1) return 1;
    sync->setProperty("playlistItems", QVariantList{
      QVariantMap{{"id", "local-playlist:deleted"}, {"title", "Deleted copy"}},
      QVariantMap{{"id", "local-playlist:existing"}, {"title", "Stale metadata"}},
      QVariantMap{{"id", "PLremote"}, {"title", "Remote"}}});
    QMetaObject::invokeMethod(sidebar, "visiblePlaylists", Q_RETURN_ARG(QVariant, visible),
        Q_ARG(QVariant, QVariant(QVariantList{
          QVariantMap{{"id", "local-playlist:existing"}, {"source", "local"}, {"title", "Device copy"}},
          QVariantMap{{"id", "local-playlist:deleted"}, {"title", "Stale home entry"}}})));
    const auto merged = visible.value<QJSValue>();
    if (merged.property("length").toInt() != 2
        || merged.property(0).property("title").toString() != "Device copy"
        || merged.property(1).property("id").toString() != "PLremote") {
      output << "Deleted local playlists must not be resurrected from cloud or cached home metadata.\n";
      return 1;
    }
    sync->setProperty("playlistItems", QVariantList{});
    dawnWindow->show();
    for (int i = 0; i < 35; ++i) { application.processEvents(); QThread::msleep(10); }
    auto *brand = dawnObject->findChild<QObject *>("applicationBrand");
    auto *dawnPlayer = dawnObject->findChild<QObject *>("dawnPlayer");
    if (!brand || brand->property("color").value<QColor>() != QColor("#575279")
        || !dawnPlayer || dawnPlayer->property("controlColor").value<QColor>() != QColor("#575279")
        || dawnPlayer->property("progressColor") == dawnPlayer->property("trackColor")) {
      output << "Dawn must provide contrasting branding, player controls and progress.\n";
      return 1;
    }
    if (!dawnWindow->grabWindow().save(captureDir + "/rose-pine-dawn.png")) return 1;
    dawnWindow->close();
  }
  QQmlComponent metricsComponent(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/views/library/ListeningMetrics.qml")));
  std::unique_ptr<QObject> metricsObject(metricsComponent.create());
  if (!metricsObject) {
    for (const auto &error : metricsComponent.errors()) output << error.toString() << '\n';
    return 1;
  }
  metricsObject->setProperty("width", 900);
  metricsObject->setProperty("height", 650);
  metricsObject->setProperty("period", 7);
  application.processEvents();
  const QString offlineCover = QStringLiteral("file:///downloaded-cover.png");
  engine.rootContext()->setContextProperty("NumaOffline", QVariantMap{
    {"songs", QVariantList{QVariantMap{{"id", "test"}, {"thumbnail", offlineCover}}}},
    {"playlists", QVariantList{}}});
  QQmlComponent offlineMetrics(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/views/offline/OfflineLibraryView.qml")));
  std::unique_ptr<QObject> offlineMetricsObject(offlineMetrics.create());
  if (!offlineMetricsObject) {
    for (const auto &error : offlineMetrics.errors()) output << error.toString() << '\n';
    return 1;
  }
  offlineMetricsObject->setProperty("filter", "metrics");
  auto *offlineDashboard = offlineMetricsObject->findChild<QObject *>("offlineListeningMetrics");
  if (!offlineDashboard || !offlineDashboard->property("visible").toBool()) {
    output << "Listening metrics must remain accessible offline.\n";
    return 1;
  }
  const auto metricTracks = offlineMetricsObject->property("metricTracks").value<QJSValue>().toVariant().toList();
  if (metricTracks.size() != 1 || metricTracks.first().toMap().value("thumbnail").toString() != offlineCover
      || metricTracks.first().toMap().value("listenCount").toInt() != 6) {
    output << "Offline listening metrics must use downloaded covers and preserve listen counts.\n";
    return 1;
  }
  engine.rootContext()->setContextProperty("NumaPlayback", QVariantMap{
    {"crossfadeEnabled", false}, {"adaptiveMix", QVariantMap{{"mode", "standard"}}},
    {"bestMixSorted", false}, {"bestMix", QVariantMap{{"error", ""}, {"busy", false},
      {"sorting", false}, {"downloading", false}, {"downloaded", 0}, {"completed", 0}, {"total", 0}}}});
  QQmlComponent queuePaneComponent(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/components/player/FullscreenQueuePane.ui.qml")));
  std::unique_ptr<QObject> queuePane(queuePaneComponent.create());
  if (!queuePane) {
    for (const auto &error : queuePaneComponent.errors()) output << error.toString() << '\n';
    return 1;
  }
  queuePane->setProperty("historyItems", QVariantList{QVariantMap{{"id", "played"}}});
  queuePane->setProperty("currentTrack", QVariantMap{{"id", "playing"}});
  queuePane->setProperty("queueItems", QVariantList{QVariantMap{{"id", "next"}}});
  queuePane->setProperty("continuous", true);
  auto *queueList = queuePane->property("list").value<QObject *>();
  auto *clearQueue = queuePane->property("clearButton").value<QObject *>();
  application.processEvents();
  const auto queueRows = queuePane->property("rowItems").value<QJSValue>().toVariant().toList();
  if (!queueList || !clearQueue || queueList->property("count").toInt() != 3
      || queuePane->property("queueStart").toInt() != 2 || queueRows.size() != 3
      || queueRows.at(0).toMap().value("id") != "played"
      || queueRows.at(1).toMap().value("id") != "playing"
      || queueRows.at(2).toMap().value("id") != "next") {
    output << "Fullscreen continuous queue must include history, current song and upcoming songs in order.\n";
    return 1;
  }
  queuePane->setProperty("historyItems", QVariantList{
    QVariantMap{{"id", "old-one"}}, QVariantMap{{"id", "old-two"}},
    QVariantMap{{"id", "recent-one"}}, QVariantMap{{"id", "recent-two"}}});
  application.processEvents();
  const auto recentRows = queuePane->property("rowItems").value<QJSValue>().toVariant().toList();
  if (queuePane->property("historyOffset").toInt() != 2 || queuePane->property("historyCount").toInt() != 2
      || recentRows.size() != 4 || recentRows.at(0).toMap().value("id") != "recent-one"
      || recentRows.at(1).toMap().value("id") != "recent-two"
      || recentRows.at(2).toMap().value("id") != "playing") {
    output << "Continuous queue must show only the two most recently played songs before the current one.\n";
    return 1;
  }
  queuePane->setProperty("historyItems", QVariantList{QVariantMap{{"id", "played"}}});
  queuePane->setProperty("dragIndex", 0);
  application.processEvents();
  auto *dragPreview = queuePane->findChild<QObject *>("fullscreenQueueDragPreview");
  if (queueList->property("interactive").toBool() || !dragPreview
      || !dragPreview->property("dragPreview").toBool() || !dragPreview->property("visible").toBool()) {
    output << "Fullscreen queue dragging must show its normal row preview and suspend list gestures.\n";
    return 1;
  }
  queuePane->setProperty("dragIndex", -1);
  QQmlComponent draggableRowComponent(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/components/player/FullscreenQueueRow.ui.qml")));
  std::unique_ptr<QObject> draggableRow(draggableRowComponent.create());
  if (!draggableRow) return 1;
  auto *rowContent = draggableRow->property("contentItem").value<QObject *>();
  for (const auto &section : {QString(), QStringLiteral("UP NEXT")}) {
    draggableRow->setProperty("sectionLabel", section);
    application.processEvents();
    const qreal header = draggableRow->property("sectionHeight").toReal();
    const qreal frameCenter = header + (draggableRow->property("height").toReal() - header) / 2;
    const qreal contentCenter = rowContent ? rowContent->property("y").toReal() + rowContent->property("height").toReal() / 2 : -1;
    if (qAbs(frameCenter - contentCenter) > 0.5) {
      output << "Queue row content must be centered inside its frame, including section headers.\n";
      return 1;
    }
  }
  draggableRow->setProperty("dragPane", QVariant::fromValue(queuePane.get()));
  draggableRow->setProperty("queueIndex", 0);
  if (!draggableRow->property("draggable").toBool()) return 1;
  draggableRow->setProperty("previous", true);
  if (draggableRow->property("draggable").toBool()) return 1;
  draggableRow->setProperty("previous", false);
  draggableRow->setProperty("current", true);
  if (draggableRow->property("draggable").toBool()) return 1;
  draggableRow->setProperty("current", false);
  draggableRow->setProperty("dragPreview", true);
  if (draggableRow->property("draggable").toBool()) return 1;
  queuePane->setProperty("queueItems", QVariantList{});
  application.processEvents();
  if (queueList->property("count").toInt() != 2 || clearQueue->property("enabled").toBool()) {
    output << "An empty upcoming queue must preserve history and the current song without enabling Clear.\n";
    return 1;
  }
  queuePane->setProperty("continuous", false);
  queuePane->setProperty("queueItems", QVariantList{QVariantMap{{"id", "next"}}});
  application.processEvents();
  if (queueList->property("count").toInt() != 1 || queuePane->property("queueStart").toInt() != 0) {
    output << "Standard fullscreen queue must contain upcoming songs only.\n";
    return 1;
  }
  QQmlComponent playlistArtworkComponent(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/components/PlaylistArtwork.qml")));
  std::unique_ptr<QObject> playlistArtwork(playlistArtworkComponent.create());
  if (!playlistArtwork) {
    for (const auto &error : playlistArtworkComponent.errors()) output << error.toString() << '\n';
    return 1;
  }
  for (const auto &entry : {qMakePair(QStringLiteral("VLLM"), QStringLiteral("liked")),
                           qMakePair(QStringLiteral("offline-playlist:SE"), QStringLiteral("episodes")),
                           qMakePair(QStringLiteral("local-playlist:example"), QStringLiteral("local"))}) {
    playlistArtwork->setProperty("media", QVariantMap{{"id", entry.first}, {"title", "Example"},
      {"thumbnail", "https://example.invalid/old-cover.jpg"}});
    if (playlistArtwork->property("kind").toString() != entry.second || !playlistArtwork->property("generated").toBool()) {
      output << "Default and local playlists must use a stable themed cover.\n";
      return 1;
    }
  }
  playlistArtwork->setProperty("customArtwork", QStringLiteral("qrc:/qt/qml/Numa/app/qml/assets/lucide/list-music.svg"));
  if (playlistArtwork->property("generated").toBool()) {
    output << "A custom playlist cover must take priority over generated artwork.\n";
    return 1;
  }
  playlistArtwork->setProperty("customArtwork", QString());
  playlistArtwork->setProperty("media", QVariantMap{{"id", "local-playlist:example"}, {"hasCustomCover", true},
    {"thumbnail", "qrc:/qt/qml/Numa/app/qml/assets/lucide/list-music.svg"}});
  if (playlistArtwork->property("generated").toBool()) return 1;
  sync->setProperty("saved", false);
  engine.rootContext()->setContextProperty("NumaLocal", QVariantMap{{"playlists", QVariantList{
    QVariantMap{{"id", "local-playlist:existing"}, {"title", "Local list"}}}}});
  QQmlComponent localCollectionComponent(&engine);
  localCollectionComponent.setData("import QtQuick\nimport Numa\nCloudCollectionAction { media: ({id: 'local-playlist:existing', source: 'local', type: 'playlist', title: 'Local list'}) }", QUrl());
  std::unique_ptr<QObject> localCollection(localCollectionComponent.create());
  if (!localCollection) return 1;
  auto *localLibraryButton = localCollection->findChild<QObject *>("cloudLibraryButton");
  if (!localCollection->property("saved").toBool() || !localLibraryButton
      || localLibraryButton->property("glyph").toString() != "check"
      || !localLibraryButton->property("enabled").toBool() || sync->property("saved").toBool()) {
    output << "Existing local playlists must be marked as saved independently of cloud-library membership.\n";
    return 1;
  }
  engine.rootContext()->setContextProperty("NumaLocal", QVariantMap{{"playlists", QVariantList{}}});
  if (localCollection->property("saved").toBool() || localLibraryButton->property("enabled").toBool()) {
    output << "Local collection state must update when the playlist leaves the device library.\n";
    return 1;
  }
  engine.rootContext()->setContextProperty("NumaLocal", QVariantMap{{"songs", QVariantList{QVariantMap{{"id", "local:test"}}}}});
  QQmlComponent localOptionsComponent(&engine);
  localOptionsComponent.setData(R"(
import QtQuick
import Numa
LocalMediaDownloadOptions {
    width: 600
    downloads: QtObject {
        property bool lyrics: true
        property bool translation: false
        property bool artwork: true
        property bool busy: false
        property int total: 0
        property int completed: 0
        property string message: ""
    }
}
)", QUrl());
  std::unique_ptr<QObject> localOptions(localOptionsComponent.create());
  if (!localOptions) {
    for (const auto &error : localOptionsComponent.errors()) output << error.toString() << '\n';
    return 1;
  }
  qobject_cast<QQuickItem *>(localOptions.get())->setParentItem(window->contentItem());
  application.processEvents();
  const auto findVisual = [](auto &&self, QQuickItem *item, const QString &name) -> QQuickItem * {
    if (item->objectName() == name) return item;
    for (auto *child : item->childItems()) if (auto *match = self(self, child, name)) return match;
    return nullptr;
  };
  for (int i = 0; i < 3; ++i) {
    auto *toggle = findVisual(findVisual, qobject_cast<QQuickItem *>(localOptions.get()), QStringLiteral("localDownloadSwitch%1").arg(i));
    if (!toggle || toggle->property("checked").toBool() != (i != 1)) {
      output << "Local download switches must reflect the selected options: " << i << " found=" << bool(toggle) << " checked=" << (toggle ? toggle->property("checked").toBool() : false) << "\n";
      return 1;
    }
  }
  QQmlComponent localMenuComponent(&engine);
  localMenuComponent.setData(R"(
import QtQuick
import Numa
MediaMenu {
    property var requestedAlbum: ({})
    property var requestedArtist: ({})
    media: ({id: 'local:test', type: 'song', title: 'Song', artist: 'Artist', album: 'Album', localPlaylistId: 'local-playlist:test'})
    onAlbumRequested: album => requestedAlbum = album
    onArtistRequested: artist => requestedArtist = artist
}
)", QUrl());
  std::unique_ptr<QObject> localMenu(localMenuComponent.create());
  if (!localMenu) return 1;
  QMetaObject::invokeMethod(localMenu.get(), "viewAlbum");
  QMetaObject::invokeMethod(localMenu.get(), "viewArtist", Q_ARG(QVariant, QVariant()), Q_ARG(QVariant, QVariant()), Q_ARG(QVariant, QVariant()));
  if (!localMenu->property("requestedAlbum").value<QJSValue>().toVariant().toMap().value("lookupByName").toBool()
      || !localMenu->property("requestedArtist").value<QJSValue>().toVariant().toMap().value("lookupByName").toBool()) {
    output << "Local songs must resolve album and artist pages by name.\n";
    return 1;
  }
  for (auto *entry : localMenu->findChildren<QObject *>()) {
    const QString text = entry->property("text").toString();
    if (text == QStringLiteral("Set cover…") || text == QStringLiteral("Set lyrics…") || text == QStringLiteral("Remove from library")) {
      output << "Local song menus must not include removed editing actions.\n";
      return 1;
    }
  }
  if (localOptions->property("implicitHeight").toDouble() < 150) {
    output << "Local download options must have a visible layout.\n";
    return 1;
  }
  QQmlComponent localBadgesComponent(&engine);
  localBadgesComponent.setData(R"(
import QtQuick
import Numa
CollectionTrackRow {
    width: 700
    showLocalExtras: true
    property string requestedAlbumId
    track: ({id: 'local:test', title: 'Test song', albumId: 'album-test', hasDownloadedLyrics: true, hasDownloadedArtwork: true})
    onAlbumRequested: album => requestedAlbumId = album.browseId
    function requestAlbum() { useTrackMenu(menu => menu.viewAlbum()); }
}
)", QUrl());
  std::unique_ptr<QObject> localBadges(localBadgesComponent.create());
  if (!localBadges) {
    for (const auto &error : localBadgesComponent.errors()) output << error.toString() << '\n';
    return 1;
  }
  auto *trackMenuLoader = localBadges->findChild<QObject *>("trackMenuLoader");
  if (!trackMenuLoader || trackMenuLoader->property("active").toBool()) return 1;
  QMetaObject::invokeMethod(localBadges.get(), "requestAlbum");
  application.processEvents();
  if (localBadges->property("requestedAlbumId").toString() != "album-test"
      || trackMenuLoader->property("active").toBool()) {
    output << "Track menus must load on demand, keep album navigation and release themselves.\n";
    return 1;
  }
  auto *lyricsBadge = localBadges->findChild<QObject *>("localLyricsBadge");
  auto *artworkBadge = localBadges->findChild<QObject *>("localArtworkBadge");
  if (!lyricsBadge || !artworkBadge || !lyricsBadge->property("visible").toBool() || !artworkBadge->property("visible").toBool()) return 1;
  localBadges->setProperty("showLocalExtras", false);
  if (lyricsBadge->property("visible").toBool() || artworkBadge->property("visible").toBool()) return 1;
  playlistArtwork->setProperty("media", QVariantMap{{"id", "local-playlist:test"}, {"source", "local"}});
  playlistArtwork->setProperty("customArtwork", QString());
  const QString generatedBackground = playlistArtwork->property("backgroundArtwork").toString();
  QImage backgroundImage;
  if (!generatedBackground.startsWith(QStringLiteral("data:image/svg+xml;base64,"))
      || !backgroundImage.loadFromData(QByteArray::fromBase64(generatedBackground.section(',', 1).toLatin1()))
      || backgroundImage.pixelColor(256, 10) == backgroundImage.pixelColor(256, 500)) {
    output << "Generated playlist artwork must supply a decodable gradient image for its background.\n";
    return 1;
  }
  QQmlContext deviceLibraryContext(engine.rootContext());
  deviceLibraryContext.setContextProperty("NumaSync", QVariantMap{{"revision", -1}});
  deviceLibraryContext.setContextProperty("NumaHome", QVariantMap{{"sections", QVariantList{QVariantMap{
    {"title", "Library"}, {"items", QVariantList{QVariantMap{{"id", "LM"}, {"type", "playlist"}},
      QVariantMap{{"id", "local-playlist:other-device"}, {"type", "playlist"}}}}}}}});
  QTemporaryDir deviceLibraryRoot;
  LocalLibrary deviceLibrary(deviceLibraryRoot.path());
  const QString devicePlaylistId = deviceLibrary.createPlaylist(QStringLiteral("Hika"));
  deviceLibraryContext.setContextProperty("NumaLocal", &deviceLibrary);
  QQmlComponent homeLibraryComponent(&engine, QUrl(QStringLiteral("qrc:/qt/qml/Numa/app/qml/views/home/HomeView.qml")));
  std::unique_ptr<QObject> homeLibrary(homeLibraryComponent.create(&deviceLibraryContext));
  if (!homeLibrary) return 1;
  const auto libraryFeed = homeLibrary->property("feed").value<QJSValue>().toVariant().toList();
  if (libraryFeed.size() != 1) { output << "Device playlists must join the existing Library shelf, not create a second shelf at the bottom.\n"; return 1; }
  const auto libraryItems = libraryFeed.first().toMap().value("items").toList();
  if (libraryItems.size() != 2 || libraryItems.last().toMap().value("id") != devicePlaylistId) {
    output << "Home library must include device-local playlists and exclude other devices' local lists.\n";
    return 1;
  }
  QQmlComponent volumeModelComponent(&engine);
  volumeModelComponent.setData("import QtQuick\nQtObject { property real volume: 0.37 }", QUrl());
  std::unique_ptr<QObject> volumeModel(volumeModelComponent.create());
  QQmlContext playerContext(engine.rootContext());
  playerContext.setContextProperty("NumaHome", volumeModel.get());
  auto playerAppearance = engine.rootContext()->contextProperty("NumaAppearance").toMap();
  playerAppearance.insert("showBitrate", false);
  playerContext.setContextProperty("NumaAppearance", playerAppearance);
  QVariantMap localTrack{{"id", "local:format-test"}, {"title", "Local song"}, {"artist", "Artist"}, {"codec", "flac"}};
  auto playerState = engine.rootContext()->contextProperty("NumaPlayback").toMap();
  playerState.insert("track", localTrack);
  playerState.insert("transitionTrack", QVariantMap{});
  playerState.insert("bitrate", 757000);
  playerState.insert("playing", false);
  playerState.insert("loading", false);
  playerState.insert("queue", QVariantList{});
  playerState.insert("repeatMode", "off");
  playerState.insert("shuffleEnabled", false);
  playerContext.setContextProperty("NumaPlayback", playerState);
  QQmlContext crystalContext(&playerContext);
  auto crystalAppearance = playerAppearance;
  crystalAppearance.insert("theme", "crystal");
  crystalAppearance.insert("rosePine", false);
  crystalAppearance.insert("surfaceColor", "#660e1210");
  crystalAppearance.insert("performanceMode", false);
  crystalContext.setContextProperty("NumaAppearance", crystalAppearance);
  auto crystalPlayback = playerState;
  crystalPlayback.insert("track", QVariantMap{{"id", "cover-red"}, {"thumbnail", firstCoverUrl.toString()}});
  crystalContext.setContextProperty("NumaPlayback", crystalPlayback);
  QQmlComponent crystalControls(&engine);
  crystalControls.setData(R"(
    import QtQuick
    import Numa
    Window {
      id: stage
      width: 1100
      height: 160
      property color playbackAccentColor: dock.accentColor
      PlayerBar { id: dock; objectName: "crystalDock"; width: stage.width; backdrop: stage.contentItem }
      ThemedScrollBar { objectName: "crystalScroll"; x: 1080; y: 70; height: 80; size: 0.5 }
      SettingsSelect { objectName: "crystalSettingsSelect"; y: 80; model: ["A", "B"] }
      SettingsComboBox { objectName: "crystalSettingsCombo"; x: 200; y: 80; model: ["A", "B"] }
      CollectionTrackRow { x: 400; y: 80; width: 600; track: NumaPlayback.track }
    }
  )", QUrl());
  std::unique_ptr<QObject> crystalWindow(crystalControls.create(&crystalContext));
  if (!crystalWindow) { output << crystalControls.errorString() << '\n'; return 1; }
  const auto findControl = [](auto &&self, QQuickItem *item, const QString &name) -> QQuickItem * {
    if (item->objectName() == name) return item;
    for (auto *child : item->childItems())
      if (auto *match = self(self, child, name)) return match;
    return nullptr;
  };
  auto *crystalContent = qobject_cast<QQuickWindow *>(crystalWindow.get())->contentItem();
  auto *crystalDock = findControl(findControl, crystalContent, QStringLiteral("crystalDock"));
  auto *crystalPlay = findControl(findControl, crystalContent, QStringLiteral("miniPlayPauseButton"));
  auto *crystalFill = findControl(findControl, crystalContent, QStringLiteral("miniPlaybackFill"));
  auto *crystalScroll = findControl(findControl, crystalContent, QStringLiteral("crystalScroll"));
  for (const QString &name : {QStringLiteral("crystalSettingsSelect"), QStringLiteral("crystalSettingsCombo")}) {
    auto *control = findControl(findControl, crystalContent, name);
    auto *popup = control ? control->property("popup").value<QObject *>() : nullptr;
    auto *surface = popup ? popup->property("background").value<QObject *>() : nullptr;
    const auto fill = surface ? surface->property("surfaceFill").value<QColor>() : QColor();
    if (fill.name() != "#0e1210" || fill.alpha() != 184) {
      output << "Settings option menus must use a darker translucent version of the panel surface.\n";
      return 1;
    }
  }
  auto *playBackground = crystalPlay ? crystalPlay->property("background").value<QObject *>() : nullptr;
  if (!crystalDock || !playBackground || !crystalFill || !crystalScroll) {
    output << "The mini player controls and scrollbar must be present in the Cristal fixture.\n";
    return 1;
  }
  const auto crystalAccent = [&] { return crystalDock->property("accentColor").value<QColor>(); };
  const auto allFollowCover = [&] {
    const auto *indicator = findControl(findControl, crystalContent, QStringLiteral("playingIndicatorBar"));
    return playBackground->property("color").value<QColor>() == crystalAccent()
        && crystalFill->property("baseColor").value<QColor>() == crystalAccent()
        && crystalScroll->property("thumbColor").value<QColor>() == crystalAccent()
        && indicator && indicator->property("color").value<QColor>() == crystalAccent();
  };
  if (crystalAccent().red() <= crystalAccent().blue() || !allFollowCover()) {
    output << "Cristal must apply the song's cover color to play, progress and scrolling.\n";
    return 1;
  }
  const auto lastAccent = crystalAccent();
  const QString pendingCover = artworkDirectory.filePath("pending-blue.png");
  if (!QFile::copy(nextCoverPath, pendingCover)) return 1;
  crystalPlayback.insert("track", QVariantMap{{"id", "cover-blue"}, {"thumbnail", QUrl::fromLocalFile(pendingCover).toString()}});
  crystalPlayback.insert("playing", true);
  crystalContext.setContextProperty("NumaPlayback", crystalPlayback);
  if (crystalAccent() != lastAccent || !allFollowCover() || crystalPlay->property("iconName").toString() != "pause") {
    output << "Cristal must keep its current accent while the next cover loads.\n";
    return 1;
  }
  QElapsedTimer crystalTransition;
  crystalTransition.start();
  while (crystalAccent().blue() <= crystalAccent().red() && crystalTransition.elapsed() < 3000) {
    application.processEvents();
    QThread::msleep(5);
  }
  if (crystalAccent().blue() <= crystalAccent().red() || !allFollowCover()) {
    output << "Cristal's play, progress, scroll and row indicator must follow the new cover together.\n";
    return 1;
  }
  for (const QString theme : {QStringLiteral("rose_pine"), QStringLiteral("rose_pine_dawn")}) {
    const QString accent = theme.endsWith("dawn") ? QStringLiteral("#d7827e") : QStringLiteral("#ebbcba");
    auto roseAppearance = crystalAppearance;
    roseAppearance.insert("theme", theme);
    roseAppearance.insert("rosePine", true);
    roseAppearance.insert("accentColor", accent);
    crystalContext.setContextProperty("NumaAppearance", roseAppearance);
    const auto *indicator = findControl(findControl, crystalContent, QStringLiteral("playingIndicatorBar"));
    if (!indicator || indicator->property("color").value<QColor>() != QColor(accent)) {
      output << "Rosé Pine's row indicator must keep the selected theme accent.\n";
      return 1;
    }
  }
  crystalWindow.reset();
  QQmlComponent playerTestComponent(&engine);
  playerTestComponent.setData("import QtQuick\nimport Numa\nItem { id: stage; width: 1100; height: 70; PlayerBar { objectName: 'volumeTestBar'; anchors.fill: parent; backdrop: stage } }", QUrl());
  std::unique_ptr<QObject> playerTest(playerTestComponent.create(&playerContext));
  if (!playerTest) return 1;
  auto *testBar = playerTest->findChild<QObject *>("volumeTestBar");
  if (!testBar) return 1;
  QMetaObject::invokeMethod(testBar, "toggleMute");
  if (volumeModel->property("volume").toDouble() != 0) return 1;
  QMetaObject::invokeMethod(testBar, "toggleMute");
  if (qAbs(volumeModel->property("volume").toDouble() - 0.37) > 0.0001) return 1;
  QVariant localFormat;
  QMetaObject::invokeMethod(testBar, "localLabel", Q_RETURN_ARG(QVariant, localFormat), Q_ARG(QVariant, QVariant(localTrack)));
  if (localFormat.toString() != QStringLiteral("FLAC")) {
    output << "Local bitrates must respect the bitrate setting.\n";
    return 1;
  }
  playerAppearance.insert("showBitrate", true);
  playerContext.setContextProperty("NumaAppearance", playerAppearance);
  QMetaObject::invokeMethod(testBar, "localLabel", Q_RETURN_ARG(QVariant, localFormat), Q_ARG(QVariant, QVariant(localTrack)));
  if (!localFormat.toString().contains(QStringLiteral("757 kbps"))) return 1;
  for (const QString theme : {QStringLiteral("rose_pine"), QStringLiteral("rose_pine_dawn")}) {
    const bool dawn = theme.endsWith("dawn");
    QQmlContext bloomContext(engine.rootContext());
    bloomContext.setContextProperty("NumaAppearance", QVariantMap{{"theme", theme}, {"rosePine", true},
      {"textColor", dawn ? "#575279" : "#e0def4"}, {"baseColor", dawn ? "#faf4ed" : "#191724"}});
    QQmlComponent bloomComponent(&engine);
    bloomComponent.setData(R"(
import QtQuick
import QtQuick.Controls
import Numa
ApplicationWindow {
    id: preview
    width: 280; height: 120
    color: NumaAppearance.baseColor
    property color paint: NumaAppearance.theme === "rose_pine_dawn" ? "#efd4cf" : "#ebbcba"
    property bool glowHovered: true
    Button {
        id: pinkButton
        HandCursor {}
        x: 50; y: 40; width: 180; height: 40
        background: Rectangle { radius: 10; color: preview.paint }
        contentItem: Text { text: "Buscar actualizaciones"; color: "#575279"; horizontalAlignment: Text.AlignHCenter; verticalAlignment: Text.AlignVCenter }
        HoverTint { objectName: "testBloom"; control: pinkButton; hovered: preview.glowHovered }
    }
}
)", QUrl());
    std::unique_ptr<QObject> bloomPreview(bloomComponent.create(&bloomContext));
    if (!bloomPreview) {
      for (const auto &error : bloomComponent.errors()) output << error.toString() << '\n';
      return 1;
    }
    auto *halo = bloomPreview->findChild<QObject *>("testBloom");
    if (!halo || !halo->property("active").toBool()) return 1;
    for (const auto &excluded : {"#fffaf3", "#eb6f92", "#40323e", "#d7827e"}) {
      bloomPreview->setProperty("paint", QColor(excluded));
      if (halo->property("active").toBool()) {
        output << "Bloom must remain off for other button colors: " << excluded << '\n';
        return 1;
      }
    }
    bloomPreview->setProperty("paint", QColor(dawn ? "#efd4cf" : "#ebbcba"));
    bloomPreview->setProperty("glowHovered", false);
    if (halo->property("active").toBool()) return 1;
    bloomPreview->setProperty("glowHovered", true);
    auto *bloomWindow = qobject_cast<QQuickWindow *>(bloomPreview.get());
    bloomWindow->show();
    QElapsedTimer renderWait; renderWait.start();
    while (renderWait.elapsed() < 260) { application.processEvents(); QThread::msleep(5); }
    QMouseEvent hoverMove(QEvent::MouseMove, QPointF(100, 60), QPointF(bloomWindow->mapToGlobal(QPoint(100, 60))), Qt::NoButton, Qt::NoButton, Qt::NoModifier);
    QCoreApplication::sendEvent(bloomWindow, &hoverMove);
    application.processEvents();
    auto *createdHalo = bloomPreview->findChild<QObject *>("buttonHoverTint");
    if (!createdHalo || !createdHalo->property("active").toBool()) {
      output << "Hovering a pink button must create its shared hover tint automatically.\n";
      return 1;
    }
    bloomWindow->close();
  }
  QQmlContext updateContext(engine.rootContext());
  updateContext.setContextProperty("NumaAuth", QVariantMap{{"isSignedIn", false}});
  updateContext.setContextProperty("NumaMusicVideo", QVariantMap{{"open", false}});
  updateContext.setContextProperty("NumaPlayback", QVariantMap{{"track", QVariantMap{{"id", ""}}}});
  updateContext.setContextProperty("NumaReleaseNotes", QVariantList{});
  updateContext.setContextProperty("NumaSupport", QVariant::fromValue<QObject *>(nullptr));
  QQmlComponent updateModelComponent(&engine);
  updateModelComponent.setData(R"(
import QtQuick
QtObject {
    signal changed()
    property string state: "available"
    property string latestVersion: "2.7"
    property string releaseNotes: "- Menús y controles actualizados."
    property bool installsInApp: true
    property string error: ""
    property int downloaded: 20
    property int total: 100
}
)", QUrl());
  std::unique_ptr<QObject> updateModel(updateModelComponent.create(&updateContext));
  if (!updateModel) return 1;
  updateContext.setContextProperty("NumaUpdates", updateModel.get());
  QQmlComponent updatePreviewComponent(&engine);
  updatePreviewComponent.setData(R"(
import QtQuick
import QtQuick.Controls
import Numa
ApplicationWindow {
    width: 420
    height: 360
    Item {
        id: updateStage
        anchors.fill: parent
        property string currentPage: "home"
        property bool fullscreenOpen: false
        function isEditableFocused() { return false }
        HomeOverlays { shell: updateStage; stage: updateStage }
    }
}
)", QUrl());
  std::unique_ptr<QObject> updatePreview(updatePreviewComponent.create(&updateContext));
  if (!updatePreview) {
    for (const auto &error : updatePreviewComponent.errors()) output << error.toString() << '\n';
    return 1;
  }
  auto *updateWindow = qobject_cast<QQuickWindow *>(updatePreview.get());
  auto *updatePrompt = updatePreview->findChild<QObject *>("updatePrompt");
  auto *updateNotes = updatePreview->findChild<QObject *>("updateNotes");
  auto *updateInstallButton = updatePreview->findChild<QObject *>("updateInstallButton");
  auto *updateError = updatePreview->findChild<QObject *>("updateError");
  if (!updateWindow || !updatePrompt || !updateNotes || !updateInstallButton || !updateError) return 1;
  updateWindow->show();
  QMetaObject::invokeMethod(updateModel.get(), "changed");
  const auto settleUpdate = [&] {
    for (int i = 0; i < 8; ++i) {
      application.processEvents();
      updateWindow->contentItem()->polish();
      QThread::msleep(5);
    }
  };
  settleUpdate();
  const auto shortHeight = updatePrompt->property("height").toDouble();
  updateModel->setProperty("releaseNotes", QStringLiteral("- Cambios en el reproductor y la biblioteca.\n").repeated(36));
  settleUpdate();
  const auto longHeight = updatePrompt->property("height").toDouble();
  if (!updatePrompt->property("visible").toBool() || shortHeight < 140 || shortHeight >= longHeight
      || longHeight > updateWindow->height() - 32
      || updateNotes->property("contentHeight").toDouble() <= updateNotes->property("height").toDouble()) {
    output << "The update prompt must fit short notes and scroll long notes inside the window."
           << " short=" << shortHeight << " long=" << longHeight << '\n';
    return 1;
  }
  updateModel->setProperty("releaseNotes", QString());
  settleUpdate();
  if (updateNotes->property("visible").toBool() || updatePrompt->property("height").toDouble() >= shortHeight) {
    output << "An update without notes must omit the notes surface.\n";
    return 1;
  }
  const QString inAppAction = updateInstallButton->property("text").toString();
  for (const auto &state : {QStringLiteral("downloading"), QStringLiteral("authorizing")}) {
    updateModel->setProperty("state", state);
    settleUpdate();
    if (updateInstallButton->property("enabled").toBool()) {
      output << "The installer action must be disabled while downloading or awaiting approval.\n";
      return 1;
    }
  }
  updateModel->setProperty("state", QStringLiteral("available"));
  updateModel->setProperty("error", QStringLiteral("Installation canceled."));
  settleUpdate();
  if (!updateError->property("visible").toBool() || !updateInstallButton->property("enabled").toBool()) {
    output << "A canceled installation must show feedback and allow a retry.\n";
    return 1;
  }
  updateModel->setProperty("installsInApp", false);
  settleUpdate();
  if (updateInstallButton->property("text").toString() == inAppAction) {
    output << "Legacy installers must keep a distinct download action.\n";
    return 1;
  }
  updateWindow->close();
  output << "Numa QML startup check passed.\n";
  return 0;
}
