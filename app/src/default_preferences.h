#pragma once

#include <QSettings>
#include <QVariantMap>

inline QVariantMap defaultPreferences() {
  QVariantMap preferences{
      {QStringLiteral("ui/language"), QStringLiteral("es")},
      {QStringLiteral("appearance/layoutStyle"), QStringLiteral("glade")},
      {QStringLiteral("appearance/performanceMode"), false},
      {QStringLiteral("appearance/showBitrate"), false},
      {QStringLiteral("appearance/immersiveBackground"), true},
      {QStringLiteral("appearance/speed"), 2.0},
      {QStringLiteral("appearance/intensity"), 1.0},
      {QStringLiteral("appearance/saturation"), 1.5},
      {QStringLiteral("appearance/brightness"), 1.0},
      {QStringLiteral("appearance/fullscreenAutoHide"), true},
      {QStringLiteral("appearance/fullscreenPulse"), true},
      {QStringLiteral("appearance/artistVideoBackdrop"), true},
      {QStringLiteral("appearance/artworkSource"), QStringLiteral("apple_music")},
      {QStringLiteral("appearance/mirrorOrder"), QStringList{QStringLiteral("m8tec"), QStringLiteral("boidu"), QStringLiteral("spotify")}},
      {QStringLiteral("appearance/animatedArtworkEnabled"), true},
      {QStringLiteral("appearance/animatedCollageEnabled"), true},
      {QStringLiteral("downloads/animatedArtwork"), true},
      {QStringLiteral("downloads/quality"), QStringLiteral("high")},
      {QStringLiteral("musicVideo/maxHeight"), 1440},
      {QStringLiteral("playback/repeatMode"), QStringLiteral("off")},
      {QStringLiteral("playback/shuffleEnabled"), false},
      {QStringLiteral("playback/exponentialVolumeEnabled"), true},
      {QStringLiteral("playback/crossfadeEnabled"), true},
      {QStringLiteral("playback/crossfadeDuration"), 3},
      {QStringLiteral("playback/crossfadeMode"), QStringLiteral("standard")},
      {QStringLiteral("playback/gaplessEnabled"), true},
      {QStringLiteral("playback/volume"), 1.0},
      {QStringLiteral("playback/queueLayout"), QStringLiteral("continuous")},
      {QStringLiteral("playback/nonMusicSkipMode"), QStringLiteral("auto")},
      {QStringLiteral("playback/streamQuality"), QStringLiteral("high")},
      {QStringLiteral("playback/autoplayEnabled"), true},
      {QStringLiteral("playback/persistenceEnabled"), true},
      {QStringLiteral("playback/youtubeHistoryEnabled"), true},
      {QStringLiteral("playback/audioEngine/enabled"), true},
      {QStringLiteral("playback/audioEngine/autoEqEnabled"), true},
      {QStringLiteral("playback/audioEngine/eqEnabled"), false},
      {QStringLiteral("playback/audioEngine/gains"), QVariantList{0., 0., 0., 0., 0., 0., 0., 0., 0., 0.}},
      {QStringLiteral("playback/audioEngine/preampDb"), 0.0},
      {QStringLiteral("playback/audioEngine/outputGainDb"), 0.0},
      {QStringLiteral("playback/audioEngine/q"), 1.1},
      {QStringLiteral("playback/audioEngine/balance"), 0.0},
      {QStringLiteral("playback/audioEngine/normalizationEnabled"), false},
      {QStringLiteral("lyrics/translationProvider"), QStringLiteral("google")},
      {QStringLiteral("lyrics/translationQuality"), QStringLiteral("high")},
      {QStringLiteral("lyrics/translate"), true},
      {QStringLiteral("lyrics/translationTargetLanguage"), QStringLiteral("spanish")},
      {QStringLiteral("window/remember"), true},
      {QStringLiteral("tray/closeToTray"), true},
  };
#ifdef Q_OS_WIN
  // Initial Windows profile captured from the approved application preferences.
  // Device IDs, window geometry, accounts and playback history are excluded.
  const QVariantMap windowsPreferences{
      {QStringLiteral("appearance/theme"), QStringLiteral("crystal")},
      {QStringLiteral("appearance/bordersEnabled"), false},
      {QStringLiteral("appearance/playlistSongBackground"), true},
      {QStringLiteral("downloads/localLyrics"), false},
      {QStringLiteral("downloads/localTranslation"), false},
      {QStringLiteral("downloads/localArtwork"), false},
      {QStringLiteral("integrations/discord/enabled"), false},
      {QStringLiteral("integrations/discord/showSyncedLyrics"), true},
      {QStringLiteral("playback/audioEngine/enabled"), false},
      {QStringLiteral("playback/audioEngine/q"), 1.0},
      {QStringLiteral("playback/slopAction"), QStringLiteral("off")},
  };
  for (auto it = windowsPreferences.cbegin(); it != windowsPreferences.cend(); ++it)
    preferences.insert(it.key(), it.value());
#endif
  return preferences;
}

// Existing profiles retain both explicit choices and their historical defaults.
inline bool initializeDefaultPreferences(QSettings &settings) {
  if (!settings.allKeys().isEmpty())
    return false;
  const QVariantMap defaults = defaultPreferences();
  for (auto it = defaults.cbegin(); it != defaults.cend(); ++it)
    settings.setValue(it.key(), it.value());
  settings.sync();
  return settings.status() == QSettings::NoError;
}
