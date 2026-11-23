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

#include "search_controller.h"

#include "auth/auth_manager.h"
#include "offline/offline_library.h"
#include "providers/youtube/catalog/youtube_catalog.h"

#include <QStringList>
#include <utility>

SearchController::SearchController(YouTubeCatalog *catalog, AuthManager *auth,
                                   QObject *parent)
    : QObject(parent), m_catalog(catalog), m_auth(auth) {
  Q_ASSERT(m_catalog);
  Q_ASSERT(m_auth);

  connect(m_catalog, &YouTubeCatalog::searchReady, this,
          &SearchController::receiveSearch);
  connect(m_catalog, &YouTubeCatalog::requestFailed, this,
          &SearchController::receiveFailure);
  connect(m_auth, &AuthManager::sessionChanged, this, [this] {
    m_recent.clear();
    clear();
  });
}

QString SearchController::normalizedFilter(const QString &filter) {
  const QString candidate = filter.trimmed().toLower();
  return QStringList{QStringLiteral("all"), QStringLiteral("songs"),
                     QStringLiteral("videos"), QStringLiteral("albums"),
                     QStringLiteral("artists"), QStringLiteral("playlists")}
      .contains(candidate)
      ? candidate
      : QStringLiteral("all");
}

void SearchController::search(const QString &query, const QString &filter) {
  m_requestId = 0;
  m_query = query.simplified();
  m_filter = normalizedFilter(filter);
  m_errorMessage.clear();
  m_loading = false;

  if (m_query.isEmpty()) {
    setSections({});
    emit stateChanged();
    return;
  }
  if (m_offline && m_offline->offline()) {
    setSections(m_offline->search(m_query, m_filter));
    emit stateChanged();
    return;
  }
  if (!m_auth->isSignedIn()) {
    setSections({});
    m_errorMessage = tr("Sign in to search YouTube Music.");
    emit stateChanged();
    return;
  }

  if (const auto *cached = m_recent.object(cacheKey()); cached && !cached->expires.hasExpired()) {
    setSections(cached->sections);
    emit stateChanged();
    return;
  }

  // Search has left the pantry; invalidate every older result before it can
  // wander back into the UI.
  m_loading = true;
  m_requestId = m_catalog->fetchSearch(m_query, m_filter,
                                       m_auth->sessionObject());
  emit stateChanged();
}

void SearchController::retry() {
  if (!m_query.isEmpty()) {
    m_recent.remove(cacheKey());
    search(m_query, m_filter);
  }
}

void SearchController::prepareSearch() {
  m_requestId = 0;
  m_errorMessage.clear();
  m_loading = true;
  emit stateChanged();
}

QString SearchController::cacheKey() const {
  return m_filter + QLatin1Char('\n') + m_query;
}

void SearchController::setSections(QVariantList sections) {
  if (m_sections == sections)
    return;
  m_sections = std::move(sections);
  emit sectionsChanged();
}

void SearchController::clear() {
  m_requestId = 0;
  setSections({});
  m_errorMessage.clear();
  m_query.clear();
  m_filter = QStringLiteral("all");
  m_loading = false;
  emit stateChanged();
}

void SearchController::receiveSearch(quint64 requestId,
                                     const QJsonObject &search) {
  if (!m_requestId || requestId != m_requestId)
    return;

  m_requestId = 0;
  m_loading = false;
  m_errorMessage.clear();
  setSections(search.value(QStringLiteral("sections")).toArray().toVariantList());
  m_recent.insert(cacheKey(), new CachedResult{m_sections, QDeadlineTimer(120'000)});
  emit stateChanged();
}

void SearchController::receiveFailure(quint64 requestId,
                                      const QString &message) {
  if (!m_requestId || requestId != m_requestId)
    return;

  m_requestId = 0;
  m_loading = false;
  m_errorMessage = message;
  setSections({});
  emit stateChanged();
}
