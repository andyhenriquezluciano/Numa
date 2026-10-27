/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
#pragma once
#include <QCryptographicHash>
#include <QDir>
#include <QFile>
#include <QFileInfo>
#include <QSaveFile>

namespace cloud {
inline bool copyVerifiedCover(const QString &source, const QString &target, const QString &digest) {
    if (digest.size() != 64) return false;
    for (const auto c : digest) if (!QStringLiteral("0123456789abcdef").contains(c)) return false;
    QFile input(source);
    if (input.size() <= 0 || input.size() > 1048576 || !input.open(QIODevice::ReadOnly)) return false;
    const auto bytes = input.readAll();
    if (QString::fromLatin1(QCryptographicHash::hash(bytes, QCryptographicHash::Sha256).toHex()) != digest) return false;
    if (QFileInfo(source).absoluteFilePath() == QFileInfo(target).absoluteFilePath()) return true;
    if (!QDir().mkpath(QFileInfo(target).absolutePath())) return false;
    QSaveFile output(target);
    return output.open(QIODevice::WriteOnly) && output.write(bytes) == bytes.size() && output.commit();
}
}
