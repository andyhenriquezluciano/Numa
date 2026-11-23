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
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR
 * A PARTICULAR PURPOSE. See the GNU Affero General Public License for more
 * details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * along with Numa. If not, see <https://www.gnu.org/licenses/>.
 */

#include "artwork_palette.h"
#include "appearance/artwork_sampler.h"

#include <QCache>
#include <QFile>
#include <QFutureWatcher>
#include <QJsonObject>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QtConcurrent/QtConcurrentRun>

namespace {

// Shared across instances so skipping back to a track recolors instantly.
QCache<QString, QVariantMap> &paletteCache() {
  static QCache<QString, QVariantMap> cache(64);
  return cache;
}

} // namespace

ArtworkPalette::ArtworkPalette(QObject *parent) : QObject(parent) {
  m_loadDelay.setSingleShot(true);
  m_loadDelay.setInterval(120);
  connect(&m_loadDelay, &QTimer::timeout, this, &ArtworkPalette::loadRemote);
}

ArtworkPalette::~ArtworkPalette() { abortReply(); }

void ArtworkPalette::setSource(const QUrl &source) {
  if (source == m_source)
    return;
  m_source = source;
  emit sourceChanged();

  abortReply();
  const quint64 generation = ++m_generation;
  m_loadDelay.stop();
  if (source.isEmpty()) {
    publish({}, false);
    return;
  }

  const QString key = source.toString();
  if (const auto *cached = paletteCache().object(key)) {
    publish(*cached, true);
    return;
  }

  // Keep the old samples for backdrop transitions, but mark them as belonging
  // to the previous cover until this source is decoded.
  publish(m_palette, false);
  if (source.isLocalFile()) {
    sample([path = source.toLocalFile()] {
      QFile file(path);
      return file.open(QIODevice::ReadOnly) ? file.readAll() : QByteArray{};
    }, generation);
    return;
  }

  m_loadDelay.start();
}

void ArtworkPalette::loadRemote() {
  const QUrl source = m_source;
  const quint64 generation = m_generation;
  if (source.isEmpty() || source.isLocalFile())
    return;
  QNetworkRequest request(source);
  request.setRawHeader(
      "Accept", "image/avif,image/webp,image/png,image/jpeg,image/*;q=0.8");
  request.setAttribute(QNetworkRequest::RedirectPolicyAttribute,
                       QNetworkRequest::NoLessSafeRedirectPolicy);
  QNetworkReply *reply = m_network.get(request);
  m_reply = reply;
  connect(reply, &QNetworkReply::finished, this, [this, reply, generation] {
    reply->deleteLater();
    if (m_reply != reply || generation != m_generation)
      return;
    m_reply = nullptr;
    if (reply->error() != QNetworkReply::NoError) {
      publish({}, false);
      return;
    }
    sample([bytes = reply->readAll()] { return bytes; }, generation);
  });
}

void ArtworkPalette::sample(std::function<QByteArray()> readArtwork, quint64 generation) {
  const QString key = m_source.toString();
  auto *watcher = new QFutureWatcher<QVariantMap>(this);
  connect(watcher, &QFutureWatcher<QVariantMap>::finished, this,
          [this, watcher, generation, key] {
            const QVariantMap palette = watcher->result();
            watcher->deleteLater();
            if (palette.isEmpty()) {
              if (generation == m_generation)
                publish({}, false);
              return;
            }
            paletteCache().insert(key, new QVariantMap(palette));
            if (generation == m_generation)
              publish(palette, true);
          });
  watcher->setFuture(
      QtConcurrent::run([readArtwork = std::move(readArtwork)]() -> QVariantMap {
        const QByteArray bytes = readArtwork();
        if (bytes.isEmpty())
          return {};
        return sampleArtworkPalette(bytes).toVariantMap();
      }));
}

void ArtworkPalette::publish(const QVariantMap &palette, bool ready) {
  if (palette == m_palette && ready == m_ready)
    return;
  m_palette = palette;
  m_ready = ready;
  emit paletteChanged();
}

void ArtworkPalette::abortReply() {
  m_loadDelay.stop();
  QNetworkReply *reply = m_reply;
  m_reply = nullptr;
  if (reply)
    reply->abort();
}
