/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
#pragma once
#include <QImage>
#include <QRectF>

namespace cloud {
inline QImage cropCover(QImage image, const QRectF &crop) {
    if (image.isNull()) return {};
    if (crop.isValid()) {
        const auto area = crop.intersected(QRectF(0, 0, 1, 1));
        if (area.isEmpty()) return {};
        image = image.copy(QRect(qRound(area.x() * image.width()), qRound(area.y() * image.height()),
                                qMax(1, qRound(area.width() * image.width())), qMax(1, qRound(area.height() * image.height()))));
    }
    const int side = qMin(image.width(), image.height());
    return image.copy((image.width() - side) / 2, (image.height() - side) / 2, side, side)
                .scaled(1024, 1024, Qt::KeepAspectRatio, Qt::SmoothTransformation);
}
}
