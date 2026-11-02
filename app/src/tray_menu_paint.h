#pragma once

#include <QAction>
#include <QMenu>
#include <QPainter>
#include <QPainterPath>
#include <QWidgetAction>

inline void paintTrayMenu(QMenu *menu)
{
    const bool rose = menu->property("rosePine").toBool();
    const bool dawn = menu->property("dawn").toBool();
    const QColor text = dawn ? QColor("#575279") : rose ? QColor("#e0def4") : QColor("#f2f0eb");
    QPainter painter(menu);
    painter.setCompositionMode(QPainter::CompositionMode_Source);
    painter.fillRect(menu->rect(), Qt::transparent);
    painter.setCompositionMode(QPainter::CompositionMode_SourceOver);
    painter.setRenderHint(QPainter::Antialiasing);
    QPainterPath surface;
    const bool nativeBlur = !rose && menu->property("nativeBlur").toBool();
    const qreal inset = 1.5;
    if (nativeBlur)
        surface.addRect(QRectF(menu->rect()));
    else
        surface.addRoundedRect(QRectF(menu->rect()).adjusted(inset, inset, -inset, -inset), 12.5, 12.5);
    painter.fillPath(surface, dawn ? QColor("#fffaf3") : rose ? QColor("#1f1d2e") : QColor(14, 18, 16, nativeBlur ? 28 : 135));
    painter.setPen(menu->property("bordersEnabled").toBool() ? QPen(dawn ? QColor("#d7827e") : rose ? QColor("#ebbcba") : QColor(255, 255, 255, 42), rose ? 1.25 : 1.0) : QPen(Qt::NoPen));
    if (!nativeBlur)
        painter.drawRoundedRect(QRectF(menu->rect()).adjusted(inset, inset, -inset, -inset), 12.5, 12.5);
    painter.setClipPath(surface);
    QFont font = menu->font();
    font.setPixelSize(13);
    painter.setFont(font);
    for (QAction *action : menu->actions()) {
        if (!action->isVisible() || qobject_cast<QWidgetAction *>(action))
            continue;
        const QRect row = menu->actionGeometry(action);
        if (action->isSeparator()) {
            painter.setPen(dawn ? QColor("#efd4cf") : rose ? QColor("#403d52") : QColor(255, 255, 255, 20));
            painter.drawLine(14, row.center().y(), menu->width() - 14, row.center().y());
            continue;
        }
        if (action == menu->activeAction() && action->isEnabled()) {
            painter.setPen(Qt::NoPen);
            painter.setBrush(dawn ? QColor("#efd4cf") : rose ? QColor("#403d52") : QColor(255, 255, 255, 24));
            painter.drawRoundedRect(QRectF(8, row.y(), menu->width() - 16, row.height()), 7, 7);
        }
        const QRect iconRect(18, row.center().y() - 8, 16, 16);
        QPixmap glyph = action->icon().pixmap(16, 16);
        if (!glyph.isNull()) {
            QPainter tint(&glyph);
            tint.setCompositionMode(QPainter::CompositionMode_SourceIn);
            tint.fillRect(glyph.rect(), text);
            tint.end();
            painter.drawPixmap(iconRect, glyph);
        }
        painter.setPen(action->isEnabled() ? text : rose ? QColor("#908caa") : QColor("#858991"));
        const QRect textRect(46, row.y(), menu->width() - 58, row.height());
        painter.drawText(textRect, Qt::AlignVCenter | Qt::AlignLeft,
                         painter.fontMetrics().elidedText(action->text(), Qt::ElideRight, textRect.width()));
    }
}
