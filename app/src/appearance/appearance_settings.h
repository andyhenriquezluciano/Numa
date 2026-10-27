/*
 * Copyright (C) 2026 SFG545
 *
 * This file is part of Numa.
 *
 * Numa is free software: you can redistribute it and/or modify it under the
 * terms of the GNU Affero General Public License as published by the Free
 * Software Foundation, either version 3 of the License, or (at your option) any
 * later version.
 *
 * Numa is distributed in the hope that it will be useful, but WITHOUT ANY
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

#pragma once
#include <QObject>
#include <QColor>
#include <QSettings>

class AppearanceSettings final : public QObject {
    Q_OBJECT
    Q_PROPERTY(bool immersiveBackground READ immersiveBackground WRITE setImmersiveBackground NOTIFY changed)
    Q_PROPERTY(bool showBitrate READ showBitrate WRITE setShowBitrate NOTIFY changed)
    Q_PROPERTY(bool welcomeSeen READ welcomeSeen WRITE setWelcomeSeen NOTIFY changed)
    Q_PROPERTY(QString lastSeenRelease READ lastSeenRelease WRITE setLastSeenRelease NOTIFY changed)
    Q_PROPERTY(QString layoutStyle READ layoutStyle WRITE setLayoutStyle NOTIFY changed)
    Q_PROPERTY(double speed READ speed WRITE setSpeed NOTIFY changed)
    Q_PROPERTY(double intensity READ intensity WRITE setIntensity NOTIFY changed)
    Q_PROPERTY(double saturation READ saturation WRITE setSaturation NOTIFY changed)
    Q_PROPERTY(double brightness READ brightness WRITE setBrightness NOTIFY changed)
    Q_PROPERTY(bool fullscreenAutoHide READ fullscreenAutoHide WRITE setFullscreenAutoHide NOTIFY changed)
    Q_PROPERTY(bool fullscreenPulse READ fullscreenPulse WRITE setFullscreenPulse NOTIFY changed)
    Q_PROPERTY(bool artistVideoBackdrop READ artistVideoBackdrop WRITE setArtistVideoBackdrop NOTIFY changed)
    Q_PROPERTY(bool animatedArtworkEnabled READ animatedArtworkEnabled WRITE setAnimatedArtworkEnabled NOTIFY changed)
    Q_PROPERTY(bool animatedCollageEnabled READ animatedCollageEnabled WRITE setAnimatedCollageEnabled NOTIFY changed)
    Q_PROPERTY(bool performanceMode READ performanceMode NOTIFY changed)
    Q_PROPERTY(QString theme READ theme WRITE setTheme NOTIFY changed)
    Q_PROPERTY(bool rosePine READ performanceMode NOTIFY changed)
    Q_PROPERTY(QColor baseColor READ baseColor NOTIFY changed)
    Q_PROPERTY(QColor surfaceColor READ surfaceColor NOTIFY changed)
    Q_PROPERTY(QColor textColor READ textColor NOTIFY changed)
    Q_PROPERTY(QColor mutedColor READ mutedColor NOTIFY changed)
    Q_PROPERTY(QColor accentColor READ accentColor NOTIFY changed)
    Q_PROPERTY(QColor borderColor READ borderColor NOTIFY changed)
    Q_PROPERTY(int borderWidth READ borderWidth NOTIFY changed)
    Q_PROPERTY(int controlBorderWidth READ controlBorderWidth NOTIFY changed)
    Q_PROPERTY(bool bordersEnabled READ bordersEnabled WRITE setBordersEnabled NOTIFY changed)
    Q_PROPERTY(QString artworkSource READ artworkSource WRITE setArtworkSource NOTIFY changed)
    Q_PROPERTY(QStringList mirrorOrder READ mirrorOrder WRITE setMirrorOrder NOTIFY changed)
    Q_PROPERTY(QVariantList availableSources READ availableSources CONSTANT)
    Q_PROPERTY(QVariantList availableMirrors READ availableMirrors NOTIFY changed)
public:
    explicit AppearanceSettings(QObject *parent = nullptr);
    bool immersiveBackground() const { return m_immersive; }
    bool showBitrate() const { return m_showBitrate; }
    bool welcomeSeen() const { return m_welcomeSeen; }
    QString lastSeenRelease() const { return m_lastSeenRelease; }
    QString layoutStyle() const { return m_layoutStyle; }
    double speed() const { return m_speed; }
    double intensity() const { return m_intensity; }
    double saturation() const { return m_saturation; }
    double brightness() const { return m_brightness; }
    bool fullscreenAutoHide() const { return m_fullscreenAutoHide; }
    bool fullscreenPulse() const { return m_fullscreenPulse; }
    bool artistVideoBackdrop() const { return m_artistVideoBackdrop; }
    bool animatedArtworkEnabled() const { return m_animatedArtwork; }
    bool animatedCollageEnabled() const { return m_animatedCollage; }
    bool performanceMode() const { return m_theme == QLatin1String("rose_pine"); }
    QString theme() const { return m_theme; }
    QColor baseColor() const { return QColor(performanceMode() ? "#191724" : "#0d0f12"); }
    QColor surfaceColor() const { return QColor(performanceMode() ? "#1f1d2e" : "#660e1210"); }
    QColor textColor() const { return QColor(performanceMode() ? "#e0def4" : "#f5f3ee"); }
    QColor mutedColor() const { return QColor(performanceMode() ? "#908caa" : "#b4b8b1"); }
    QColor accentColor() const { return QColor("#c4a7e7"); }
    QColor borderColor() const { return QColor(performanceMode() ? "#ebbcba" : "#1cffffff"); }
    bool bordersEnabled() const { return m_bordersEnabled; }
    int borderWidth() const { return m_bordersEnabled ? (performanceMode() ? 2 : 1) : 0; }
    int controlBorderWidth() const { return m_bordersEnabled && !performanceMode() ? 1 : 0; }
    QString artworkSource() const { return m_artworkSource; }
    QStringList mirrorOrder() const { return m_mirrorOrder; }
    QVariantList availableSources() const;
    QVariantList availableMirrors() const;

    void setImmersiveBackground(bool value);
    void setShowBitrate(bool value);
    void setWelcomeSeen(bool value);
    void setLastSeenRelease(const QString &value);
    void setLayoutStyle(const QString &value);
    void setSpeed(double value);
    void setIntensity(double value);
    void setSaturation(double value);
    void setBrightness(double value);
    void setFullscreenAutoHide(bool value);
    void setFullscreenPulse(bool value);
    void setArtistVideoBackdrop(bool value);
    void setAnimatedArtworkEnabled(bool value);
    void setAnimatedCollageEnabled(bool value);
    void setTheme(const QString &value);
    void setBordersEnabled(bool value);
    void setArtworkSource(const QString &value);
    void setMirrorOrder(const QStringList &value);
    Q_INVOKABLE void moveMirrorUp(int index);
    Q_INVOKABLE void moveMirrorDown(int index);
    Q_INVOKABLE void resetMirrorOrder();
    Q_INVOKABLE void reset();
signals:
    void changed();
private:
    void setFlag(const QString &key, bool value, bool &field);
    void setNumber(const QString &key, double value, double &field, double low, double high);
    QStringList sanitizeMirrorOrder(const QStringList &order) const;

    QSettings m_settings;
    bool m_immersive{true};
    // Because sometimes you just need proof that those 128 kbps Opus bytes are truly exquisite.
    bool m_showBitrate{false};
    // Set once the post-login welcome has been shown, so it greets people exactly once.
    bool m_welcomeSeen{false};
    // Newest bundled release the person has opened; drives the "New" marks in release notes.
    QString m_lastSeenRelease;
    // Glade floats the player at the bottom.
    QString m_layoutStyle{QStringLiteral("glade")};
    double m_speed{2.0};
    double m_intensity{1.0};
    double m_saturation{1.5};
    // Sunglasses optional: 0 is a power outage, 2 is staring directly into the album art.
    double m_brightness{1.0};
    bool m_fullscreenAutoHide{true};
    bool m_fullscreenPulse{true};
    // Plays one of the artist's music videos, muted, behind the artist page hero.
    bool m_artistVideoBackdrop{true};
    // Moving album art: when a still image simply isn't consuming enough of your monthly data plan.
    bool m_animatedArtwork{true};
    // Four videos at once: off by default, because your GPU didn't sign up for a lava lamp.
    bool m_animatedCollage{true};
    QString m_theme{QStringLiteral("crystal")};
    bool m_bordersEnabled{true};
    QString m_artworkSource{QStringLiteral("apple_music")};
    // Mirror order: because if one mirror stumbles, the other one better catch the falling MP4.
    QStringList m_mirrorOrder{QStringLiteral("m8tec"), QStringLiteral("boidu"), QStringLiteral("spotify")};
};
