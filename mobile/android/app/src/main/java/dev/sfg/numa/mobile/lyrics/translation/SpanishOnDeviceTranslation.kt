/* Copyright (C) 2026 SFG545
 * SPDX-License-Identifier: AGPL-3.0-or-later
 */
package dev.sfg.numa.mobile.lyrics.translation

import com.google.android.gms.tasks.Task
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.common.model.RemoteModelManager
import com.google.mlkit.nl.translate.TranslateRemoteModel
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException
import kotlinx.coroutines.suspendCancellableCoroutine

internal class SpanishOnDeviceTranslation {
    suspend fun prepare(sourceCode: String, allowModelDownload: Boolean, requireWifi: Boolean = true, onDownload: () -> Unit = {}) {
        val source = languageTag(sourceCode) ?: error("Este idioma todavía no admite traducción local.")
        if (source == TranslateLanguage.SPANISH) return
        val manager = RemoteModelManager.getInstance()
        val installed = manager.getDownloadedModels(TranslateRemoteModel::class.java).await().map { it.language }.toSet()
        val missing = missingTranslationModels(source, installed)
        if (missing.isEmpty()) return
        if (!allowModelDownload) throw TranslationModelPending()
        onDownload()
        val downloadConditions = DownloadConditions.Builder().apply { if (requireWifi) this.requireWifi() }.build()
        val completed = kotlinx.coroutines.withTimeoutOrNull(120_000L) {
            missing.forEach { language ->
                manager.download(TranslateRemoteModel.Builder(language).build(), downloadConditions).await()
            }
            true
        } ?: false
        check(completed) { "La descarga del modelo está tardando demasiado. Vuelve a intentarlo." }
    }

    suspend fun translate(sourceCode: String, texts: Collection<String>, allowModelDownload: Boolean,
        onDownload: () -> Unit = {}, onReady: () -> Unit = {},
        onTranslated: suspend (String, String) -> Unit = { _, _ -> }, requireWifi: Boolean = true,
    ): Map<String, String> {
        if (texts.isEmpty()) return emptyMap()
        val source = languageTag(sourceCode) ?: error("Este idioma todavía no admite traducción local.")
        if (source == TranslateLanguage.SPANISH) return texts.associateWith { it }
        prepare(sourceCode, allowModelDownload, requireWifi, onDownload)
        onReady()

        val translator = Translation.getClient(
            TranslatorOptions.Builder()
                .setSourceLanguage(source)
                .setTargetLanguage(TranslateLanguage.SPANISH)
                .build(),
        )
        try {
            val translated = LinkedHashMap<String, String>()
            for (text in texts) {
                val value = translator.translate(text).await().trim()
                onTranslated(text, value)
                translated[text] = value
            }
            return translated
        } finally {
            translator.close()
        }
    }

    fun revision(sourceCode: String): String = "mlkit-es-v1-${sourceCode.ifBlank { "eng" }}"

    private fun languageTag(code: String): String? = when (code.ifBlank { "eng" }) {
        "eng" -> TranslateLanguage.ENGLISH
        "kor" -> TranslateLanguage.KOREAN
        "jpn" -> TranslateLanguage.JAPANESE
        "zho" -> TranslateLanguage.CHINESE
        "rus" -> TranslateLanguage.RUSSIAN
        "ell" -> TranslateLanguage.GREEK
        "ara" -> TranslateLanguage.ARABIC
        "tur" -> TranslateLanguage.TURKISH
        "fra" -> TranslateLanguage.FRENCH
        "deu" -> TranslateLanguage.GERMAN
        "spa" -> TranslateLanguage.SPANISH
        "ita" -> TranslateLanguage.ITALIAN
        "nld" -> TranslateLanguage.DUTCH
        "cat" -> TranslateLanguage.CATALAN
        "por" -> TranslateLanguage.PORTUGUESE
        else -> TranslateLanguage.fromLanguageTag(code)
    }
}

internal class TranslationModelPending : IllegalStateException("El modelo del idioma todavía no está preparado.")

private suspend fun <T> Task<T>.await(): T = suspendCancellableCoroutine { continuation ->
    addOnSuccessListener { value -> if (continuation.isActive) continuation.resume(value) }
    addOnFailureListener { error -> if (continuation.isActive) continuation.resumeWithException(error) }
    addOnCanceledListener { continuation.cancel() }
}
