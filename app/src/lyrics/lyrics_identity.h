/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */
#pragma once

#include <QCryptographicHash>
#include <QDateTime>
#include <QJsonDocument>
#include <QJsonObject>
#include <QVariantMap>

namespace lyrics {
inline constexpr auto policyVersion = "recording-timing-v3";

inline QJsonObject lookupTrack(const QVariantMap &track, double measuredSeconds = 0) {
  const QString source = track.value(QStringLiteral("sourceVideoId")).toString();
  double seconds = measuredSeconds > 0 ? measuredSeconds : track.value(QStringLiteral("sourceDurationSeconds")).toDouble();
  if (seconds <= 0) seconds = track.value(QStringLiteral("durationSeconds")).toDouble();
  QJsonObject result{{QStringLiteral("id"), source.isEmpty() ? track.value(QStringLiteral("id")).toString() : source},
    {QStringLiteral("title"), track.value(QStringLiteral("title")).toString()},
    {QStringLiteral("artist"), track.value(QStringLiteral("artist")).toString()},
    {QStringLiteral("album"), track.value(QStringLiteral("album")).toString()}};
  if (seconds > 0) result.insert(QStringLiteral("durationSeconds"), qRound64(seconds * 1000) / 1000.0);
  else result.insert(QStringLiteral("duration"), track.value(QStringLiteral("duration")).toString());
  return result;
}

inline QString identity(const QJsonObject &track) {
  return QString::fromLatin1(QCryptographicHash::hash(QJsonDocument(track).toJson(QJsonDocument::Compact), QCryptographicHash::Sha256).toHex());
}

inline QJsonObject validated(QJsonObject result, const QString &key) {
  result.insert(QStringLiteral("timingIdentity"), key);
  result.insert(QStringLiteral("validatedAt"), QDateTime::currentMSecsSinceEpoch());
  return result;
}

inline bool current(const QJsonObject &result, const QString &key) {
  const qint64 age = QDateTime::currentMSecsSinceEpoch() - result.value(QStringLiteral("validatedAt")).toVariant().toLongLong();
  return result.value(QStringLiteral("policyVersion")).toString() == QLatin1String(policyVersion)
    && result.value(QStringLiteral("timingIdentity")).toString() == key
    && age >= 0 && age < 7LL * 24 * 60 * 60 * 1000;
}
}
