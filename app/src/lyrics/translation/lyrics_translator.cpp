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

#include "lyrics_translator.h"
#include "translation_packs.h"
#include "translation_worker.h"

#include <QCoreApplication>

#include "lyrics/lyrics_controller.h"
#include "translation/lyric_language.h"

#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QSettings>

namespace {
const QString kEnabledKey = QStringLiteral("lyrics/translate");
const QString kQualityKey = QStringLiteral("lyrics/translationQuality");
const QString kProviderKey = QStringLiteral("lyrics/translationProvider");
const QString kTargetLanguageKey = QStringLiteral("lyrics/translationTargetLanguage");
QString lineText(const QVariant &value) {
  const QVariantMap line = value.toMap();
  const QString text = line.value(QStringLiteral("text")).toString().trimmed();
  if (!text.isEmpty())
    return text;
  QStringList words;
  for (const QVariant &word : line.value(QStringLiteral("words")).toList())
    words.append(word.toMap().value(QStringLiteral("text")).toString().trimmed());
  return words.join(QLatin1Char(' ')).simplified();
}

// "Oh oh oh" comes back as "Oh oh oh"; showing it twice is noise.
bool sameText(const QString &a, const QString &b) {
  return a.simplified().compare(b.simplified(), Qt::CaseInsensitive) == 0;
}
} // namespace

LyricsTranslator::LyricsTranslator(LyricsController *lyrics, QObject *parent)
    : QObject(parent), m_lyrics(lyrics), m_network(new QNetworkAccessManager(this)),
      m_packs(new TranslationPacks(m_network, this)), m_generation(std::make_shared<std::atomic_int>(0)) {
  m_enabled = QSettings().value(kEnabledKey, false).toBool();
  if (QSettings().value(kQualityKey).toString() == QStringLiteral("high"))
    m_quality = QStringLiteral("high");
  const QString savedProvider = QSettings().value(kProviderKey).toString();
  if (QStringList{QStringLiteral("google"), QStringLiteral("local")}.contains(savedProvider))
    m_provider = savedProvider;
  else
    m_provider = QStringLiteral("google");
  const QString savedTarget = QSettings().value(kTargetLanguageKey).toString();
  if (savedTarget == QStringLiteral("english"))
    m_targetLanguage = QStringLiteral("english");
  else
    m_targetLanguage = QStringLiteral("spanish");
  m_worker = new TranslationWorker(m_generation);
  m_worker->moveToThread(&m_thread);
  connect(&m_thread, &QThread::finished, m_worker, &QObject::deleteLater);
  connect(m_worker, &TranslationWorker::translated, this, &LyricsTranslator::accept);
  connect(m_worker, &TranslationWorker::finished, this, &LyricsTranslator::jobFinished);
  m_thread.setObjectName(QStringLiteral("Lyrics translation"));
  // Lowest priority: a translated chorus can wait a frame, the game you're playing can't.
  m_thread.start(QThread::LowestPriority);

  connect(m_packs, &TranslationPacks::ready, this, &LyricsTranslator::packReady);
  connect(m_packs, &TranslationPacks::failed, this, &LyricsTranslator::packFailed);
  connect(m_packs, &TranslationPacks::progress, this, &LyricsTranslator::packProgress);
  connect(m_packs, &TranslationPacks::changed, this, &LyricsTranslator::packsChanged);

  // Lines arrive one at a time; repaint in batches instead of per line.
  m_flush.setSingleShot(true);
  m_flush.setInterval(120);
  connect(&m_flush, &QTimer::timeout, this, &LyricsTranslator::flush);

  connect(m_lyrics, &LyricsController::stateChanged, this, &LyricsTranslator::sync);
  connect(m_lyrics, &LyricsController::activeChanged, this, &LyricsTranslator::sync);
  sync();
}

LyricsTranslator::~LyricsTranslator() {
  ++*m_generation;
  cancelRemote();
  m_thread.quit();
  m_thread.wait();
}

void LyricsTranslator::setEnabled(bool enabled) {
  if (m_enabled == enabled)
    return;
  m_enabled = enabled;
  QSettings().setValue(kEnabledKey, enabled);
  emit enabledChanged();
  // Switching off mid-download means "not now", so the half-fetched model goes too.
  if (!enabled)
    m_packs->cancel();
  cancelRemote();
  m_key.clear();
  sync();
}

void LyricsTranslator::setQuality(const QString &quality) {
  const QString value = quality == QStringLiteral("high") ? quality : QStringLiteral("standard");
  if (m_quality == value)
    return;
  m_quality = value;
  QSettings().setValue(kQualityKey, value);
  emit qualityChanged();
  // A download for the other quality is no longer wanted.
  m_packs->cancel();
  cancelRemote();
  m_key.clear();
  sync();
}

void LyricsTranslator::setProvider(const QString &provider) {
  if (!QStringList{QStringLiteral("google"), QStringLiteral("local")}.contains(provider) || m_provider == provider)
    return;
  m_packs->cancel();
  cancelRemote();
  m_provider = provider;
  QSettings().setValue(kProviderKey, provider);
  emit providerChanged();
  m_key.clear();
  sync();
}

void LyricsTranslator::setTargetLanguage(const QString &language) {
  if (language != QStringLiteral("english") && language != QStringLiteral("spanish")) return;
  if (m_targetLanguage == language) return;
  ++*m_generation;
  cancelRemote();
  m_packs->cancel();
  m_targetLanguage = language;
  QSettings().setValue(kTargetLanguageKey, language);
  emit targetLanguageChanged();
  m_key.clear();
  sync();
}

QString LyricsTranslator::sourceName() const {
  if (m_source.isEmpty()) return tr("English");
  const QString name = QString::fromStdString(lyric_language::displayName(m_source.toStdString()));
  const QString translated = QCoreApplication::translate("Language", name.toUtf8().constData(), nullptr);
  return translated.isEmpty() ? name : translated;
}

QVariantList LyricsTranslator::packs() const { return m_packs->list(); }

double LyricsTranslator::installedMegabytes() const { return double(m_packs->installedBytes()) / 1e6; }

void LyricsTranslator::removePack(const QString &code) {
  if (code == m_pack) {
    ++*m_generation;
    m_lines = QStringList(m_texts.size(), QString());
    setStatus(QStringLiteral("failed"), tr("The %1 model was removed.").arg(sourceName()));
  }
  m_packs->remove(code);
}

void LyricsTranslator::retry() {
  cancelRemote();
  m_key.clear();
  sync();
}

void LyricsTranslator::setStatus(const QString &status, const QString &message) {
  m_status = status;
  m_message = message;
  emit stateChanged();
}

void LyricsTranslator::sync() {
  if (!m_enabled || m_lyrics->status() != QStringLiteral("ready")) {
    if (!m_key.isEmpty() || !m_lines.isEmpty() || m_status != (m_enabled ? QStringLiteral("idle") : QStringLiteral("off"))) {
      ++*m_generation;
      cancelRemote();
      m_key.clear();
      m_texts.clear();
      m_lines.clear();
      m_pending.clear();
      m_source.clear();
      m_pack.clear();
      setStatus(m_enabled ? QStringLiteral("idle") : QStringLiteral("off"));
    }
    return;
  }
  // Discord can keep lyrics loaded while nobody looks; only spend CPU on lines someone will read.
  if (!m_lyrics->active()) {
    if (m_provider != QStringLiteral("local") && m_reply) {
      ++*m_generation;
      cancelRemote();
      m_key.clear();
    }
    return;
  }
  QStringList texts;
  for (const QVariant &line : m_lyrics->lines())
    texts.append(lineText(line));
  const QString key = m_lyrics->trackId() + QChar(0x1f) + texts.join(QChar(0x1e));
  if (key == m_key)
    return;
  m_key = key;
  m_texts = texts;
  start();
}

void LyricsTranslator::start() {
  ++*m_generation;
  cancelRemote();
  m_flush.stop();
  m_pending.clear();
  m_progress = 0;
  m_lines = QStringList(m_texts.size(), QString());
  std::vector<std::string> utf8;
  utf8.reserve(m_texts.size());
  for (const QString &text : std::as_const(m_texts))
    utf8.push_back(text.toStdString());
  const lyric_language::SongPlan plan = lyric_language::plan(utf8);
  m_source = QString::fromStdString(plan.source);
  m_pack.clear();
  if ((m_targetLanguage == QStringLiteral("english") && m_source.isEmpty()) ||
      (m_targetLanguage == QStringLiteral("spanish") && (m_source == QStringLiteral("spa") || m_source == QStringLiteral("es")))) {
    setStatus(QStringLiteral("ready"), tr("Lyrics are up to date"));
    return;
  }
  const bool useGoogle = m_provider == QStringLiteral("google");
  const QString packSource = m_source.isEmpty() && m_targetLanguage == QStringLiteral("spanish")
                                 ? QStringLiteral("eng") : m_source;
  const TranslationPack *pack = (!useGoogle && m_provider == QStringLiteral("local"))
                                    ? TranslationPacks::resolve(packSource, m_targetLanguage, m_quality)
                                    : nullptr;
  if (pack)
    m_pack = QLatin1String(pack->id);
  if (!useGoogle && m_provider == QStringLiteral("local") && (!pack || !m_packs->available(m_pack))) {
    setStatus(QStringLiteral("unsupported"), tr("%1 lyrics can't be translated yet.").arg(sourceName()));
    return;
  }

  QStringList wanted;
  for (qsizetype i = 0; i < m_texts.size(); ++i) {
    if (!useGoogle && m_provider == QStringLiteral("local") &&
        m_targetLanguage == QStringLiteral("english") && !plan.translate[size_t(i)])
      continue;
    if (m_texts.at(i).isEmpty())
      continue;
    if (!m_pending.contains(m_texts.at(i)))
      wanted.append(m_texts.at(i));
    m_pending[m_texts.at(i)].append(int(i));
  }
  const QString revision = cacheRevision();
  const QHash<QString, QString> cached = m_store.lookup(revision, wanted);
  for (auto it = cached.cbegin(); it != cached.cend(); ++it) {
    for (const int index : m_pending.take(it.key()))
      m_lines[index] = sameText(it.key(), it.value()) ? QString() : it.value();
  }
  if (m_pending.isEmpty()) {
    setStatus(QStringLiteral("ready"), tr("Translated from %1").arg(sourceName()));
    return;
  }
  if (useGoogle) {
    for (const QString &text : std::as_const(m_texts))
      if (m_pending.contains(text) && !m_remoteQueue.contains(text)) m_remoteQueue.append(text);
    runGoogleRemote();
    return;
  }
  if (m_packs->installed(m_pack)) {
    runModel();
    return;
  }
  setStatus(QStringLiteral("downloading"), tr("Downloading the %1 model").arg(sourceName()));
  m_packs->ensure(m_pack);
}

void LyricsTranslator::runModel() {
  QStringList texts;
  // Song order, so the opening lines land first.
  for (const QString &text : std::as_const(m_texts))
    if (m_pending.contains(text) && !texts.contains(text))
      texts.append(text);
  const int generation = m_generation->load();
  const QString directory = m_packs->directory(m_pack);
  setStatus(QStringLiteral("translating"), tr("Translating from %1").arg(sourceName()));
  QMetaObject::invokeMethod(m_worker, [worker = m_worker, generation, directory, texts] {
    worker->translate(generation, directory, texts);
  });
}

void LyricsTranslator::accept(int generation, const QString &text, const QString &translation) {
  if (generation != m_generation->load())
    return;
  if (const auto *pack = TranslationPacks::find(m_pack))
    m_store.store(QLatin1String(pack->revision), text, translation);
  for (const int index : m_pending.take(text))
    m_lines[index] = sameText(text, translation) ? QString() : translation;
  if (!m_flush.isActive())
    m_flush.start();
}

void LyricsTranslator::flush() { emit stateChanged(); }

void LyricsTranslator::jobFinished(int generation, const QString &error) {
  if (generation != m_generation->load())
    return;
  m_flush.stop();
  m_pending.clear();
  if (!error.isEmpty())
    setStatus(QStringLiteral("failed"), error);
  else
    setStatus(QStringLiteral("ready"), tr("Translated from %1").arg(sourceName()));
}

void LyricsTranslator::packReady(const QString &id) {
  emit packsChanged();
  if (id == m_pack && m_status == QStringLiteral("downloading"))
    runModel();
}

void LyricsTranslator::packFailed(const QString &id, const QString &message) {
  emit packsChanged();
  if (id == m_pack && m_status == QStringLiteral("downloading"))
    setStatus(QStringLiteral("failed"), message);
}

void LyricsTranslator::packProgress(const QString &id, double fraction) {
  if (id != m_pack || m_status != QStringLiteral("downloading"))
    return;
  // Whole percents only; a 20 MB download would otherwise repaint the lyrics thousands of times.
  if (int(fraction * 100) == int(m_progress * 100))
    return;
  m_progress = fraction;
  emit stateChanged();
}
