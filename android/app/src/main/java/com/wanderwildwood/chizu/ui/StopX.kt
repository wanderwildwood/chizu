package com.wanderwildwood.chizu.ui

import android.view.View
import androidx.activity.ComponentActivity

/*
 * The round X in the guidance foot. A drive is not ended by a brushed thumb: the first tap fills
 * the button, the second one stops. Anything else the person does puts it back (ArmedWatch).
 */
object StopX {
    @JvmStatic
    fun wire(button: View, activity: ComponentActivity, stop: Runnable) {
        val watch = ArmedWatch.of(activity)
        val plain: () -> Unit = { button.isActivated = false }
        button.setOnClickListener {
            if (watch.isArmed(plain)) {
                watch.done(plain)
                plain()
                stop.run()
            } else {
                button.isActivated = true
                watch.arm(plain)
            }
        }
    }
}
