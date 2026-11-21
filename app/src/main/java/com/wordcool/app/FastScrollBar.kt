package com.wordcool.app

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.util.AttributeSet
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.widget.AbsListView
import android.widget.ListView
import kotlin.math.abs

/**
 * 词库页右侧的可拖动快速滚动条。
 *
 * 系统 ListView 自带的滚动条只能看、不能拖；这里用一层覆盖在列表右侧的轻量 View
 * 画一条细轨道 + 一个滑块，按住滑块上下拖动即可按比例跳到列表任意位置。
 * 只有按在滑块附近才拦截触摸，其他区域的事件照常交给下面的 ListView。
 */
class FastScrollBar @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : View(context, attrs, defStyleAttr) {

    private var list: ListView? = null

    private val trackPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x66D8D3C9
        style = Paint.Style.FILL
    }
    private val thumbPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xB36B6F76.toInt()
        style = Paint.Style.FILL
    }

    private val thumbRect = RectF()
    private val density = resources.displayMetrics.density
    private val trackWidth = 2f * density
    private val thumbWidth = 5f * density
    private val minThumbHeight = 48f * density
    private val touchSlop = ViewConfiguration.get(context).scaledTouchSlop.toFloat()

    private var thumbTop = 0f
    private var thumbHeight = 0f
    private var dragging = false
    private var grabOffset = 0f

    fun attach(l: ListView) {
        list = l
        l.setOnScrollListener(object : AbsListView.OnScrollListener {
            override fun onScrollStateChanged(view: AbsListView?, scrollState: Int) = Unit

            override fun onScroll(
                view: AbsListView?,
                firstVisibleItem: Int,
                visibleItemCount: Int,
                totalItemCount: Int
            ) {
                if (!dragging) refresh()
            }
        })
        refresh()
    }

    /** 数据量 / 折叠状态变化后调用，重新计算滑块长度和位置 */
    fun refresh() {
        val l = list ?: return
        val total = l.count
        val visible = (l.lastVisiblePosition - l.firstVisiblePosition + 1).coerceAtLeast(1)
        if (total <= visible || height == 0) {
            thumbHeight = 0f
            thumbTop = 0f
            invalidate()
            return
        }

        thumbHeight = maxOf(minThumbHeight, height * visible / total.toFloat())
        val maxTop = (height - thumbHeight).coerceAtLeast(0f)
        val maxFirst = (total - visible).coerceAtLeast(1)
        val progress = (l.firstVisiblePosition / maxFirst.toFloat()).coerceIn(0f, 1f)
        thumbTop = maxTop * progress
        invalidate()
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        refresh()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (thumbHeight <= 0f) return

        val cx = width / 2f
        canvas.drawRoundRect(
            cx - trackWidth / 2f, 0f, cx + trackWidth / 2f, height.toFloat(),
            trackWidth / 2f, trackWidth / 2f, trackPaint
        )
        thumbRect.set(cx - thumbWidth / 2f, thumbTop, cx + thumbWidth / 2f, thumbTop + thumbHeight)
        canvas.drawRoundRect(thumbRect, thumbWidth / 2f, thumbWidth / 2f, thumbPaint)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val l = list ?: return false
        if (thumbHeight <= 0f) return false

        when (event.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                val nearX = abs(event.x - width / 2f) <= thumbWidth / 2f + touchSlop
                val nearY = event.y >= thumbTop - touchSlop &&
                    event.y <= thumbTop + thumbHeight + touchSlop
                if (!nearX || !nearY) return false
                dragging = true
                grabOffset = event.y - thumbTop
                parent?.requestDisallowInterceptTouchEvent(true)
                return true
            }

            MotionEvent.ACTION_MOVE -> {
                if (!dragging) return false
                val maxTop = (height - thumbHeight).coerceAtLeast(0f)
                thumbTop = (event.y - grabOffset).coerceIn(0f, maxTop)
                invalidate()
                jumpTo(if (maxTop == 0f) 0f else thumbTop / maxTop)
                return true
            }

            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                if (!dragging) return false
                dragging = false
                parent?.requestDisallowInterceptTouchEvent(false)
                refresh()
                return true
            }
        }
        return false
    }

    private fun jumpTo(fraction: Float) {
        val l = list ?: return
        val total = l.count
        val visible = (l.lastVisiblePosition - l.firstVisiblePosition + 1).coerceAtLeast(1)
        val maxFirst = (total - visible).coerceAtLeast(0)
        if (maxFirst == 0) return
        val position = (fraction * maxFirst).toInt().coerceIn(0, maxFirst)
        l.setSelectionFromTop(position, 0)
    }
}
