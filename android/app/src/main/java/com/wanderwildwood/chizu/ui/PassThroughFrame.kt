package com.wanderwildwood.chizu.ui

import android.content.Context
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout

/** A frame that shows its children and takes no touch: every touch goes to what lies under it. */
class PassThroughFrame @JvmOverloads constructor(context: Context, attrs: AttributeSet? = null) :
    FrameLayout(context, attrs) {
    override fun dispatchTouchEvent(ev: MotionEvent?): Boolean = false
}
