package com.wordcool.app

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.ColorFilter
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PixelFormat
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.random.Random

/**
 * 一块真正「浮起来」的玻璃表面。
 *
 * 之前用 View.elevation 做阴影时，阴影会被半透明卡片透出来，卡片内部发灰；
 * 这里改成自绘：先把卡片自身从画布裁掉，再在裁出的外侧画多层圆角阴影，
 * 最后画渐变玻璃面、右下角冷色染色、顶部高光和渐变描边。
 * 这样阴影只出现在卡片外沿，半透明区域保持干净。
 */
class GlassSurfaceDrawable(
    private val res: Resources,
    private val radiusDp: Float,
    private val shadowDp: Float = 4f,
    private val shadowDyDp: Float = 2f,
    private val fillTopColor: Int = res.getColor(R.color.glass_card_top, null),
    private val fillBottomColor: Int = res.getColor(R.color.glass_card_bottom, null),
    private val tintColor: Int = res.getColor(R.color.glass_card_tint, null),
    private val borderBright: Int = res.getColor(R.color.glass_border_bright, null),
    private val borderSoft: Int = res.getColor(R.color.glass_border_soft, null),
    private val shineColor: Int = res.getColor(R.color.glass_shine, null),
    private val shadowColor: Int = res.getColor(R.color.glass_shadow, null)
) : Drawable() {

    private val density = res.displayMetrics.density
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
    }
    private val card = RectF()
    private val clip = Path()
    private val cardPath = Path()
    private val boundsPath = Path()
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        shader = BitmapShader(grain(), Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)
        alpha = 16
    }

    private var shaderWidth = -1f
    private var shaderHeight = -1f
    private var fillShader: Shader? = null
    private var tintShader: Shader? = null
    private var sheenShader: Shader? = null
    private var borderShader: Shader? = null

    companion object {
        private var grainBitmap: Bitmap? = null

        /** 细颗粒磨砂纹理，压住大面积渐变的色带 */
        private fun grain(): Bitmap {
            grainBitmap?.let { return it }
            val size = 72
            val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
            val rnd = Random(9407)
            val pixels = IntArray(size * size)
            for (i in pixels.indices) {
                val v = rnd.nextInt(256)
                val a = rnd.nextInt(14)
                pixels[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
            }
            bmp.setPixels(pixels, 0, size, 0, 0, size, size)
            grainBitmap = bmp
            return bmp
        }
    }

    private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)

    override fun draw(canvas: Canvas) {
        val b = bounds
        if (b.isEmpty) return
        paint.alpha = 255
        borderPaint.alpha = 255

        val shadow = shadowDp * density
        val dy = shadowDyDp * density
        card.set(b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom - shadow)
        if (card.height() <= 0f || card.width() <= 0f) return
        val radius = (radiusDp * density).coerceAtMost(minOf(card.width(), card.height()) / 2f)

        cardPath.reset()
        cardPath.addRoundRect(card, radius, radius, Path.Direction.CW)

        if (shadow > 0f) {
            boundsPath.reset()
            boundsPath.addRect(
                b.left.toFloat(), b.top.toFloat(), b.right.toFloat(), b.bottom.toFloat(),
                Path.Direction.CW
            )
            clip.reset()
            clip.op(boundsPath, cardPath, Path.Op.DIFFERENCE)
            canvas.save()
            canvas.clipPath(clip)
            val steps = 6
            for (i in steps downTo 1) {
                val spread = shadow * i / steps
                val alpha = (Color.alpha(shadowColor) * (1f - i.toFloat() / (steps + 1))).toInt()
                paint.shader = null
                paint.color = withAlpha(shadowColor, alpha)
                canvas.drawRoundRect(
                    card.left - spread, card.top - spread + dy,
                    card.right + spread, card.bottom + spread + dy,
                    radius + spread, radius + spread, paint
                )
            }
            canvas.restore()
        }

        // 磨砂玻璃面：左上亮、右下透
        rebuildShadersIfNeeded()
        paint.shader = fillShader
        canvas.drawPath(cardPath, paint)

        // 右下角一圈冷色玻璃染色
        paint.shader = tintShader
        canvas.drawPath(cardPath, paint)

        // 顶部反光
        paint.shader = sheenShader
        canvas.drawPath(cardPath, paint)
        paint.shader = null

        // 细颗粒，模拟磨砂玻璃
        canvas.drawPath(cardPath, grainPaint)

        // 1dp 渐变描边：上亮下淡，像被左上光源照亮
        borderPaint.shader = borderShader
        borderPaint.strokeWidth = density
        canvas.drawPath(cardPath, borderPaint)
        borderPaint.shader = null
    }

    /** 渐变 shader 与大小绑定，滚动时只复用不重建，避免每帧分配 */
    private fun rebuildShadersIfNeeded() {
        if (card.width() == shaderWidth && card.height() == shaderHeight) return
        shaderWidth = card.width()
        shaderHeight = card.height()
        fillShader = LinearGradient(
            card.left, card.top, card.right, card.bottom,
            fillTopColor, fillBottomColor, Shader.TileMode.CLAMP
        )
        tintShader = LinearGradient(
            card.left, card.top, card.right, card.bottom,
            0x00FFFFFF, tintColor, Shader.TileMode.CLAMP
        )
        sheenShader = LinearGradient(
            card.left, card.top, card.left, card.top + card.height() * 0.55f,
            shineColor, 0x00FFFFFF, Shader.TileMode.CLAMP
        )
        borderShader = LinearGradient(
            card.left, card.top, card.left, card.bottom,
            borderBright, borderSoft, Shader.TileMode.CLAMP
        )
    }

    override fun setAlpha(alpha: Int) {
        paint.alpha = alpha
        borderPaint.alpha = alpha
        invalidateSelf()
    }

    override fun setColorFilter(colorFilter: ColorFilter?) {
        paint.colorFilter = colorFilter
        borderPaint.colorFilter = colorFilter
        invalidateSelf()
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT

    override fun getPadding(padding: Rect): Boolean {
        // 背景底部留出阴影高度，内容仍按布局 padding 排，不需要额外 inset
        return super.getPadding(padding)
    }

    override fun onBoundsChange(bounds: Rect) {
        super.onBoundsChange(bounds)
        invalidateSelf()
    }
}
