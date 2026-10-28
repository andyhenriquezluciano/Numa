#include "ui_language.h"
#include <QSettings>
#include <QTemporaryDir>
#include <QtTest>

class UiLanguageTest : public QObject {
    Q_OBJECT
    QTemporaryDir directory;

private slots:
    void initTestCase() {
        QVERIFY(directory.isValid());
        QSettings::setDefaultFormat(QSettings::IniFormat);
        QSettings::setPath(QSettings::IniFormat, QSettings::UserScope, directory.path());
        QCoreApplication::setOrganizationName("NumaTests");
        QCoreApplication::setApplicationName("Language");
    }

    void init() { QSettings().clear(); }

    void migratesSystemToAnExplicitLanguage() {
        QSettings().setValue("ui/language", "system");
        UiLanguage language;
        QCOMPARE(language.language(), QStringLiteral("es"));
        QCOMPARE(language.effectiveLanguage(), QStringLiteral("es"));
        QCOMPARE(QSettings().value("ui/language").toString(), QStringLiteral("es"));
    }

    void preservesAnExplicitChoiceAcrossRestarts() {
        for (const auto &choice : {QStringLiteral("en"), QStringLiteral("es")}) {
            QSettings().setValue("ui/language", choice);
            UiLanguage language;
            QCOMPARE(language.language(), choice);
            QCOMPARE(language.effectiveLanguage(), choice);
        }
    }

    void acceptsOnlyEnglishAndSpanish() {
        UiLanguage language;
        language.setLanguage("en");
        QCOMPARE(language.effectiveLanguage(), QStringLiteral("en"));
        language.setLanguage("system");
        QCOMPARE(language.language(), QStringLiteral("es"));
        QCOMPARE(QSettings().value("ui/language").toString(), QStringLiteral("es"));
    }
};

QTEST_GUILESS_MAIN(UiLanguageTest)
#include "ui_language_test.moc"
