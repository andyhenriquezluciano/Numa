/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */
#pragma once

#include <QJsonArray>
#include <QByteArray>
#include <QDateTime>
#include <QLocale>
#include <QString>
#include <QTimeZone>
#include <limits>

inline qint64 googleTranslationRetryDelay(int http, const QByteArray &retryAfter, qint64 nowMs) {
  bool numeric = false;
  const qint64 seconds = retryAfter.trimmed().toLongLong(&numeric);
  if (numeric && seconds >= 0 && seconds <= std::numeric_limits<qint64>::max() / 1000)
    return qMin(std::numeric_limits<qint64>::max() - qMax(qint64(0), nowMs),
                qMax(qint64(1000), seconds * 1000));
  const auto header = QString::fromLatin1(retryAfter).trimmed();
  auto date = QDateTime::fromString(header, Qt::RFC2822Date);
  if (!date.isValid()) {
    date = QLocale::c().toDateTime(header, QStringLiteral("ddd, dd MMM yyyy HH:mm:ss 'GMT'"));
    date.setTimeZone(QTimeZone(QTimeZone::UTC));
  }
  if (date.isValid() && date.toMSecsSinceEpoch() > nowMs)
    return date.toMSecsSinceEpoch() - nowMs;
  return http >= 300 ? 600'000 : 30'000;
}

inline QString googleTranslatedLine(const QJsonArray &segments) {
  QString result;
  for (const auto &value : segments) {
    const auto segment = value.toArray();
    if (!segment.isEmpty() && segment.at(0).isString()) result += segment.at(0).toString();
  }
  return result.trimmed();
}
