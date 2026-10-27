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

#include "window_state.h"

#include <QEvent>
#include <QGuiApplication>
#include <QIcon>
#include <QScreen>
#include <QWindow>
#include <QWindowStateChangeEvent>

#ifdef Q_OS_WIN
#include <dwmapi.h>
#include <windows.h>
#endif

namespace {
constexpr int kDefaultWidth = 1360;
constexpr int kDefaultHeight = 820;
// Keeps the background animating while alt-tab previews the window.
constexpr int kUnfocusedGraceMs = 2500;

void configureNativeFrame(QWindow *window, bool compact, bool videoFullScreen = false)
{
#ifdef Q_OS_WIN
    if (!window)
        return;
    const HWND handle = reinterpret_cast<HWND>(window->winId());
    static const HICON smallIcon = static_cast<HICON>(LoadImageW(GetModuleHandleW(nullptr),
        MAKEINTRESOURCEW(1), IMAGE_ICON, GetSystemMetrics(SM_CXSMICON), GetSystemMetrics(SM_CYSMICON), LR_SHARED));
    static const HICON largeIcon = static_cast<HICON>(LoadImageW(GetModuleHandleW(nullptr),
        MAKEINTRESOURCEW(1), IMAGE_ICON, GetSystemMetrics(SM_CXICON), GetSystemMetrics(SM_CYICON), LR_SHARED));
    if (smallIcon && reinterpret_cast<HICON>(SendMessageW(handle, WM_GETICON, ICON_SMALL, 0)) != smallIcon)
        SendMessageW(handle, WM_SETICON, ICON_SMALL, reinterpret_cast<LPARAM>(smallIcon));
    if (largeIcon && reinterpret_cast<HICON>(SendMessageW(handle, WM_GETICON, ICON_BIG, 0)) != largeIcon)
        SendMessageW(handle, WM_SETICON, ICON_BIG, reinterpret_cast<LPARAM>(largeIcon));
    const bool fillsScreen = videoFullScreen
        || window->visibility() == QWindow::Maximized
        || window->visibility() == QWindow::FullScreen;
    const int cornerPreference = fillsScreen ? 1 : 2;
    const DWORD borderColor = 0xFFFFFFFE;
    DwmSetWindowAttribute(handle, 33, &cornerPreference, sizeof(cornerPreference));
    DwmSetWindowAttribute(handle, 34, &borderColor, sizeof(borderColor));

    const MARGINS margins = {-1, -1, -1, -1};
    DwmExtendFrameIntoClientArea(handle, &margins);
    SetWindowRgn(handle, nullptr, TRUE);
    Q_UNUSED(compact)
#else
    Q_UNUSED(window)
    Q_UNUSED(compact)
    Q_UNUSED(videoFullScreen)
#endif
}
}

WindowState::WindowState(QObject *parent) : QObject(parent)
{
    m_enabled = m_settings.value(QStringLiteral("window/remember"), true).toBool();
    if (m_enabled) {
        m_width = qMax(m_settings.value(QStringLiteral("window/width"), kDefaultWidth).toInt(), 1);
        m_height = qMax(m_settings.value(QStringLiteral("window/height"), kDefaultHeight).toInt(), 1);
        m_maximized = m_settings.value(QStringLiteral("window/maximized"), false).toBool();
    }
    // Coalesce drag-resize bursts into one write.
    m_saveTimer.setSingleShot(true);
    m_saveTimer.setInterval(400);
    connect(&m_saveTimer, &QTimer::timeout, this, &WindowState::save);

    m_unfocusedTimer.setSingleShot(true);
    m_unfocusedTimer.setInterval(kUnfocusedGraceMs);
    connect(&m_unfocusedTimer, &QTimer::timeout, this, [this] { setUnfocused(true); });
    connect(qGuiApp, &QGuiApplication::applicationStateChanged, this, &WindowState::updateState);
    connect(&m_occlusion, &WindowOcclusion::occludedChanged, this, &WindowState::updateState);

    m_geometryAnimation.setDuration(0);
    m_geometryAnimation.setEasingCurve(QEasingCurve::InOutCubic);
    connect(&m_geometryAnimation, &QVariantAnimation::valueChanged, this, [this](const QVariant &value) {
        if (!m_window)
            return;
        const QRect rect = value.toRect();
#ifdef Q_OS_WIN
        const HWND handle = reinterpret_cast<HWND>(m_window->winId());
        SetWindowPos(handle, nullptr, rect.x(), rect.y(), rect.width(), rect.height(),
                     SWP_NOZORDER | SWP_NOACTIVATE);
#else
        m_window->setGeometry(rect);
#endif
    });
    connect(&m_geometryAnimation, &QVariantAnimation::finished, this, [this] {
        if (m_maximizeAfterAnimation && m_window)
            m_window->showMaximized();
        m_maximizeAfterAnimation = false;
        emit compactTransitionFinished();
    });
}

void WindowState::setEnabled(bool value)
{
    if (value == m_enabled)
        return;
    m_enabled = value;
    m_settings.setValue(QStringLiteral("window/remember"), value);
    if (value)
        save();
    emit enabledChanged();
}

void WindowState::setWindow(QWindow *window)
{
    m_window = window;
    if (!window)
        return;
    window->setIcon(QGuiApplication::windowIcon());
    m_previousVisibility = window->visibility();
    const auto scheduleSave = [this] {
        m_saveTimer.start();
        applyFrame();
    };
    connect(window, &QWindow::widthChanged, this, scheduleSave);
    connect(window, &QWindow::heightChanged, this, scheduleSave);
    connect(window, &QWindow::visibilityChanged, this, [this](QWindow::Visibility visibility) {
        if (!m_videoFullScreen) {
            m_saveTimer.start();
            applyFrame();
        }

        if (visibility == QWindow::Minimized || visibility == QWindow::Hidden) {
            m_wasMaximizedBeforeHide = m_wasMaximizedBeforeHide
                || m_previousVisibility == QWindow::Maximized;
            m_wasFullScreenBeforeMinimize = m_wasFullScreenBeforeMinimize
                || m_videoFullScreen || m_previousVisibility == QWindow::FullScreen;
            return;
        }
        if (m_wasFullScreenBeforeMinimize) {
            m_wasFullScreenBeforeMinimize = false;
            if (visibility != QWindow::FullScreen) {
                QTimer::singleShot(0, m_window, [this] {
                    if (m_window && m_window->isVisible()
                        && m_window->visibility() != QWindow::Minimized)
                        m_window->showFullScreen();
                });
            }
        }
        if (m_wasMaximizedBeforeHide) {
            m_wasMaximizedBeforeHide = false;
            if (!m_videoFullScreen && visibility == QWindow::Windowed) {
                QTimer::singleShot(0, m_window, [this] {
                    if (m_window && m_window->visibility() == QWindow::Windowed && !m_videoFullScreen)
                        m_window->showMaximized();
                });
            }
        }
        m_previousVisibility = visibility;
    });
    connect(window, &QWindow::visibilityChanged, this, &WindowState::updateState);
    connect(window, &QWindow::visibleChanged, this, &WindowState::updateState);
    window->installEventFilter(this);
    m_occlusion.setWindow(window);
    applyFrame();
    updateState();
}

void WindowState::setCompactFrame(bool enabled)
{
    m_compactFrame = enabled;
    applyFrame();
}

void WindowState::setVideoFullScreen(bool enabled, int restoreVisibility)
{
    if (!m_window || enabled == m_videoFullScreen)
        return;
    if (enabled) {
        const auto requested = static_cast<QWindow::Visibility>(restoreVisibility);
        m_videoRestoreVisibility = requested == QWindow::Maximized || requested == QWindow::FullScreen
            || requested == QWindow::Windowed ? requested : m_window->visibility();
        m_videoFullScreen = true;
        emit videoFullScreenChanged();
        m_window->showFullScreen();
    } else {
        m_videoFullScreen = false;
        m_wasFullScreenBeforeMinimize = false;
        emit videoFullScreenChanged();
        if (m_videoRestoreVisibility == QWindow::Maximized)
            m_window->showMaximized();
        else if (m_videoRestoreVisibility == QWindow::FullScreen)
            m_window->showFullScreen();
        else
            m_window->showNormal();
    }
    applyFrame();
}

void WindowState::animateGeometry(const QRect &start, const QRect &end, bool maximize)
{
    m_geometryAnimation.stop();
    m_maximizeAfterAnimation = maximize;
    m_geometryAnimation.setStartValue(start);
    m_geometryAnimation.setEndValue(end);
    m_geometryAnimation.start();
}

void WindowState::enterCompactMode(int width, int height)
{
    if (!m_window)
        return;
#ifdef Q_OS_WIN
    const HWND handle = reinterpret_cast<HWND>(m_window->winId());
    RECT nativeStart{};
    MONITORINFO info{sizeof(info)};
    const HMONITOR monitor = MonitorFromWindow(handle, MONITOR_DEFAULTTONEAREST);
    if (!GetWindowRect(handle, &nativeStart) || !GetMonitorInfoW(monitor, &info))
        return;
    const QRect start(nativeStart.left, nativeStart.top,
                      nativeStart.right - nativeStart.left, nativeStart.bottom - nativeStart.top);
    const qreal scaleX = m_window->width() > 0 ? qreal(start.width()) / m_window->width() : 1.0;
    const qreal scaleY = m_window->height() > 0 ? qreal(start.height()) / m_window->height() : 1.0;
    const int targetWidth = qRound(width * scaleX);
    const int targetHeight = qRound(height * scaleY);
    const int x = info.rcWork.left + (info.rcWork.right - info.rcWork.left - targetWidth) / 2;
    const int y = info.rcWork.top + (info.rcWork.bottom - info.rcWork.top - targetHeight) / 2;
    ShowWindow(handle, SW_RESTORE);
    SetWindowPos(handle, nullptr, start.x(), start.y(), start.width(), start.height(),
                 SWP_NOZORDER | SWP_NOACTIVATE);
    animateGeometry(start, QRect(x, y, targetWidth, targetHeight), false);
#else
    m_window->showNormal();
    m_window->resize(width, height);
    const QRect area = m_window->screen()->availableGeometry();
    m_window->setPosition(area.center() - QPoint(width / 2, height / 2));
    emit compactTransitionFinished();
#endif
}

void WindowState::exitCompactMode()
{
    if (!m_window)
        return;
#ifdef Q_OS_WIN
    const HWND handle = reinterpret_cast<HWND>(m_window->winId());
    RECT nativeStart{};
    MONITORINFO info{sizeof(info)};
    const HMONITOR monitor = MonitorFromWindow(handle, MONITOR_DEFAULTTONEAREST);
    if (!GetWindowRect(handle, &nativeStart) || !GetMonitorInfoW(monitor, &info)) {
        m_window->showMaximized();
        return;
    }
    const QRect start(nativeStart.left, nativeStart.top,
                      nativeStart.right - nativeStart.left, nativeStart.bottom - nativeStart.top);
    const QRect end(info.rcWork.left, info.rcWork.top,
                    info.rcWork.right - info.rcWork.left, info.rcWork.bottom - info.rcWork.top);
    animateGeometry(start, end, true);
#else
    m_window->showMaximized();
    emit compactTransitionFinished();
#endif
}

void WindowState::applyFrame()
{
    if (!m_window)
        return;
    configureNativeFrame(m_window, m_compactFrame, m_videoFullScreen);
}

bool WindowState::eventFilter(QObject *watched, QEvent *event)
{
    if (watched == m_window && event->type() == QEvent::WinIdChange)
        applyFrame();
    if (watched == m_window && event->type() == QEvent::Expose)
        updateState();
    return QObject::eventFilter(watched, event);
}

void WindowState::updateState()
{
    if (!m_window)
        return;
    const bool minimized = m_window->visibility() == QWindow::Minimized
        || m_window->visibility() == QWindow::Hidden;
    setIdle(minimized || !m_window->isVisible() || !m_window->isExposed()
        || m_occlusion.occluded());

    if (qGuiApp->applicationState() == Qt::ApplicationActive) {
        m_unfocusedTimer.stop();
        setUnfocused(false);
    } else if (!m_unfocused && !m_unfocusedTimer.isActive()) {
        m_unfocusedTimer.start();
    }
    syncBackgroundIdle();
}

void WindowState::setIdle(bool value)
{
    if (value == m_idle)
        return;
    m_idle = value;
    emit idleChanged();
    syncBackgroundIdle();
}

void WindowState::setUnfocused(bool value)
{
    if (value == m_unfocused)
        return;
    m_unfocused = value;
    emit unfocusedChanged();
    syncBackgroundIdle();
}

void WindowState::syncBackgroundIdle()
{
    const bool fullscreen = m_window && m_window->visibility() == QWindow::FullScreen;
    const bool value = m_idle || (m_unfocused && !fullscreen);
    if (value == m_backgroundIdle)
        return;
    m_backgroundIdle = value;
    emit backgroundIdleChanged();
}

void WindowState::save()
{
    if (!m_enabled || !m_window || m_videoFullScreen)
        return;
    if (m_window->visibility() == QWindow::Minimized || m_window->visibility() == QWindow::Hidden)
        return;
    const bool maximized = m_window->visibility() == QWindow::Maximized;
    m_settings.setValue(QStringLiteral("window/maximized"), maximized);
    // Keep the last normal size while maximized or fullscreen so restore returns to it.
    if (m_window->visibility() == QWindow::Windowed) {
        m_settings.setValue(QStringLiteral("window/width"), m_window->width());
        m_settings.setValue(QStringLiteral("window/height"), m_window->height());
    }
}
