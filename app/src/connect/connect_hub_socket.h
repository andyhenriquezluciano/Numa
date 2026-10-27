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
#include <QTimer>
#include <QWebSocket>

class NumaAccount;

// The account service's Connect hub (services/account /connect/hub). Opens with
// the Numa access token, keeps it fresh, and reconnects with backoff.
class ConnectHubSocket final : public QObject {
  Q_OBJECT

public:
  explicit ConnectHubSocket(NumaAccount *account, QObject *parent = nullptr,
                            QString path = QStringLiteral("/connect/hub"), QString protocol = QStringLiteral("numa-connect.2"));

  void start();
  void stop();
  void send(const QString &text);
  [[nodiscard]] bool isOpen() const { return m_open; }

signals:
  void opened();
  void message(const QString &text);
  void closed();

private:
  void open();
  void scheduleRetry();
  void refreshToken();

  QPointer<NumaAccount> m_account;
  QWebSocket m_socket;
  QTimer m_retry;
  QTimer m_refresh;
  QString m_token; // last token the hub saw
  QString m_path, m_protocol;
  int m_attempt{0};
  bool m_wanted{false};
  bool m_open{false};
  // Bumped per open() so a slow token callback cannot revive a stopped socket.
  quint64 m_generation{0};
};
