/* Copyright (C) 2026 SFG545. SPDX-License-Identifier: AGPL-3.0-or-later */
#pragma once

#include <QJsonArray>
#include <QString>

inline QString googleTranslatedLine(const QJsonArray &segments) {
  QString result;
  for (const auto &value : segments) {
    const auto segment = value.toArray();
    if (!segment.isEmpty() && segment.at(0).isString()) result += segment.at(0).toString();
  }
  return result.trimmed();
}
