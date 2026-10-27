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

#include "playback/letterbox.h"
#include <QPainter>
#include <QTest>

namespace {

// A 1920x1080 frame of black with a mid-grey picture inside `content` (pixels).
QImage frame(const QRect &content) {
  QImage image(1920, 1080, QImage::Format_RGB32);
  image.fill(Qt::black);
  QPainter(&image).fillRect(content, QColor(120, 110, 100));
  return image;
}

bool near(double a, double b) { return qAbs(a - b) < 0.02; }

} // namespace

class LetterboxTest : public QObject {
  Q_OBJECT

private slots:
  void findsBakedInLetterbox() {
    // 2.39:1 picture inside a 16:9 frame.
    const QRectF rect = letterbox::contentRect(frame(QRect(0, 138, 1920, 804)));
    QVERIFY(near(rect.top(), 138.0 / 1080));
    QVERIFY(near(rect.height(), 804.0 / 1080));
    QVERIFY(near(rect.width(), 1.0));
  }

  void findsPillarbox() {
    // 4:3 picture inside a 16:9 frame.
    const QRectF rect = letterbox::contentRect(frame(QRect(240, 0, 1440, 1080)));
    QVERIFY(near(rect.left(), 240.0 / 1920));
    QVERIFY(near(rect.width(), 1440.0 / 1920));
    QVERIFY(near(rect.height(), 1.0));
  }

  void ignoresDarkScenes() {
    QVERIFY(letterbox::contentRect(frame(QRect())).isNull());
    // A lamp in a dark room is not a picture boundary.
    QVERIFY(letterbox::contentRect(frame(QRect(800, 450, 300, 200))).isNull());
  }

  void preservesDarkPictureOutsideTheBrightSubject() {
    QImage image(1920, 1080, QImage::Format_RGB32);
    image.fill(QColor(22, 22, 22));
    QPainter(&image).fillRect(QRect(300, 150, 1320, 780), QColor(120, 110, 100));
    QCOMPARE(letterbox::contentRect(image), QRectF(0, 0, 1, 1));
  }

  void keepsTexturedNearBlackEdges() {
    QImage image(1920, 1080, QImage::Format_RGB32);
    QPainter painter(&image);
    for (int y = 0; y < image.height(); ++y)
      for (int x = 0; x < image.width(); x += 20) {
        const int shade = (x / 20 + y / 20) % 2 ? 8 : 24;
        painter.fillRect(QRect(x, y, 20, 1), QColor(shade, shade, shade));
      }
    painter.end();
    QCOMPARE(letterbox::contentRect(image), QRectF(0, 0, 1, 1));
  }

  void acceptsUniformLimitedRangeBlackBars() {
    QImage image(1920, 1080, QImage::Format_RGB32);
    image.fill(QColor(16, 16, 16));
    QPainter(&image).fillRect(QRect(240, 0, 1440, 1080), QColor(120, 110, 100));
    const QRectF rect = letterbox::contentRect(image);
    QVERIFY(near(rect.left(), 0.125));
    QVERIFY(near(rect.width(), 0.75));
  }

  void accumulatesAndSnapsToEdges() {
    QRectF rect = letterbox::accumulate({}, QRectF(0.01, 0.13, 0.98, 0.70));
    QCOMPARE(rect.left(), 0.0);
    QCOMPARE(rect.right(), 1.0);
    QVERIFY(near(rect.top(), 0.13));
    rect = letterbox::accumulate(rect, QRectF(0.2, 0.12, 0.5, 0.76));
    QVERIFY(near(rect.top(), 0.12));
    QVERIFY(near(rect.bottom(), 0.88));
    // A dark sample leaves what was seen untouched.
    QCOMPARE(letterbox::accumulate(rect, QRectF()), rect);
  }
};

QTEST_MAIN(LetterboxTest)
#include "letterbox_test.moc"
