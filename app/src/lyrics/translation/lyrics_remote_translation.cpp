/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
#include "lyrics_translator.h"
#include "google_translation.h"
#include "translation_packs.h"

#include <QJsonArray>
#include <QJsonDocument>
#include <QNetworkAccessManager>
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QUrlQuery>
#include <QDateTime>
#include <algorithm>

namespace {
constexpr int kBatchSize = 1;

bool sameText(const QString &a, const QString &b) {
  return a.simplified().compare(b.simplified(), Qt::CaseInsensitive) == 0;
}
}

QString LyricsTranslator::cacheRevision() const {
  if (m_provider == QStringLiteral("local") || m_googleFallback) {
    QStringList revisions;
    for (const auto &id : m_localRoute) {
      const auto *pack = TranslationPacks::find(id);
      if (!pack) return {};
      revisions.append(QLatin1String(pack->revision));
    }
    if (revisions.size() == 1) return revisions.first();
    return revisions.isEmpty() ? QString() : QStringLiteral("pivot-v1:") + revisions.join(QLatin1Char('|'));
  }
  return QStringLiteral("google-v2-") + m_targetLanguage;
}

void LyricsTranslator::cancelRemote() {
  m_remoteQueue.clear();
  m_remoteBatch.clear();
  if (m_reply) {
    disconnect(m_reply, nullptr, this, nullptr);
    m_reply->abort();
    m_reply->deleteLater();
    m_reply = nullptr;
  }
}

void LyricsTranslator::runGoogleRemote() {
  if (QDateTime::currentMSecsSinceEpoch() < m_googleRetryAtMs) {
    useLocalFallback();
    return;
  }
  if (m_remoteQueue.isEmpty()) {
    setStatus(QStringLiteral("ready"), tr("Translated from %1").arg(sourceName()));
    return;
  }
  m_remoteBatch = m_remoteQueue.mid(0, kBatchSize);
  m_remoteQueue = m_remoteQueue.mid(m_remoteBatch.size());

  QUrl url(QStringLiteral("https://translate.googleapis.com/translate_a/single"));
  QUrlQuery query;
  query.addQueryItem(QStringLiteral("client"), QStringLiteral("gtx"));
  query.addQueryItem(QStringLiteral("sl"), QStringLiteral("auto"));
  query.addQueryItem(QStringLiteral("tl"), m_targetLanguage == QStringLiteral("spanish")
                                              ? QStringLiteral("es") : QStringLiteral("en"));
  query.addQueryItem(QStringLiteral("dt"), QStringLiteral("t"));
  query.addQueryItem(QStringLiteral("q"), m_remoteBatch.join(QLatin1Char('\n')));

  QNetworkRequest request(url);
  request.setHeader(QNetworkRequest::ContentTypeHeader,
                    QStringLiteral("application/x-www-form-urlencoded; charset=UTF-8"));
  request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::ManualRedirectPolicy);
  request.setTransferTimeout(20000);

  const int generation = m_generation->load();
  auto *reply = m_network->post(request, query.query(QUrl::FullyEncoded).toUtf8());
  m_reply = reply;
  setStatus(QStringLiteral("translating"), tr("Translating with Google Translate"));

  connect(reply, &QNetworkReply::finished, this, [this, reply, generation] {
    if (reply != m_reply || generation != m_generation->load()) {
      reply->deleteLater();
      return;
    }
    m_reply = nullptr;
    const QByteArray body = reply->readAll();
    const int http = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
    const QNetworkReply::NetworkError error = reply->error();
    const QByteArray retryAfter = reply->rawHeader("Retry-After");
    reply->deleteLater();
    if (error != QNetworkReply::NoError || http >= 300) {
      const qint64 now = QDateTime::currentMSecsSinceEpoch();
      m_googleRetryAtMs = now + googleTranslationRetryDelay(http, retryAfter, now);
      useLocalFallback();
      return;
    }

    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isArray() || doc.array().isEmpty() || !doc.array().at(0).isArray()) {
      m_googleRetryAtMs = QDateTime::currentMSecsSinceEpoch() + 600'000;
      useLocalFallback();
      return;
    }
    const QJsonArray segments = doc.array().at(0).toArray();
    const QString translatedLine = googleTranslatedLine(segments);
    if (translatedLine.isEmpty()) {
      m_googleRetryAtMs = QDateTime::currentMSecsSinceEpoch() + 600'000;
      useLocalFallback();
      return;
    }

    const QString revision = cacheRevision();
    for (qsizetype i = 0; i < m_remoteBatch.size(); ++i) {
      const QString &text = m_remoteBatch.at(i);
      const QString translated = translatedLine;
      m_store.store(revision, text, translated);
      for (const int index : m_pending.take(text))
        m_lines[index] = sameText(text, translated) ? QString() : translated;
    }
    m_remoteBatch.clear();
    emit stateChanged();
    if (generation == m_generation->load()) runGoogleRemote();
  });
}

void LyricsTranslator::useLocalFallback() {
  m_remoteQueue.clear();
  m_remoteBatch.clear();
  m_googleFallback = true;
  const QString source = m_source.isEmpty() ? QStringLiteral("eng") : m_source;
  m_localRoute = localRoute(source);
  if (m_localRoute.isEmpty() || std::any_of(m_localRoute.cbegin(), m_localRoute.cend(),
      [this](const QString &id) { return !m_packs->available(id); })) {
    setStatus(QStringLiteral("failed"), tr("Google is unavailable and no local model supports this language pair."));
    return;
  }
  m_pack = m_localRoute.first();
  if (m_targetLanguage == QStringLiteral("english")) {
    for (const auto &text : m_pending.keys())
      if (!m_localSourceTexts.contains(text)) m_pending.remove(text);
  }
  const auto cached = m_store.lookup(cacheRevision(), m_pending.keys());
  for (auto it = cached.cbegin(); it != cached.cend(); ++it)
    for (const int index : m_pending.take(it.key()))
      m_lines[index] = sameText(it.key(), it.value()) ? QString() : it.value();
  if (m_pending.isEmpty()) {
    setStatus(QStringLiteral("ready"), tr("Translated locally from %1 (Google unavailable)").arg(sourceName()));
  } else {
    prepareLocal();
  }
}
