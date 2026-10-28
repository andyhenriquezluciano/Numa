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

#include "translation_worker.h"
#include "translation/litert_api.h"
#include "translation/marian_model.h"

#include <QCoreApplication>
#include <QDir>
#include <QFile>
#include <QTimer>

namespace {
// Two threads keep a song under a few seconds without taking cores from games or playback.
constexpr int kThreads = 2;
// About 75 MB resident while loaded; songs in a row reuse it, idle gives it back.
constexpr int kUnloadAfterMs = 90 * 1000;
} // namespace

QString TranslationWorker::liteRtLibraryPath() {
  const QString override = qEnvironmentVariable("NUMA_LITERT_LIBRARY");
  if (!override.isEmpty())
    return override;
#if defined(Q_OS_WIN)
  const QString name = QStringLiteral("libLiteRt.dll");
#elif defined(Q_OS_MACOS)
  const QString name = QStringLiteral("libLiteRt.dylib");
#else
  const QString name = QStringLiteral("libLiteRt.so");
#endif
  return QDir(QCoreApplication::applicationDirPath()).filePath(name);
}

TranslationWorker::TranslationWorker(std::shared_ptr<std::atomic_int> current) : m_current(std::move(current)) {}

TranslationWorker::~TranslationWorker() = default;

void TranslationWorker::unload() {
  m_model.reset();
  m_loadedDirectory.clear();
}

void TranslationWorker::translate(int generation, const QString &packDirectory, const QStringList &texts) {
  translateRoute(generation, {packDirectory}, texts, texts);
}

void TranslationWorker::translateRoute(int generation, const QStringList &directories,
                                      const QStringList &texts, const QStringList &sourceLanguageTexts) {
  const auto cancelled = [this, generation] { return m_current->load(std::memory_order_relaxed) != generation; };
  if (cancelled()) return;
  if (directories.isEmpty()) {
    emit finished(generation, tr("No local translation route is available."));
    return;
  }
  if (!m_idle) {
    m_idle = new QTimer(this);
    m_idle->setSingleShot(true);
    m_idle->setInterval(kUnloadAfterMs);
    connect(m_idle, &QTimer::timeout, this, &TranslationWorker::unload);
  }
  m_idle->stop();
  QStringList values = texts;
  bool incomplete = false;
  for (qsizetype stage = 0; stage < directories.size(); ++stage) {
    if (cancelled()) { m_idle->start(); return; }
    const auto &directory = directories.at(stage);
    if (!m_model || m_loadedDirectory != directory) {
      unload();
      std::string error;
      const LiteRtApi *api = LiteRtApi::get(QFile::encodeName(liteRtLibraryPath()).toStdString(), &error);
      auto model = std::make_unique<MarianModel>();
      if (!api || !model->load(*api, QFile::encodeName(directory).toStdString(), kThreads, &error)) {
        emit finished(generation, QString::fromStdString(error));
        return;
      }
      m_model = std::move(model);
      m_loadedDirectory = directory;
    }
    for (qsizetype i = 0; i < texts.size(); ++i) {
      if (cancelled()) { m_idle->start(); return; }
      // Already-English lines bypass the first leg of a source -> English -> Spanish route.
      if (stage == 0 && directories.size() > 1 && !sourceLanguageTexts.contains(texts.at(i))) continue;
      if (values.at(i).isEmpty()) continue;
      const auto out = QString::fromStdString(m_model->translate(values.at(i).toStdString(), cancelled));
      if (out.isEmpty() && !cancelled()) incomplete = true;
      values[i] = out;
      if (stage == directories.size() - 1 && !out.isEmpty()) emit translated(generation, texts.at(i), out);
    }
  }
  m_idle->start();
  emit finished(generation, incomplete ? tr("Some lyric lines could not be translated locally.") : QString());
}
