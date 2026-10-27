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

#include "system_tray.h"
#include "tray_menu_paint.h"
#include "appearance/appearance_settings.h"

#include "auth/auth_manager.h"
#include "playback/playback_controller.h"

#include <QAction>
#include <QApplication>
#include <QCursor>
#include <QFontMetrics>
#include <QIcon>
#include <QLabel>
#include <QMenu>
#include <QMouseEvent>
#include <QPainter>
#include <QPainterPath>
#include <QResizeEvent>
#include <QScreen>
#include <QSystemTrayIcon>
#include <QVBoxLayout>
#include <QWidgetAction>
#include <QWindow>
#include <functional>

#ifdef Q_OS_WIN
#include <dwmapi.h>
#include <windows.h>
#endif

namespace {
#ifdef Q_OS_WIN
bool applyWindowEffects(HWND hwnd, bool rose)
{
    if (!hwnd)
        return false;

    const BOOL darkMode = TRUE;
    DwmSetWindowAttribute(hwnd, 20 /* DWMWA_USE_IMMERSIVE_DARK_MODE */, &darkMode, sizeof(darkMode));
    DwmSetWindowAttribute(hwnd, 19, &darkMode, sizeof(darkMode));

    const DWORD corner = 1; // DWMWCP_DONOTROUND
    DwmSetWindowAttribute(hwnd, 33 /* DWMWA_WINDOW_CORNER_PREFERENCE */, &corner, sizeof(corner));

    const DWORD noBorder = 0xFFFFFFFE; // DWMWA_COLOR_NONE
    DwmSetWindowAttribute(hwnd, 34 /* DWMWA_BORDER_COLOR */, &noBorder, sizeof(noBorder));

    const MARGINS margins = {-1, -1, -1, -1};
    DwmExtendFrameIntoClientArea(hwnd, &margins);
    const DWORD backdrop = rose ? 1 : 3;
    return SUCCEEDED(DwmSetWindowAttribute(hwnd, 38 /* DWMWA_SYSTEMBACKDROP_TYPE */, &backdrop, sizeof(backdrop))) && !rose;
}
#endif

const QString trayMenuStyle = QStringLiteral(
    "QMenu {"
    "    background-color: transparent;"
    "    border: none;"
    "    border-radius: 12px;"
    "    padding: 6px 10px;"
    "}"
    "QMenu::item {"
    "    font-family: 'Inter', -apple-system, sans-serif;"
    "    font-size: 13px;"
    "    font-weight: 500;"
    "    color: #f2f0eb;"
    "    background-color: transparent;"
    "    padding: 7px 6px 7px 14px;"
    "    border-radius: 8px;"
    "    margin: 1px 4px;"
    "}"
    "QMenu::item:selected {"
    "    background-color: rgba(255, 255, 255, 0.12);"
    "    color: #ffffff;"
    "}"
    "QMenu::item:disabled {"
    "    color: #858991;"
    "    background-color: transparent;"
    "}"
    "QMenu::separator {"
    "    height: 1px;"
    "    background: rgba(255, 255, 255, 0.08);"
    "    margin: 4px 8px;"
    "}"
    "QMenu::indicator {"
    "    width: 0px;"
    "    height: 0px;"
    "}"
);
}

class TrayMenu final : public QMenu
{
public:
    explicit TrayMenu(std::function<void()> onLanguageChanged, std::function<QRect()> anchor, QWidget *parent = nullptr)
        : QMenu(parent), m_onLanguageChanged(std::move(onLanguageChanged)), m_anchor(std::move(anchor))
    {
        setAttribute(Qt::WA_TranslucentBackground);
        setAttribute(Qt::WA_NoSystemBackground);
        setAutoFillBackground(false);
        setWindowFlags(Qt::Popup | Qt::FramelessWindowHint | Qt::NoDropShadowWindowHint);
        setFixedWidth(200);

#ifdef Q_OS_WIN
        createWinId();
        setProperty("nativeBlur", applyWindowEffects(reinterpret_cast<HWND>(winId()), property("rosePine").toBool()));
#endif
    }

    void setVisible(bool visible) override
    {
        QMenu::setVisible(visible);
        if (visible)
            adjustTrayPosition();
    }

protected:
    void paintEvent(QPaintEvent *event) override
    {
        Q_UNUSED(event)
        paintTrayMenu(this);
    }

    void resizeEvent(QResizeEvent *event) override
    {
        QMenu::resizeEvent(event);
        clearMask();
    }

    void changeEvent(QEvent *event) override
    {
        if (event->type() == QEvent::LanguageChange && m_onLanguageChanged)
            m_onLanguageChanged();
        QMenu::changeEvent(event);
    }

    void showEvent(QShowEvent *event) override
    {
        adjustTrayPosition();
        QMenu::showEvent(event);
#ifdef Q_OS_WIN
        setProperty("nativeBlur", applyWindowEffects(reinterpret_cast<HWND>(winId()), property("rosePine").toBool()));
#endif
    }

private:
    std::function<void()> m_onLanguageChanged;
    std::function<QRect()> m_anchor;
    void adjustTrayPosition()
    {
        const QRect anchor = m_anchor ? m_anchor() : QRect();
        const QPoint point = anchor.isValid() ? anchor.center() : QCursor::pos();
        QScreen *screen = QGuiApplication::screenAt(point);
        if (!screen)
            screen = QGuiApplication::primaryScreen();
        if (!screen)
            return;

        const QRect work = screen->availableGeometry();
        const QSize sz = sizeHint();
        const int gap = 4;
        QPoint target(point.x() - sz.width() / 2, anchor.isValid() ? anchor.top() - sz.height() - gap : point.y() - sz.height());
        if (anchor.isValid() && anchor.bottom() <= work.top()) target.setY(anchor.bottom() + gap);
        else if (anchor.isValid() && anchor.right() <= work.left()) target = QPoint(anchor.right() + gap, point.y() - sz.height() / 2);
        else if (anchor.isValid() && anchor.left() >= work.right()) target = QPoint(anchor.left() - sz.width() - gap, point.y() - sz.height() / 2);
        target.setX(qBound(work.left(), target.x(), qMax(work.left(), work.right() - sz.width() + 1)));
        target.setY(qBound(work.top(), target.y(), qMax(work.top(), work.bottom() - sz.height() + 1)));
        move(target);
    }
};

class TrayHeaderWidget final : public QWidget
{
public:
    explicit TrayHeaderWidget(std::function<void()> onActivated, QWidget *parent = nullptr)
        : QWidget(parent), m_onActivated(std::move(onActivated))
    {
        setCursor(Qt::PointingHandCursor);

        auto *layout = new QVBoxLayout(this);
        layout->setContentsMargins(14, 9, 14, 9);
        layout->setSpacing(3);

        m_titleLabel = new QLabel(this);
        m_titleLabel->setStyleSheet(QStringLiteral(
            "color: #f2f0eb; font-size: 14px; font-weight: 600; "
            "font-family: 'Inter', -apple-system, sans-serif; background: transparent;"));
        m_titleLabel->setAttribute(Qt::WA_TransparentForMouseEvents);

        m_artistLabel = new QLabel(this);
        m_artistLabel->setStyleSheet(QStringLiteral(
            "color: #b8bbc3; font-size: 12px; font-weight: 500; "
            "font-family: 'Inter', sans-serif; background: transparent;"));
        m_artistLabel->setAttribute(Qt::WA_TransparentForMouseEvents);

        layout->addWidget(m_titleLabel);
        layout->addWidget(m_artistLabel);
    }

    void setTheme(bool rose)
    {
        setProperty("rosePine", rose);
        m_titleLabel->setStyleSheet(QStringLiteral("color: %1; font-size: 14px; font-weight: 600; font-family: 'Inter'; background: transparent;")
            .arg(rose ? "#e0def4" : "#f2f0eb"));
        m_artistLabel->setStyleSheet(QStringLiteral("color: %1; font-size: 12px; font-weight: 500; font-family: 'Inter'; background: transparent;")
            .arg(rose ? "#908caa" : "#b8bbc3"));
    }

    void setTrack(const QString &title, const QString &artist)
    {
        m_title = title;
        m_artist = artist;
        updateText();
    }

protected:
    bool event(QEvent *event) override
    {
        if (event->type() == QEvent::Enter) {
            m_hovered = true;
            update();
        } else if (event->type() == QEvent::Leave) {
            m_hovered = false;
            update();
        }
        return QWidget::event(event);
    }

    void resizeEvent(QResizeEvent *event) override
    {
        QWidget::resizeEvent(event);
        updateText();
    }

    void mouseReleaseEvent(QMouseEvent *event) override
    {
        QWidget::mouseReleaseEvent(event);
        if (event->button() == Qt::LeftButton && m_onActivated)
            m_onActivated();
    }

    void paintEvent(QPaintEvent *event) override
    {
        QWidget::paintEvent(event);
        if (m_hovered) {
            QPainter p(this);
            p.setRenderHint(QPainter::Antialiasing);
            p.setPen(Qt::NoPen);
            p.setBrush(property("rosePine").toBool() ? QColor("#403d52") : QColor(255, 255, 255, 24));
            p.drawRoundedRect(rect().adjusted(4, 2, -4, -2), 6, 6);
        }
    }

private:
    void updateText()
    {
        const int availWidth = width() > 40 ? width() - 28 : 200;
        QFontMetrics fmTitle(m_titleLabel->font());
        m_titleLabel->setText(fmTitle.elidedText(m_title, Qt::ElideRight, availWidth));

        if (!m_artist.isEmpty()) {
            QFontMetrics fmArtist(m_artistLabel->font());
            m_artistLabel->setText(fmArtist.elidedText(m_artist, Qt::ElideRight, availWidth));
            m_artistLabel->setVisible(true);
        } else {
            m_artistLabel->setVisible(false);
        }

        const QString tip = m_artist.isEmpty() ? m_title : m_title + QStringLiteral(" · ") + m_artist;
        setToolTip(tip);
    }

    QLabel *m_titleLabel = nullptr;
    QLabel *m_artistLabel = nullptr;
    QString m_title;
    QString m_artist;
    bool m_hovered = false;
    std::function<void()> m_onActivated;
};

SystemTray::SystemTray(PlaybackController *playback, AuthManager *auth, QObject *parent)
    : QObject(parent), m_playback(playback), m_auth(auth)
{
    const QVariant stranded = m_settings.value(QStringLiteral("General/closeToTray"));
    m_settings.beginGroup(QStringLiteral("tray"));
    m_closeToTray = m_settings.value(QStringLiteral("closeToTray"), stranded.isValid() ? stranded : true).toBool();

    if (!QSystemTrayIcon::isSystemTrayAvailable())
        return;

    // Quit is always explicit, so a hidden main window never ends the session.
    QApplication::setQuitOnLastWindowClosed(false);

    m_menu = new TrayMenu([this] {
        retranslate();
    }, [this] { return m_icon ? m_icon->geometry() : QRect(); });
    m_menu->setStyleSheet(trayMenuStyle);

    m_headerWidget = new TrayHeaderWidget([this] {
        m_menu->close();
        showWindow();
    }, m_menu);

    auto *headerAction = new QWidgetAction(m_menu);
    headerAction->setDefaultWidget(m_headerWidget);
    m_nowPlaying = headerAction;
    m_menu->addAction(headerAction);

    m_trackSeparator = m_menu->addSeparator();

    m_toggle = m_menu->addAction(tr("Play"), m_playback, &PlaybackController::toggle);
    m_previous = m_menu->addAction(tr("Previous"), m_playback, &PlaybackController::previous);
    m_next = m_menu->addAction(tr("Next"), m_playback, &PlaybackController::next);
    m_menu->addSeparator();
    m_showHide = m_menu->addAction(tr("Show NÜMA"), this, &SystemTray::toggleWindow);
    m_quit = m_menu->addAction(tr("Quit NÜMA"), qApp, &QCoreApplication::quit);
    const auto icon = [](const char *name) {
        return QIcon(QStringLiteral(":/qt/qml/Numa/app/qml/assets/lucide/") + QString::fromLatin1(name) + QStringLiteral(".svg"));
    };
    m_previous->setIcon(icon("skip-back"));
    m_next->setIcon(icon("skip-forward"));
    m_showHide->setIcon(icon("app-window"));
    m_quit->setIcon(icon("x"));
    m_quit->setIconVisibleInMenu(true);

    m_icon = new QSystemTrayIcon(
        QIcon(QStringLiteral(":/qt/qml/Numa/app/qml/assets/numa.ico")), this);
    m_icon->setContextMenu(m_menu);
    connect(m_icon, &QSystemTrayIcon::activated, this, [this](QSystemTrayIcon::ActivationReason reason) {
        if (reason == QSystemTrayIcon::Trigger)
            toggleWindow();
    });
    connect(m_playback, &PlaybackController::stateChanged, this, &SystemTray::refresh);
    connect(m_auth, &AuthManager::statusChanged, this, &SystemTray::refresh);

    refresh();
    m_icon->show();
}

void SystemTray::setAppearance(AppearanceSettings *appearance)
{
    if (!m_menu || !appearance) return;
    const auto update = [this, appearance] {
        const bool rose = appearance->performanceMode();
        m_menu->setProperty("rosePine", rose);
        m_menu->setProperty("bordersEnabled", appearance->bordersEnabled());
        m_headerWidget->setTheme(rose);
        m_menu->update();
    };
    connect(appearance, &AppearanceSettings::changed, this, update);
    update();
}

SystemTray::~SystemTray()
{
    delete m_menu;
}

bool SystemTray::available() const
{
    return m_icon != nullptr;
}

void SystemTray::setCloseToTray(bool value)
{
    if (value == m_closeToTray)
        return;
    m_closeToTray = value;
    m_settings.setValue(QStringLiteral("closeToTray"), value);
    emit closeToTrayChanged();
    refresh();
}

void SystemTray::setWindow(QWindow *window)
{
    if (m_window)
        disconnect(m_window, nullptr, this, nullptr);
    m_window = window;
    if (m_window)
        connect(m_window, &QWindow::visibleChanged, this, &SystemTray::refresh);
    refresh();
}

void SystemTray::showWindow()
{
    if (!m_window)
        return;
    m_window->setWindowStates(m_window->windowStates() & ~Qt::WindowMinimized);
    m_window->show();
    m_window->raise();
    m_window->requestActivate();
}

void SystemTray::toggleWindow()
{
    if (!m_window)
        return;
    if (m_closeToTray && m_auth->isSignedIn() && m_window->isVisible()
        && !(m_window->windowStates() & Qt::WindowMinimized))
        m_window->hide();
    else
        showWindow();
}

void SystemTray::refresh()
{
    if (!m_icon)
        return;

    // stateChanged fires on every position tick; QAction setters no-op on equal values.
    const QVariantMap track = m_playback->shownTrack();
    const QString title = track.value(QStringLiteral("title")).toString();
    const QString artist = track.value(QStringLiteral("artist")).toString();
    const bool hasTrack = !title.isEmpty();
    const QString summary = artist.isEmpty() ? title : title + QStringLiteral(" · ") + artist;

    if (m_headerWidget)
        m_headerWidget->setTrack(title, artist);
    if (m_nowPlaying)
        m_nowPlaying->setVisible(hasTrack);
    if (m_trackSeparator)
        m_trackSeparator->setVisible(hasTrack);

    m_toggle->setText(m_playback->shownPlaying() ? tr("Pause") : tr("Play"));
    static const QIcon playIcon(QStringLiteral(":/qt/qml/Numa/app/qml/assets/lucide/play.svg"));
    static const QIcon pauseIcon(QStringLiteral(":/qt/qml/Numa/app/qml/assets/lucide/pause.svg"));
    m_toggle->setIcon(m_playback->shownPlaying() ? pauseIcon : playIcon);
    m_toggle->setEnabled(hasTrack);
    m_previous->setEnabled(m_playback->shownCanGoPrevious());
    m_next->setEnabled(m_playback->shownCanGoNext());
    const bool canHide = m_closeToTray && m_auth->isSignedIn();
    m_showHide->setText(canHide && m_window && m_window->isVisible()
                            ? tr("Hide NÜMA") : tr("Show NÜMA"));

    const QString toolTip = hasTrack ? summary : QStringLiteral("NÜMA");
    if (toolTip != m_toolTip) {
        m_toolTip = toolTip;
        m_icon->setToolTip(toolTip);
    }
}

void SystemTray::retranslate()
{
    if (m_previous)
        m_previous->setText(tr("Previous"));
    if (m_next)
        m_next->setText(tr("Next"));
    if (m_quit)
        m_quit->setText(tr("Quit NÜMA"));
    refresh();
}
