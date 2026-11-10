package com.wanderwildwood.chizu.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import app.organicmaps.R

/** The address of the app's own source code. */
const val SOURCE_URL = "https://github.com/wanderwildwood/chizu"

/** Each distinct license text once: family id and the raw resource. */
val LICENCE_RAW: List<Pair<String, Int>> = listOf(
    "gpl" to R.raw.gpl_3_0,
    "apache" to R.raw.apache_2_0,
    "ofl" to R.raw.ofl_1_1,
)

@Composable
fun rememberLicencesData(): LicencesData = LicencesData(
    appName = "Atlas",
    sourceUrl = SOURCE_URL,
    licenceLine = stringResource(R.string.licences_licence),
    // One row per artifact in the APK, listed by the build. config/libraries adds what Gradle
    // cannot see: the map engine's own libraries, data sets, fonts and icons (gen-credits.py).
    components = rememberGeneratedComponents(R.raw.aboutlibraries),
    texts = LICENCE_RAW.map { (id, raw) ->
        LicenceText(id, stringResource(when (id) {
            "gpl" -> R.string.licences_full_gpl
            "apache" -> R.string.licences_full_apache
            "ofl" -> R.string.licences_full_ofl
            else -> R.string.licences_full_head
        }), raw)
    },
)

@Composable
fun rememberLicencesLabels(): LicencesLabels {
    val none = stringResource(R.string.about_no_browser_address, "@@")
    val noText = stringResource(R.string.licences_no_text, "@@")
    return LicencesLabels(
        title = stringResource(R.string.licences_title),
        back = stringResource(R.string.licences_back),
        sourceHeading = stringResource(R.string.licences_source_head),
        noText = { licence -> noText.replace("@@", licence) },
        noBrowser = { address -> none.replace("@@", address) },
    )
}
