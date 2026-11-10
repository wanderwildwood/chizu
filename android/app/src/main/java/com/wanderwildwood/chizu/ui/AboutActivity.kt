package com.wanderwildwood.chizu.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import com.mudita.mmd.ThemeMMD

/**
 * The About, opened from the menu and from Settings. The rest of Atlas is Views; this one screen
 * is Compose so that it is the same About as in every other app. It draws nothing of its own:
 * the dialog sits over the screen it was opened from, and closing it ends the activity.
 */
class AboutActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ThemeMMD(colorScheme = monochrome, typography = houseTypography) {
                AboutDialog(onDismiss = { finish() })
            }
        }
    }
}
