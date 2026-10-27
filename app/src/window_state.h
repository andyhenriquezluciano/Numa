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
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A
 * PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

#pragma once

#include <QObject>
#include <QPointer>
#include <QSettings>
#include <QTimer>
#include <QVariantAnimation>
#include <QWindow>

#include "window_occlusion.h"

// Remembers the main window size and maximized state between launches.
class WindowState final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool enabled READ enabled WRITE setEnabled NOTIFY enabledChanged)
    Q_PROPERTY(int initialWidth READ initialWidth CONSTANT)
    Q_PROPERTY(int initialHeight READ initialHeight CONSTANT)
    Q_PROPERTY(bool initialMaximized READ initialMaximized CONSTANT)
    Q_PROPERTY(bool idle READ idle NOTIFY idleChanged)
    Q_PROPERTY(bool unfocused READ unfocused NOTIFY unfocusedChanged)
    Q_PROPERTY(bool backgroundIdle READ backgroundIdle NOTIFY backgroundIdleChanged)
    Q_PROPERTY(bool videoFullScreen READ videoFullScreen NOTIFY videoFullScreenChanged)

public:
    bool videoFullScreen() const { return m_videoFullScreen; }
    explicit WindowState(QObject *parent = nullptr);

    [[nodiscard]] bool enabled() const { return m_enabled; }
    void setEnabled(bool value);
    [[nodiscard]] int initialWidth() const { return m_width; }
    [[nodiscard]] int initialHeight() const { return m_height; }
    [[nodiscard]] bool initialMaximized() const { return m_maximized; }
    void setWindow(QWindow *window);
    Q_INVOKABLE void setCompactFrame(bool enabled);
    Q_INVOKABLE void enterCompactMode(int width, int height);
    Q_INVOKABLE void exitCompactMode();
    Q_INVOKABLE void setVideoFullScreen(bool enabled, int restoreVisibility = -1);
    // True when the window is hidden, minimized, or reported as covered by the platform.
    [[nodiscard]] bool idle() const { return m_idle; }
    // True once the app has stayed unfocused past a short grace.
    [[nodiscard]] bool unfocused() const { return m_unfocused; }
    // Idle, or unfocused outside fullscreen. Drives the immersive background frame rate.
    [[nodiscard]] bool backgroundIdle() const { return m_backgroundIdle; }

signals:
    void videoFullScreenChanged();
    void compactTransitionFinished();
    void enabledChanged();
    void idleChanged();
    void unfocusedChanged();
    void backgroundIdleChanged();

protected:
    bool eventFilter(QObject *watched, QEvent *event) override;

private:
    void save();
    void updateState();
    void setIdle(bool value);
    void setUnfocused(bool value);
    void syncBackgroundIdle();
    void applyFrame();
    void animateGeometry(const QRect &start, const QRect &end, bool maximize);

    QSettings m_settings;
    QPointer<QWindow> m_window;
    QTimer m_saveTimer;
    QTimer m_unfocusedTimer;
    QVariantAnimation m_geometryAnimation;
    WindowOcclusion m_occlusion;
    bool m_maximizeAfterAnimation{false};
    bool m_videoFullScreen{false};
    QWindow::Visibility m_videoRestoreVisibility{QWindow::Maximized};
    bool m_idle{false};
    bool m_unfocused{false};
    bool m_backgroundIdle{false};
    bool m_enabled{true};
    bool m_compactFrame{false};
    bool m_wasFullScreenBeforeMinimize{false};
    bool m_wasMaximizedBeforeHide{false};
    QWindow::Visibility m_previousVisibility{QWindow::Windowed};
    int m_width{1360};
    int m_height{820};
    bool m_maximized{false};
};
