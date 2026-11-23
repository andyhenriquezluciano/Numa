// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

#include <QAbstractListModel>
#include <QVariantList>
#include <QtQml/qqmlregistration.h>
#include <algorithm>

class PlaylistTrackModel : public QAbstractListModel {
  Q_OBJECT
  QML_ELEMENT
  Q_PROPERTY(int count READ count NOTIFY countChanged)

public:
  using QAbstractListModel::QAbstractListModel;

  int count() const { return static_cast<int>(m_tracks.size()); }
  int rowCount(const QModelIndex &parent = {}) const override {
    return parent.isValid() ? 0 : count();
  }

  QHash<int, QByteArray> roleNames() const override {
    return {{Qt::UserRole, QByteArrayLiteral("track")}};
  }

  QVariant data(const QModelIndex &index, int role) const override {
    if (role != Qt::UserRole || !index.isValid() || index.row() < 0 || index.row() >= count())
      return {};
    return m_tracks.at(index.row());
  }

  Q_INVOKABLE void updateRows(const QVariantList &tracks) {
    const int before = count();
    const int after = static_cast<int>(tracks.size());
    if (after >= before && std::equal(m_tracks.cbegin(), m_tracks.cend(), tracks.cbegin())) {
      if (after == before)
        return;
      beginInsertRows({}, before, after - 1);
      m_tracks = tracks;
      endInsertRows();
    } else {
      beginResetModel();
      m_tracks = tracks;
      endResetModel();
    }
    if (before != after)
      emit countChanged();
  }

signals:
  void countChanged();

private:
  QVariantList m_tracks;
};
