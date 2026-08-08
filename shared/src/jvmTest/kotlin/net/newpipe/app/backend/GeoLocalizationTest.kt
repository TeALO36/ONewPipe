package net.newpipe.app.backend

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.NewPipe
import org.schabi.newpipe.extractor.localization.Localization
import java.util.Locale
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies that the extractor is geo-localized from the system locale, so a
 * French user gets French trending/search content (YouTube gl/hl params)
 * instead of the US default.
 */
class GeoLocalizationTest {

    private val initialized: Boolean by lazy {
        NewPipe.init(
            OkHttpDownloader(OkHttpClient.Builder().build()),
            Localization("en", "US")
        )
        true
    }

    @Test
    fun `applies system locale to the extractor`() {
        initialized
        val previousLocale = Locale.getDefault()
        try {
            Locale.setDefault(Locale.FRANCE) // country "FR", language "fr"
            applySystemGeoLocalization()
            assertEquals("FR", NewPipe.getPreferredContentCountry().countryCode)
            assertEquals("fr", NewPipe.getPreferredLocalization().languageCode)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }

    @Test
    fun `unknown locale falls back safely`() {
        initialized
        val previousLocale = Locale.getDefault()
        try {
            // A locale without a country must not break the extractor config.
            Locale.setDefault(Locale("zh")) // language only, no country
            applySystemGeoLocalization()
            assertEquals("zh", NewPipe.getPreferredLocalization().languageCode)
        } finally {
            Locale.setDefault(previousLocale)
        }
    }
}
