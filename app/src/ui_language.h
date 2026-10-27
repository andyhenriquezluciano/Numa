/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
#pragma once

#include <QObject>
#include <QSettings>
#include <QTranslator>

class QQmlEngine;

class SpanishTranslator final : public QTranslator {
public:
  QString translate(const char *context, const char *sourceText,
                    const char *disambiguation = nullptr, int n = -1) const override;
};

class UiLanguage final : public QObject {
  Q_OBJECT
  Q_PROPERTY(QString language READ language WRITE setLanguage NOTIFY languageChanged)
  Q_PROPERTY(QString effectiveLanguage READ effectiveLanguage NOTIFY languageChanged)

public:
  explicit UiLanguage(QObject *parent = nullptr);
  [[nodiscard]] QString language() const { return m_language; }
  [[nodiscard]] QString effectiveLanguage() const;
  void setLanguage(const QString &language);
  void setEngine(QQmlEngine *engine);

signals:
  void languageChanged();

private:
  void apply();

  QSettings m_settings;
  SpanishTranslator m_spanish;
  QQmlEngine *m_engine{nullptr};
  QString m_language{QStringLiteral("system")};
  bool m_installed{false};
};
