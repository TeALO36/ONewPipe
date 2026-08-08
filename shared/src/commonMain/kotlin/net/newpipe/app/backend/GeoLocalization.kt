package net.newpipe.app.backend

import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.ContentCountry
import org.schabi.newpipe.extractor.localization.Localization

/** Two-letter ISO country code of the user's system locale (e.g. "FR"), or "" when unknown. */
expect fun systemCountryCode(): String

/** Two-letter ISO language code of the user's system locale (e.g. "fr"), or "" when unknown. */
expect fun systemLanguageCode(): String

/**
 * Applies the user's system locale to the NewPipe extractor so trending and
 * search results are geo-localized (YouTube `gl`/`hl` parameters) instead of
 * defaulting to US content. Without this, a French user gets American
 * trending/search results.
 */
fun applySystemGeoLocalization() {
    val country = systemCountryCode()
    val language = systemLanguageCode()
    try {
        NewPipe.setPreferredLocalization(
            Localization(language.ifBlank { "en" }, country.ifBlank { "US" })
        )
    } catch (_: Exception) {
        // Extractors that ignore localization must keep working.
    }
    try {
        if (country.isNotBlank()) {
            NewPipe.setPreferredContentCountry(ContentCountry(country))
        }
    } catch (_: Exception) {
        // Unknown/unsupported country codes are ignored.
    }
}
