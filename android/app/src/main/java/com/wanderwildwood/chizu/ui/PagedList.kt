@file:JvmName("PagedList")

package com.wanderwildwood.chizu.ui

import android.view.MotionEvent
import android.view.ViewConfiguration
import androidx.recyclerview.widget.RecyclerView
import app.organicmaps.R
import kotlin.math.abs

/*
 * A swipe moves the list one screen, and then stops.
 *
 * The house list is PagedColumn, which is Compose; these lists are RecyclerViews with their own
 * adapters, headers and sections, so they take the behaviour and not the component (the same
 * helper as Messaging's lists). No inertia: a screen that redraws in full renders a fling as a
 * smear. No scrollbar either: the stock one fades in and out, which is an animation.
 *
 * The page you arrive at begins where the last one ended. After the jump, the row the page landed
 * part-way through is pulled fully into view, so the top edge is always a row's edge and the row
 * cut at the foot of the old page (it is drawn, and pressable) opens the new one whole.
 *
 * Only plainly vertical gestures are claimed. Once a page has turned the rest of the drag is
 * swallowed: one swipe is one page, however far the finger keeps travelling.
 */
fun RecyclerView.turnsAPageOnSwipe() {
    isVerticalScrollBarEnabled = false
    onFlingListener = object : RecyclerView.OnFlingListener() {
        override fun onFling(velocityX: Int, velocityY: Int) = true
    }

    val slop = ViewConfiguration.get(context).scaledTouchSlop
    addOnItemTouchListener(object : RecyclerView.OnItemTouchListener {
        private var downX = 0f
        private var downY = 0f
        private var turned = false

        override fun onInterceptTouchEvent(rv: RecyclerView, e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.x
                    downY = e.y
                    turned = false
                }
                MotionEvent.ACTION_MOVE -> {
                    if (turned) return true
                    val dy = e.y - downY
                    val dx = e.x - downX
                    if (abs(dy) > slop && abs(dy) > abs(dx)) {
                        turned = true
                        rv.turnPage(forward = dy < 0)
                        (rv.getTag(R.id.page_turn_listener) as? Runnable)?.run()
                        return true
                    }
                }
            }
            return false
        }

        override fun onTouchEvent(rv: RecyclerView, e: MotionEvent) = Unit
        override fun onRequestDisallowInterceptTouchEvent(disallow: Boolean) = Unit
    })
}

/**
 * [then] runs at each page turn. The lists used to hide the keyboard when a drag began; a page
 * turn never becomes a drag, so that is hung here instead.
 */
fun RecyclerView.onPageTurn(then: Runnable) {
    setTag(R.id.page_turn_listener, then)
}

/** One screen on, landing on a row's edge. By pixels, so a row taller than the screen takes two pages. */
private fun RecyclerView.turnPage(forward: Boolean) {
    val page = height - paddingTop - paddingBottom
    if (page <= 0) return
    if (!forward) {
        turnPageBack(page)
        return
    }
    scrollBy(0, page)
    // At the end the list scrolls only as far as there is list left; pulling a row's edge to the
    // top there would push the last rows back under the foot, where no swipe reaches them.
    if (!canScrollVertically(1)) return
    val first = getChildAt(0) ?: return
    // A row taller than the screen cannot be brought fully into view; pulling at it would hand
    // back nearly the whole page.
    if (first.height > page) return
    scrollBy(0, first.top - if (clipToPadding) paddingTop else 0)
}

/**
 * One screen back, losing nothing: the row that opened the page before lands whole at the bottom
 * of this one. The top row is pulled into view only when that costs no more than that overlap
 * row; otherwise the page opens mid-row and the next page back shows that row whole.
 */
private fun RecyclerView.turnPageBack(page: Int) {
    val topEdge = if (clipToPadding) paddingTop else 0
    val bottomEdge = height - paddingBottom
    val opener = (0 until childCount).map { getChildAt(it) }.firstOrNull { it.bottom > topEdge }
    if (opener == null || opener.height > page) {
        scrollBy(0, -page)
        return
    }
    val overlap = opener.height
    scrollBy(0, opener.bottom - bottomEdge)
    if (!canScrollVertically(-1)) return
    val first = getChildAt(0) ?: return
    val cut = topEdge - first.top
    if (cut in 1..overlap && first.height <= page) scrollBy(0, -cut)
}
