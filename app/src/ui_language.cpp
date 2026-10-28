/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
#include "ui_language.h"
#include "translations/spanish_dictionary.h"

#include <QCoreApplication>
#include <QHash>
#include <QQmlEngine>

namespace {
QHash<QString, QString> buildSpanishTable() {
  QHash<QString, QString> map;
  map.reserve(1200);
  loadSpanishTranslations1(map);
  loadSpanishTranslations2(map);
  loadSpanishTranslations3(map);
  loadSpanishTranslations4(map);
  return map;
}
} // namespace

QString SpanishTranslator::translate(const char *, const char *sourceText,
                                     const char *, int) const {
  if (!sourceText) return {};
  static const QHash<QString, QString> text = buildSpanishTable();
  const QString source = QString::fromUtf8(sourceText);
  QString translated = text.value(source);
  if (translated.isEmpty() && source.contains(QStringLiteral("NÜMA"))) {
    QString legacySource = source;
    legacySource.replace(QStringLiteral("NÜMA"), QStringLiteral("Numa"));
    translated = text.value(legacySource);
  }
  translated.replace(QStringLiteral("Numa"), QStringLiteral("NÜMA"));
  return translated;
}

UiLanguage::UiLanguage(QObject *parent) : QObject(parent) {
  const QString saved = m_settings.value(QStringLiteral("ui/language"), QStringLiteral("es")).toString();
  if (saved == QStringLiteral("en") || saved == QStringLiteral("es")) m_language = saved;
  if (saved != m_language) m_settings.setValue(QStringLiteral("ui/language"), m_language);
  apply();
}

QString UiLanguage::effectiveLanguage() const {
  return m_language;
}

void UiLanguage::setLanguage(const QString &language) {
  const QString value = language == QStringLiteral("es") || language == QStringLiteral("en")
      ? language : QStringLiteral("es");
  if (m_language == value) return;
  m_language = value;
  m_settings.setValue(QStringLiteral("ui/language"), value);
  apply();
  emit languageChanged();
}

void UiLanguage::setEngine(QQmlEngine *engine) {
  m_engine = engine;
}

void UiLanguage::apply() {
  if (m_installed) {
    QCoreApplication::removeTranslator(&m_spanish);
    m_installed = false;
  }
  if (effectiveLanguage() == QStringLiteral("es")) {
    m_installed = QCoreApplication::installTranslator(&m_spanish);
  }
  if (m_engine) m_engine->retranslate();
}
