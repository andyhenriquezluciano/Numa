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

namespace {
constexpr int kBatchSize = 1;

bool sameText(const QString &a, const QString &b) {
  return a.simplified().compare(b.simplified(), Qt::CaseInsensitive) == 0;
}
}

QString LyricsTranslator::cacheRevision() const {
  if (m_provider == QStringLiteral("local")) {
    if (const auto *pack = TranslationPacks::find(m_pack)) return QLatin1String(pack->revision);
    return {};
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
    reply->deleteLater();
    if (error != QNetworkReply::NoError || http >= 300) {
      setStatus(QStringLiteral("failed"), tr("Translation request failed (%1). Try again.")
                    .arg(http > 0 ? QString::number(http) : tr("network error")));
      return;
    }

    const QJsonDocument doc = QJsonDocument::fromJson(body);
    if (!doc.isArray() || doc.array().isEmpty() || !doc.array().at(0).isArray()) {
      setStatus(QStringLiteral("failed"), tr("Translation request failed."));
      return;
    }
    const QJsonArray segments = doc.array().at(0).toArray();
    const QString translatedLine = googleTranslatedLine(segments);
    if (translatedLine.isEmpty()) {
      setStatus(QStringLiteral("failed"), tr("Translation request failed."));
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
