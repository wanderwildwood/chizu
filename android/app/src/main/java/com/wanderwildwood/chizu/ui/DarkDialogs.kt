package com.wanderwildwood.chizu.ui

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Bundle
import androidx.annotation.StyleRes
import androidx.appcompat.app.AlertDialog
import androidx.fragment.app.DialogFragment
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentActivity
import androidx.fragment.app.FragmentManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/**
 * Atlas's dialogs in dark mode. The app's own, not a shared file: every dialog fragment and bottom
 * sheet is reached once, through the fragment manager of each activity, and every alert through
 * [DarkAlertDialogBuilder]. Each hands its window to [DarkMode.window] once it is made.
 */
object DarkDialogs {
    private val fragments = object : FragmentManager.FragmentLifecycleCallbacks() {
        // A dialog fragment shows its dialog in its own onStart, so the window is up by now.
        override fun onFragmentStarted(fm: FragmentManager, f: Fragment) {
            if (f !is DialogFragment) return
            val window = f.dialog?.window ?: return
            DarkMode.window(window.decorView)
        }
    }

    fun init(application: Application) {
        application.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityCreated(activity: Activity, state: Bundle?) {
                (activity as? FragmentActivity)?.supportFragmentManager?.registerFragmentLifecycleCallbacks(fragments, true)
            }
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityResumed(activity: Activity) {}
            override fun onActivityPaused(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, state: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }
}

/** MaterialAlertDialogBuilder whose dialog is dark from its first frame while dark mode is on. */
class DarkAlertDialogBuilder : MaterialAlertDialogBuilder {
    constructor(context: Context) : super(context)
    constructor(context: Context, @StyleRes theme: Int) : super(context, theme)

    override fun create(): AlertDialog {
        val dialog = super.create()
        dialog.window?.let { DarkMode.window(it.decorView) }
        return dialog
    }
}
