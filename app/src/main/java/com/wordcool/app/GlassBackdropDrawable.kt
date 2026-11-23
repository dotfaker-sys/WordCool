package com.wordcool.app

import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RadialGradient
import android.graphics.Shader
import android.graphics.drawable.Drawable
import kotlin.math.max
import kotlin.random.Random

/**
 * 极光玻璃底：冷色渐变 + 五团柔光 + 细微颗粒。
 *
 * XML 的 layer-list 也能画 radial gradient，但多层叠加时容易出现生硬的色带；
 * 这里用 RadialGradient 的四段透明度 + 噪点，做出更接近磨砂玻璃的通透底色。
 * Shader 只在尺寸变化时重建，滚动/重绘时不会重复分配。
 */
class GlassBackdropDrawable(private val res: Resources) : Drawable() {

    private val fillPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val glowPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val beamPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val grainPaint = Paint(Paint.ANTI_ALIAS_FLAG)

    private val grain: Bitmap = buildGrain()
    private val grainShader = BitmapShader(grain, Shader.TileMode.REPEAT, Shader.TileMode.REPEAT)

    private var cachedW = 0
    private var cachedH = 0
    private val glows = ArrayList<Shader>(5)
    private var baseShader: Shader? = null
    private var beamShader: Shader? = null

    init {
        glowPaint.style = Paint.Style.FILL
        beamPaint.style = Paint.Style.FILL
        grainPaint.style = Paint.Style.FILL
        grainPaint.shader = grainShader
        grainPaint.alpha = 12
    }

    private fun color(id: Int): Int = res.getColor(id, null)

    private fun buildGrain(): Bitmap {
        val size = 112
        val bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
        val rnd = Random(20261122)
        val pixels = IntArray(size * size)
        for (i in pixels.indices) {
            val v = rnd.nextInt(256)
            val a = rnd.nextInt(10)
            pixels[i] = (a shl 24) or (v shl 16) or (v shl 8) or v
        }
        bmp.setPixels(pixels, 0, size, 0, 0, size, size)
        return bmp
    }

    private fun rebuild(w: Int, h: Int) {
        cachedW = w
        cachedH = h
        val maxSide = max(w, h).toFloat()

        baseShader = LinearGradient(
            0f, 0f, w.toFloat(), h.toFloat(),
            color(R.color.glass_bg_start), color(R.color.glass_bg_end),
            Shader.TileMode.CLAMP
        )

        data class Blob(val x: Float, val y: Float, val r: Float, val c: Int)
        // 只保留三团极淡的大面积柔光，背景保持干净
        val blobs = listOf(
            Blob(0.08f, 0.02f, 0.60f, color(R.color.glass_glow_warm)),
            Blob(0.94f, 0.22f, 0.55f, color(R.color.glass_glow_cool)),
            Blob(0.52f, 0.92f, 0.58f, color(R.color.glass_glow_extra))
        )
        glows.clear()
        for (b in blobs) {
            glows.add(
                RadialGradient(
                    w * b.x, h * b.y, maxSide * b.r,
                    intArrayOf(b.c, withAlpha(b.c, 10), 0x00FFFFFF),
                    floatArrayOf(0f, 0.58f, 1f),
                    Shader.TileMode.CLAMP
                )
            )
        }

        // 左上到右下的斜向反光，让整块背景像一块斜放的玻璃
        beamShader = LinearGradient(
            0f, 0f, w.toFloat(), h * 0.72f,
            intArrayOf(0x1AFFFFFF, 0x00FFFFFF, 0x0AFFFFFF),
            floatArrayOf(0f, 0.42f, 1f),
            Shader.TileMode.CLAMP
        )
    }

    private fun withAlpha(c: Int, a: Int): Int = (c and 0x00FFFFFF) or (a shl 24)

    override fun draw(canvas: Canvas) {
        val w = bounds.width()
        val h = bounds.height()
        if (w <= 0 || h <= 0) return
        if (w != cachedW || h != cachedH) rebuild(w, h)

        canvas.save()
        canvas.clipRect(bounds)

        fillPaint.shader = baseShader
        canvas.drawRect(bounds, fillPaint)

        glowPaint.shader = null
        for (s in glows) {
            glowPaint.shader = s
            canvas.drawRect(bounds, glowPaint)
        }

        beamPaint.shader = beamShader
        canvas.drawRect(bounds, beamPaint)

        canvas.drawRect(bounds, grainPaint)
        canvas.restore()
    }

    override fun setAlpha(alpha: Int) {
        // 背景始终铺满，透明度由颜色自身控制
    }

    override fun setColorFilter(colorFilter: android.graphics.ColorFilter?) {
        // 主题切换会重建 Drawable，无需额外着色
    }

    @Deprecated("Deprecated in Drawable")
    override fun getOpacity(): Int = android.graphics.PixelFormat.OPAQUE
}
