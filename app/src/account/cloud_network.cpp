#include "account/cloud_library.h"
#include "account/cloud_json.h"
#include "account/numa_account.h"
#include <QNetworkReply>
#include <QNetworkRequest>
#include <QJsonDocument>
#include <QJsonArray>
#include <QFile>
#include <QDateTime>
#include <QCryptographicHash>
#include <QSaveFile>
#include <QRegularExpression>
#include <memory>

void CloudLibrary::request(const QString &path, const QByteArray &method, const QByteArray &body, std::function<void(QByteArray)> done, bool image) {
    const auto generation = m_generation; const auto owner = m_owner; QPointer<CloudLibrary> self(this);
    m_account->withAccessToken([self, generation, owner, path, method, body, done, image](const QString &token) {
        if (!self || self->m_generation != generation || self->m_owner != owner) return;
        if (token.isEmpty()) { self->fail(QStringLiteral("Sin conexión o sesión caducada. Se reintentará al conectar.")); return; }
        QNetworkRequest request(self->m_account->serviceUrl().resolved(QUrl(path)));
        request.setTransferTimeout(25000); request.setAttribute(QNetworkRequest::RedirectPolicyAttribute, QNetworkRequest::ManualRedirectPolicy);
        request.setRawHeader("Authorization", "Bearer " + token.toUtf8()); request.setHeader(QNetworkRequest::ContentTypeHeader, image ? "image/webp" : "application/json");
        auto *reply = self->m_network.sendCustomRequest(request, method, body); self->m_reply = reply;
        auto bytes = std::make_shared<QByteArray>();
        connect(reply, &QIODevice::readyRead, self, [reply, bytes] { bytes->append(reply->readAll()); if (bytes->size() > 4 * 1024 * 1024) reply->abort(); });
        connect(reply, &QNetworkReply::finished, self, [self, generation, owner, reply, bytes, done] {
            reply->deleteLater(); if (!self || generation != self->m_generation || owner != self->m_owner) return;
            bytes->append(reply->readAll()); const auto status = reply->attribute(QNetworkRequest::HttpStatusCodeAttribute).toInt();
            if (reply->error() != QNetworkReply::NoError || status < 200 || status >= 300 || bytes->size() > 4 * 1024 * 1024) {
                self->fail(status == 413 ? QStringLiteral("La copia supera el espacio de la nube. Tus datos locales se conservan.") : QStringLiteral("No se pudo sincronizar. Comprueba la conexión y reintenta.")); return;
            }
            done(*bytes);
        });
    });
}
void CloudLibrary::synchronize() {
    if (!m_account->isSignedIn() || m_owner != m_account->userId()) return;
    if (m_busy) { retry(); return; }
    m_busy = true; m_status = QStringLiteral("Sincronizando biblioteca…"); emit changed(); if (!save()) return;
    auto uploads = m_data.value("uploads").toObject();
    const auto styles = m_data.value("local").toObject().value("coverStyles").toObject();
    for (auto it = uploads.begin(); it != uploads.end();) {
        const auto digest = it.key();
        bool referenced = false;
        for (auto style = styles.begin(); style != styles.end(); ++style) if (style.value().toObject().value("asset") == digest) referenced = true;
        if (!referenced) { it = uploads.erase(it); continue; }
        ++it;
        if (!recoverCover(digest)) continue;
        QFile file(assetPath(digest)); if (!file.open(QIODevice::ReadOnly)) continue;
        m_data.insert("uploads", uploads); if (!save()) return;
        request("/sync/covers/" + digest, "PUT", file.readAll(), [this, digest](QByteArray) {
            auto pending = m_data.value("uploads").toObject(); pending.remove(digest); m_data.insert("uploads", pending); save(); m_busy = false; retry();
        }, true); return;
    }
    m_data.insert("uploads", uploads); if (!save()) return;
    request("/sync/library", "GET", {}, [this](QByteArray bytes) {
        const auto document = QJsonDocument::fromJson(bytes).object();
        if (!document.value("library").isObject()) { fail(QStringLiteral("La respuesta de sincronización no es válida.")); return; }
        const auto base = m_data.value("base").toObject(), counters = m_data.value("counters").toObject();
        auto sent = m_data.value("local").toObject(), styles = sent.value("coverStyles").toObject();
        const auto baseStyles = base.value("coverStyles").toObject(), pending = m_data.value("uploads").toObject();
        for (auto it = styles.begin(); it != styles.end();) {
            const auto digest = it.value().toObject().value("asset").toString();
            if (!pending.contains(digest)) { ++it; continue; }
            if (baseStyles.contains(it.key())) { it.value() = baseStyles.value(it.key()); ++it; }
            else it = styles.erase(it);
        }
        sent.insert("coverStyles", styles);
        if (sent == base && counters == m_data.value("acknowledged").toObject()) { finishSync(document.value("library").toObject(), sent, counters); return; }
        const auto seq = m_data.value("seq").toInteger() + 1; m_data.insert("seq", seq); if (!save()) return;
        const QJsonObject body{{"client", m_client}, {"seq", seq}, {"base", base}, {"local", sent}, {"counters", counters}};
        request("/sync/library", "POST", QJsonDocument(body).toJson(QJsonDocument::Compact), [this, sent, counters](QByteArray bytes) {
            const auto result = QJsonDocument::fromJson(bytes).object();
            if (!result.value("library").isObject()) { fail(QStringLiteral("No se pudo validar la copia de la nube.")); return; }
            finishSync(result.value("library").toObject(), sent, counters);
        });
    });
}
void CloudLibrary::finishSync(const QJsonObject &remote, const QJsonObject &sent, const QJsonObject &counters) {
    const auto merged = cloud::reconcile(sent, m_data.value("local").toObject(), remote);
    m_data.insert("local", cloud::addPending(merged, cloud::countersDelta(m_data.value("counters").toObject(), counters)));
    m_data.insert("base", remote); m_data.insert("acknowledged", counters); if (!save()) return; ++m_revision; emit changed(); fetchCovers();
}
void CloudLibrary::fetchCovers() {
    const auto styles = m_data.value("local").toObject().value("coverStyles").toObject();
    for (auto it = styles.begin(); it != styles.end(); ++it) {
        const auto digest = it.value().toObject().value("asset").toString();
        if (digest.size() != 64 || digest.contains(QRegularExpression("[^a-f0-9]")) || recoverCover(digest)
            || m_data.value("uploads").toObject().contains(digest)) continue;
        request("/sync/covers/" + digest, "GET", {}, [this, digest](QByteArray bytes) {
            const auto hash = QString::fromLatin1(QCryptographicHash::hash(bytes, QCryptographicHash::Sha256).toHex());
            if (hash != digest || bytes.size() > 1048576) { fail(QStringLiteral("No se pudo verificar una carátula.")); return; }
            QSaveFile file(assetPath(digest)); if (!file.open(QIODevice::WriteOnly) || file.write(bytes) != bytes.size() || !file.commit()) { fail(QStringLiteral("No se pudo guardar una carátula.")); return; }
            ++m_revision; emit changed(); fetchCovers();
        }); return;
    }
    m_busy = false; m_lastSynced = QDateTime::currentDateTime().toString(Qt::ISODate); m_status = m_data.value("uploads").toObject().isEmpty() ? QStringLiteral("Biblioteca sincronizada con tu cuenta NÜMA.")
        : QStringLiteral("Biblioteca sincronizada. Falta una imagen local: vuelve a elegir la carátula para enviarla."); emit changed();
}
