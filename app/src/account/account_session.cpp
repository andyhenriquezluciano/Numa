#include "account/numa_account.h"
#include "account/account_endpoint.h"
#include <QJsonDocument>
#include <QJsonObject>
#include <QDebug>
#include <qtkeychain/keychain.h>

namespace { constexpr auto keychainService = "dev.sfg.numa"; }

void NumaAccount::restoreSession() {
  setStatus(QStringLiteral("restoring"));
  const quint64 generation = m_generation;
  auto *job = new QKeychain::ReadPasswordJob(QString::fromLatin1(keychainService), this);
  job->setKey(numaAccountSessionKey(m_serviceUrl));
  job->setInsecureFallback(false);
  connect(job, &QKeychain::Job::finished, this, [this, generation](QKeychain::Job *baseJob) {
    // A sign-in started while the keyring was thinking wins.
    if (generation != m_generation)
      return;
    auto *readJob = static_cast<QKeychain::ReadPasswordJob *>(baseJob);
    const QJsonObject secret = QJsonDocument::fromJson(readJob->textData().toUtf8()).object();
    if (readJob->error() != QKeychain::NoError || secret.value(QStringLiteral("refreshToken")).toString().isEmpty()) {
      if (readJob->error() != QKeychain::NoError && readJob->error() != QKeychain::EntryNotFound)
        qWarning().noquote() << "Could not read the Numa account from the OS keyring:" << readJob->errorString();
      setStatus(QStringLiteral("signed_out"));
      return;
    }
    m_refreshToken = secret.value(QStringLiteral("refreshToken")).toString();
    m_deviceId = secret.value(QStringLiteral("deviceId")).toString();
    m_userId = secret.value(QStringLiteral("userId")).toString();
    m_userName = secret.value(QStringLiteral("userName")).toString();
    m_userEmail = secret.value(QStringLiteral("userEmail")).toString();
    m_userPicture = secret.value(QStringLiteral("userPicture")).toString();
    emit userChanged();
    // Optimistic so offline starts still look signed in. The refresh
    // confirms the session and signs out if the server revoked it.
    setStatus(QStringLiteral("signed_in"));
    refreshAccessToken();
  });
  job->start();
}

void NumaAccount::persistSession() {
  if (!m_useKeychain || m_refreshToken.isEmpty())
    return;
  const QJsonObject secret{{QStringLiteral("refreshToken"), m_refreshToken},
                           {QStringLiteral("deviceId"), m_deviceId},
                           {QStringLiteral("userId"), m_userId},
                           {QStringLiteral("userName"), m_userName},
                           {QStringLiteral("userEmail"), m_userEmail},
                           {QStringLiteral("userPicture"), m_userPicture}};
  auto *job = new QKeychain::WritePasswordJob(QString::fromLatin1(keychainService), this);
  job->setKey(numaAccountSessionKey(m_serviceUrl));
  job->setTextData(QString::fromUtf8(QJsonDocument(secret).toJson(QJsonDocument::Compact)));
  job->setInsecureFallback(false);
  connect(job, &QKeychain::Job::finished, this, [](QKeychain::Job *finishedJob) {
    if (finishedJob->error() != QKeychain::NoError)
      qWarning().noquote() << "Could not store the Numa account in the OS keyring:" << finishedJob->errorString();
  });
  job->start();
}

void NumaAccount::deleteSession() {
  if (!m_useKeychain)
    return;
  auto *job = new QKeychain::DeletePasswordJob(QString::fromLatin1(keychainService), this);
  job->setKey(numaAccountSessionKey(m_serviceUrl));
  job->setInsecureFallback(false);
  connect(job, &QKeychain::Job::finished, this, [](QKeychain::Job *finishedJob) {
    if (finishedJob->error() != QKeychain::NoError && finishedJob->error() != QKeychain::EntryNotFound)
      qWarning().noquote() << "Could not remove the Numa account from the OS keyring:" << finishedJob->errorString();
  });
  job->start();
}
