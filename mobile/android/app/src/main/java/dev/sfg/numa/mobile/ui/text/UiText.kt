package dev.sfg.numa.mobile.ui.text

import java.util.Locale

/** Interface copy only. Catalog titles, artist names and user content are not translated. */
fun uiText(english: String, spanish: String): String =
    if (Locale.getDefault().language == "en") english else spanish
