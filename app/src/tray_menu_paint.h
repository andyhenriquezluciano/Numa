#pragma once

#include <QAction>
#include <QMenu>
#include <QPainter>
#include <QPainterPath>
#include <QWidgetAction>

inline void paintTrayMenu(QMenu *menu)
{
    const bool rose = menu->property("rosePine").toBool();
    const QColor text = rose ? QColor("#e0def4") : QColor("#f2f0eb");
    QPainter painter(menu);
    painter.setCompositionMode(QPainter::CompositionMode_Source);
    painter.fillRect(menu->rect(), Qt::transparent);
    painter.setCompositionMode(QPainter::CompositionMode_SourceOver);
    painter.setRenderHint(QPainter::Antialiasing);
    QPainterPath surface;
    const qreal inset = rose ? 1.0 : 0.5;
    surface.addRoundedRect(QRectF(menu->rect()).adjusted(inset, inset, -inset, -inset), 11.5, 11.5);
    painter.fillPath(surface, rose ? QColor("#1f1d2e") : QColor(14, 18, 16, menu->property("nativeBlur").toBool() ? 100 : 235));
    painter.setPen(menu->property("bordersEnabled").toBool() ? QPen(rose ? QColor("#ebbcba") : QColor(255, 255, 255, 42), rose ? 2 : 1) : QPen(Qt::NoPen));
    painter.drawPath(surface);
    painter.setClipPath(surface);
    QFont font = menu->font();
    font.setPixelSize(13);
    painter.setFont(font);
    for (QAction *action : menu->actions()) {
        if (!action->isVisible() || qobject_cast<QWidgetAction *>(action))
            continue;
        const QRect row = menu->actionGeometry(action);
        if (action->isSeparator()) {
            painter.setPen(rose ? QColor("#403d52") : QColor(255, 255, 255, 20));
            painter.drawLine(14, row.center().y(), menu->width() - 14, row.center().y());
            continue;
        }
        if (action == menu->activeAction() && action->isEnabled()) {
            painter.setPen(Qt::NoPen);
            painter.setBrush(rose ? QColor("#403d52") : QColor(255, 255, 255, 24));
            painter.drawRoundedRect(QRectF(8, row.y(), menu->width() - 16, row.height()), 7, 7);
        }
        const QRect iconRect(18, row.center().y() - 8, 16, 16);
        action->icon().paint(&painter, iconRect, Qt::AlignCenter,
                             action->isEnabled() ? QIcon::Normal : QIcon::Disabled);
        painter.setPen(action->isEnabled() ? text : rose ? QColor("#908caa") : QColor("#858991"));
        const QRect textRect(46, row.y(), menu->width() - 58, row.height());
        painter.drawText(textRect, Qt::AlignVCenter | Qt::AlignLeft,
                         painter.fontMetrics().elidedText(action->text(), Qt::ElideRight, textRect.width()));
    }
}
