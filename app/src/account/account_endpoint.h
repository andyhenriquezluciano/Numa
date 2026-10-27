#pragma once
#include <QCryptographicHash>
#include <QString>
#include <QUrl>

inline constexpr auto numaAccountDefaultUrl = "https://numa-account.peluchito281.workers.dev";
inline QString numaAccountSessionKey(const QUrl &url) {
    return QStringLiteral("numa-account-") + QString::fromLatin1(QCryptographicHash::hash(
        url.adjusted(QUrl::StripTrailingSlash).toEncoded(), QCryptographicHash::Sha256).toHex());
}
