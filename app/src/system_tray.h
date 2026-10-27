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

class PlaybackController;
class AuthManager;
class QAction;
class QMenu;
class QSystemTrayIcon;
class QWindow;
class TrayHeaderWidget;
class AppearanceSettings;

class SystemTray final : public QObject
{
    Q_OBJECT
    Q_PROPERTY(bool available READ available CONSTANT)
    Q_PROPERTY(bool closeToTray READ closeToTray WRITE setCloseToTray NOTIFY closeToTrayChanged)

public:
    explicit SystemTray(PlaybackController *playback, AuthManager *auth, QObject *parent = nullptr);
    ~SystemTray() override;

    [[nodiscard]] bool available() const;
    [[nodiscard]] bool closeToTray() const { return m_closeToTray; }
    void setCloseToTray(bool value);
    void setWindow(QWindow *window);
    void setAppearance(AppearanceSettings *appearance);

    Q_INVOKABLE void showWindow();
    Q_INVOKABLE void toggleWindow();
    Q_INVOKABLE void retranslate();

signals:
    void closeToTrayChanged();

private:
    void refresh();

    PlaybackController *m_playback = nullptr;
    AuthManager *m_auth = nullptr;
    QPointer<QWindow> m_window;
    QSettings m_settings;
    bool m_closeToTray{false};
    QSystemTrayIcon *m_icon = nullptr;
    QMenu *m_menu = nullptr;
    TrayHeaderWidget *m_headerWidget = nullptr;
    QAction *m_nowPlaying = nullptr;
    QAction *m_trackSeparator = nullptr;
    QAction *m_toggle = nullptr;
    QAction *m_previous = nullptr;
    QAction *m_next = nullptr;
    QAction *m_showHide = nullptr;
    QAction *m_quit = nullptr;
    QString m_toolTip;
};
