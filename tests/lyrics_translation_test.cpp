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

#include "lyrics/translation/translation_packs.h"
#include "lyrics/translation/google_translation.h"
#include "lyrics/translation/translation_store.h"
#include "lyrics/translation/translation_worker.h"
#include "translation/litert_api.h"
#include "translation/lyric_language.h"
#include "translation/marian_model.h"

#include <QDir>
#include <QFile>
#include <QNetworkAccessManager>
#include <QTemporaryDir>
#include <QtTest>

// Proof that a computer can tell "사랑해" from "I love you", which is more than some exes manage.
namespace {
lyric_language::SongPlan planOf(const QStringList &lines) {
  std::vector<std::string> utf8;
  for (const QString &line : lines)
    utf8.push_back(line.toStdString());
  return lyric_language::plan(utf8);
}

std::vector<bool> bools(std::initializer_list<bool> values) { return values; }
} // namespace

class LyricsTranslationTest : public QObject {
  Q_OBJECT

private slots:
  void googleBackoffRespectsServerRetryAfter() {
    const qint64 now = QDateTime::fromString(QStringLiteral("2026-10-28T00:00:00Z"), Qt::ISODate).toMSecsSinceEpoch();
    QCOMPARE(googleTranslationRetryDelay(302, {}, now), qint64(600'000));
    QCOMPARE(googleTranslationRetryDelay(429, "120", now), qint64(120'000));
    QCOMPARE(googleTranslationRetryDelay(503, "Wed, 28 Oct 2026 00:02:00 GMT", now), qint64(120'000));
    QCOMPARE(googleTranslationRetryDelay(0, {}, now), qint64(30'000));
  }

  void googleJoinsEverySentenceInTheVerse() {
    const QJsonArray segments{
        QJsonArray{QStringLiteral("¡Espera! "), QStringLiteral("Wait! ")},
        QJsonArray{QStringLiteral("No te vayas. "), QStringLiteral("Don't go. ")},
        QJsonArray{QStringLiteral("Vuelve\n"), QStringLiteral("Come back\n")}};
    QCOMPARE(googleTranslatedLine(segments), QStringLiteral("¡Espera! No te vayas. Vuelve"));
  }

  void koreanSongKeepsEnglishLines() {
    const QStringList lines{
        QStringLiteral("서둘러서 정리해 걔는 Real bad"), QStringLiteral("받아주면 안돼"),
        QStringLiteral("No you better trust me"), QStringLiteral("답답해서 그래"),
        QStringLiteral("He's been totally lying, yeah"), QString(),
    };
    const auto plan = planOf(lines);
    QCOMPARE(QString::fromStdString(plan.source), QStringLiteral("kor"));
    QVERIFY(plan.translate == bools({true, true, false, true, false, false}));
  }

  void japaneseClaimsKanjiOnlyLines() {
    const QStringList lines{QStringLiteral("君のことを考えると眠れない"), QStringLiteral("永遠"),
                            QStringLiteral("今夜は君と踊りたい")};
    const auto plan = planOf(lines);
    QCOMPARE(QString::fromStdString(plan.source), QStringLiteral("jpn"));
    QVERIFY(plan.translate == bools({true, true, true}));
  }

  void spanishSongLeansOnTheSong() {
    const QStringList lines{QStringLiteral("Te quiero más que a mi vida"), QStringLiteral("¿Dónde estás, mi amor?"),
                            QStringLiteral("Despacito"), QStringLiteral("I know you want me")};
    const auto plan = planOf(lines);
    QCOMPARE(QString::fromStdString(plan.source), QStringLiteral("spa"));
    // The unclear one-word line follows the song; the English line stays.
    QVERIFY(plan.translate == bools({true, true, true, false}));
  }

  void englishSongNeedsNothing() {
    const QStringList lines{QStringLiteral("I can't stop thinking about you"), QStringLiteral("Oh oh oh"),
                            QStringLiteral("Baby, you're the one"), QStringLiteral("Je ne sais quoi")};
    const auto plan = planOf(lines);
    QVERIFY(plan.source.empty());
    QVERIFY(plan.translate == bools({false, false, false, false}));
  }

  void portugueseIsNotSpanish() {
    const QStringList lines{QStringLiteral("Eu não sei o que fazer"), QStringLiteral("Meu coração é seu"),
                            QStringLiteral("Você é tudo pra mim")};
    QCOMPARE(planOf(lines).source, std::string("por"));
  }

  void repeatedVocalSyllablesCannotTurnAnEnglishSongIntoPortuguese() {
    QStringList lines{QStringLiteral("Keep a mild groove on"), QStringLiteral("Get the cool shoeshine")};
    for (int i = 0; i < 20; ++i) lines.append(QStringLiteral("Dey-do-da-bop"));
    QVERIFY(lyric_language::detectLine("Dey-do-da-bop").empty());
    QVERIFY(lyric_language::isVocalization("Dey-do-da-bop"));
    QVERIFY(!lyric_language::isVocalization("Eu não sei o que fazer"));
    QVERIFY(planOf(lines).source.empty());
  }

  void localModelsCanReachSpanishThroughEnglish() {
    QCOMPARE(TranslationPacks::route(QStringLiteral("kor"), QStringLiteral("spanish"), QStringLiteral("standard")),
             (QStringList{QStringLiteral("kor-eng"), QStringLiteral("eng-spa")}));
    QCOMPARE(TranslationPacks::route(QStringLiteral("eng"), QStringLiteral("spanish"), QStringLiteral("high")),
             (QStringList{QStringLiteral("eng-spa")}));
    QCOMPARE(TranslationPacks::route(QStringLiteral("spa"), QStringLiteral("english"), QStringLiteral("standard")),
             (QStringList{QStringLiteral("spa-eng")}));
  }

  void storeRoundTrips() {
    QTemporaryDir dir;
    const QString path = dir.filePath(QStringLiteral("t.sqlite"));
    {
      TranslationStore store(path);
      QVERIFY(!QFile::exists(path)); // lazy: nothing on disk until used
      store.store(QStringLiteral("rev1"), QStringLiteral("사랑해"), QStringLiteral("I love you"));
    }
    TranslationStore store(path);
    const auto hit = store.lookup(QStringLiteral("rev1"), {QStringLiteral("사랑해"), QStringLiteral("안녕")});
    QCOMPARE(hit.size(), 1);
    QCOMPARE(hit.value(QStringLiteral("사랑해")), QStringLiteral("I love you"));
    // A new pack revision never reuses an old model's output.
    QVERIFY(store.lookup(QStringLiteral("rev2"), {QStringLiteral("사랑해")}).isEmpty());
  }

  void packNeedsMarkerAndSizes() {
    QTemporaryDir dir;
    qputenv("NUMA_TRANSLATION_DIR", dir.path().toUtf8());
    QNetworkAccessManager network;
    TranslationPacks packs(&network);
    const auto *pack = TranslationPacks::find(QStringLiteral("kor-eng"));
    QVERIFY(pack);
    QVERIFY(!packs.installed(QStringLiteral("kor-eng")));
    QVERIFY(packs.available(QStringLiteral("kor-eng")));

    const QDir target(packs.directory(QStringLiteral("kor-eng")));
    QVERIFY(QDir().mkpath(target.path()));
    for (const auto &file : pack->files) {
      QFile out(target.filePath(QLatin1String(file.name)));
      QVERIFY(out.open(QIODevice::WriteOnly));
      QVERIFY(out.resize(file.size));
    }
    QVERIFY(!packs.installed(QStringLiteral("kor-eng")));
    QFile marker(target.filePath(QStringLiteral("pack-revision")));
    QVERIFY(marker.open(QIODevice::WriteOnly));
    marker.write(pack->revision);
    marker.close();
    QVERIFY(packs.installed(QStringLiteral("kor-eng")));
    QVERIFY(packs.installedBytes() > 20'000'000);

    packs.remove(QStringLiteral("kor-eng"));
    QVERIFY(!packs.installed(QStringLiteral("kor-eng")));
    qunsetenv("NUMA_TRANSLATION_DIR");
  }

  void qualityPicksPack() {
    for (const auto &pack : translationPackTable()) {
      const QString source = QLatin1String(pack.source);
      const QString target = QLatin1String(pack.id).contains(QStringLiteral("-spa"))
                                 ? QStringLiteral("spanish") : QStringLiteral("english");
      // Every language has a standard pack to fall back on.
      QVERIFY(TranslationPacks::resolve(source, target, QStringLiteral("standard")));
      const auto *high = TranslationPacks::resolve(source, target, QStringLiteral("high"));
      QVERIFY(high);
      QCOMPARE(QLatin1String(high->source), source);
    }
    QVERIFY(!TranslationPacks::resolve(QStringLiteral("por"), QStringLiteral("english"), QStringLiteral("standard")));
  }

  void modelsTranslateBetweenSpanishAndEnglish() {
    const QString directory = qEnvironmentVariable("NUMA_TEST_PAIR_PACKS");
    if (directory.isEmpty()) QSKIP("NUMA_TEST_PAIR_PACKS not set");
    for (bool toEnglish : {true, false}) {
      TranslationWorker worker(std::make_shared<std::atomic_int>(7));
      QSignalSpy translated(&worker, &TranslationWorker::translated);
      QSignalSpy finished(&worker, &TranslationWorker::finished);
      const QString input = toEnglish ? QStringLiteral("Quiero bailar contigo esta noche.")
                                      : QStringLiteral("I want to dance with you tonight.");
      const QString pack = toEnglish ? QStringLiteral("spa-eng") : QStringLiteral("eng-spa");
      worker.translateRoute(7, {QDir(directory).filePath(pack)}, {input}, {input});
      QCOMPARE(finished.size(), 1);
      QVERIFY2(finished.first().at(1).toString().isEmpty(), qPrintable(finished.first().at(1).toString()));
      QCOMPARE(translated.size(), 1);
      const QString result = translated.first().at(2).toString();
      QVERIFY2(result.contains(toEnglish ? QStringLiteral("dance") : QStringLiteral("bailar"), Qt::CaseInsensitive), qPrintable(result));
      QVERIFY(result != input);
    }
  }

  void downloadsThePublicSpanishModel() {
    if (!qEnvironmentVariableIsSet("NUMA_TEST_MODEL_DOWNLOAD")) QSKIP("NUMA_TEST_MODEL_DOWNLOAD not set");
    QTemporaryDir directory;
    QVERIFY(directory.isValid());
    qputenv("NUMA_TRANSLATION_DIR", directory.path().toUtf8());
    QNetworkAccessManager network;
    TranslationPacks packs(&network);
    QSignalSpy ready(&packs, &TranslationPacks::ready);
    QSignalSpy failed(&packs, &TranslationPacks::failed);
    packs.ensure(QStringLiteral("spa-eng"));
    QTRY_VERIFY_WITH_TIMEOUT(ready.size() + failed.size() > 0, 120000);
    QVERIFY2(failed.isEmpty(), failed.isEmpty() ? "" : qPrintable(failed.first().at(1).toString()));
    QCOMPARE(ready.size(), 1);
    QVERIFY(packs.installed(QStringLiteral("spa-eng")));
  }

  // Needs a real pack and libLiteRt; set NUMA_TEST_KOR_PACK to a kor-eng folder to run.
  void modelTranslatesKorean() {
    const QString packDir = qEnvironmentVariable("NUMA_TEST_KOR_PACK");
    if (packDir.isEmpty())
      QSKIP("NUMA_TEST_KOR_PACK not set");
    std::string error;
    const LiteRtApi *api = LiteRtApi::get(TranslationWorker::liteRtLibraryPath().toStdString(), &error);
    QVERIFY2(api, error.c_str());
    MarianModel model;
    QVERIFY2(model.load(*api, packDir.toStdString(), 1, &error), error.c_str());
    QCOMPARE(model.translate("오늘 밤 너와 춤추고 싶어"), std::string("I want to dance with you tonight."));
    QCOMPARE(model.translate("눈물이 멈추지 않아"), std::string("The tears don't stop."));
    int calls = 0;
    QVERIFY(model.translate("눈물이 멈추지 않아", [&calls] { return ++calls > 2; }).empty());
  }
};

QTEST_GUILESS_MAIN(LyricsTranslationTest)
#include "lyrics_translation_test.moc"
