package com.sablesrouis.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import kotlin.math.cos
import kotlin.math.sin

class Rock(val x: Float, val y: Float, val r: Float, val s: Float)

class BgResult(val bitmap: Bitmap, val rocks: List<Rock>, val cabanons: List<FloatArray>)

/** Colour with alpha as a float from 0 to 1. */
fun argbF(a: Float, r: Int, g: Int, b: Int): Int {
    val ai = (clamp(a, 0f, 1f) * 255f + 0.5f).toInt()
    return Color.argb(ai, r, g, b)
}

object BackgroundBuilder {

    fun build(pxW: Int, pxH: Int): BgResult {
        val bmp = Bitmap.createBitmap(pxW, pxH, Bitmap.Config.ARGB_8888)
        val g = Canvas(bmp)
        g.scale(pxW / SCENE_W, pxH / SCENE_H)
        val rng = Mulberry(7)
        val p = Paint(Paint.ANTI_ALIAS_FLAG)
        val path = Path()

        fun solid(color: Int) {
            p.shader = null; p.pathEffect = null; p.style = Paint.Style.FILL; p.color = color
        }

        // ---- land ----
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(200f, 0f, SCENE_W, 0f, Color.parseColor("#566640"), Color.parseColor("#3b4b30"), Shader.TileMode.CLAMP)
        g.drawRect(0f, 0f, SCENE_W, SCENE_H, p)

        val greens = intArrayOf(
            Color.parseColor("#6b7c47"), Color.parseColor("#3d4e30"), Color.parseColor("#7d8a4e"),
            Color.parseColor("#4b5d38"), Color.parseColor("#8a7752"), Color.parseColor("#66506a"),
            Color.parseColor("#33432a")
        )
        for (i in 0 until 2200) {
            val y = rng.next() * SCENE_H
            val x = duneX(y) + rng.next() * (SCENE_W - duneX(y))
            val a = 0.22f + rng.next() * 0.38f
            val c = greens[(rng.next() * greens.size).toInt() % greens.size]
            solid(Color.argb((a * 255f).toInt(), Color.red(c), Color.green(c), Color.blue(c)))
            g.drawCircle(x, y, 1.2f + rng.next() * 5.2f, p)
        }
        for (i in 0 until 110) {
            val y = rng.next() * SCENE_H
            val x = duneX(y) + 28f + rng.next() * (SCENE_W - duneX(y) - 28f)
            val r = 3f + rng.next() * 6f
            solid(argbF(0.45f, 20, 34, 22)); g.drawCircle(x + 2f, y + 2.5f, r, p)
            solid(Color.parseColor("#2e4228")); g.drawCircle(x, y, r, p)
            solid(Color.parseColor("#55703c")); g.drawCircle(x - r * 0.28f, y - r * 0.3f, r * 0.55f, p)
        }

        // ---- coastal path and access track ----
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 2.6f
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.color = argbF(0.75f, 222, 205, 160)
        path.reset()
        var yy = -10f
        while (yy <= SCENE_H + 10f) {
            val x = duneX(yy) + 46f + 9f * sin(yy * 0.019f + 1f)
            if (yy == -10f) path.moveTo(x, yy) else path.lineTo(x, yy)
            yy += 8f
        }
        g.drawPath(path, p)
        path.reset()
        path.moveTo(SCENE_W + 10f, 372f)
        path.quadTo(SCENE_W - 90f, 392f, duneX(436f) + 40f, 436f)
        path.lineTo(duneX(442f) + 4f, 444f)
        g.drawPath(path, p)

        val cabanons = ArrayList<FloatArray>()
        cabanons.add(floatArrayOf(duneX(300f) + 58f, 300f))
        cabanons.add(floatArrayOf(duneX(478f) + 62f, 478f))
        cabanons.add(floatArrayOf(SCENE_W - 42f, 372f))
        cabanons.add(floatArrayOf(duneX(560f) + 70f, 560f))
        for (c in cabanons) {
            solid(argbF(0.35f, 10, 18, 14)); g.drawRect(c[0] + 1.5f, c[1] + 2f, c[0] + 10.5f, c[1] + 8f, p)
            solid(Color.parseColor("#efe9dc")); g.drawRect(c[0], c[1], c[0] + 9f, c[1] + 6f, p)
            solid(Color.parseColor("#8a6a52")); g.drawRect(c[0], c[1], c[0] + 9f, c[1] + 2.6f, p)
        }

        // ---- sea bed (hidden under the water layer) ----
        path.reset()
        path.moveTo(-5f, -5f)
        yy = -5f
        while (yy <= SCENE_H + 5f) { path.lineTo(duneX(yy), yy); yy += 4f }
        path.lineTo(-5f, SCENE_H + 5f)
        path.close()
        p.style = Paint.Style.FILL
        p.shader = LinearGradient(
            0f, 0f, 250f, 0f,
            intArrayOf(
                Color.parseColor("#07303f"), Color.parseColor("#0e5567"), Color.parseColor("#2c8892"),
                Color.parseColor("#6fb5ab"), Color.parseColor("#bdb98c")
            ),
            floatArrayOf(0f, 0.4f, 0.7f, 0.9f, 1f), Shader.TileMode.CLAMP
        )
        g.drawPath(path, p)
        val reefs = arrayOf(
            floatArrayOf(112f, 150f, 52f, 26f), floatArrayOf(120f, 676f, 60f, 30f),
            floatArrayOf(150f, 255f, 34f, 16f), floatArrayOf(140f, 598f, 30f, 18f)
        )
        for (e in reefs) {
            solid(argbF(0.5f, 6, 40, 46))
            g.save(); g.translate(e[0], e[1]); g.rotate(17.2f)
            g.drawOval(RectF(-e[2], -e[3], e[2], e[3]), p)
            g.restore()
        }

        // ---- sand (the cove) and rock platform (the points), painted in strips ----
        val step = 3f
        yy = -6f
        while (yy < SCENE_H + 6f) {
            val cv = cove(yy)
            val sandy = smooth(0.18f, 0.5f, cv)
            val rocky = 1f - sandy
            val m = midX(yy)
            val rr = tideR(yy)
            val x0 = m - rr - 46f
            val x1 = duneX(yy) + 3f
            val span = x1 - x0
            val f = { v: Float -> clamp((v - x0) / span, 0f, 1f) }
            if (sandy > 0.01f) {
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(
                        argbF(0f, 196, 184, 140), argbF(0.55f, 190, 178, 136), Color.parseColor("#b98f66"),
                        Color.parseColor("#c9996b"), Color.parseColor("#d6a37a"), Color.parseColor("#e1b48c")
                    ),
                    floatArrayOf(0f, f(m - rr - 14f), f(m - rr), f(m), f(m + rr * 0.9f), 1f),
                    Shader.TileMode.CLAMP
                )
                p.alpha = (sandy * 255f).toInt()
                g.drawRect(x0, yy, x1, yy + step + 0.6f, p)
            }
            if (rocky > 0.01f) {
                p.style = Paint.Style.FILL
                p.shader = LinearGradient(
                    x0, 0f, x1, 0f,
                    intArrayOf(
                        argbF(0f, 80, 92, 84), argbF(0.7f, 70, 80, 76), Color.parseColor("#5b5a52"),
                        Color.parseColor("#6d695d"), Color.parseColor("#7a7566")
                    ),
                    floatArrayOf(0f, f(m - rr - 12f), f(m - rr), 0.85f, 1f),
                    Shader.TileMode.CLAMP
                )
                p.alpha = (rocky * 255f).toInt()
                g.drawRect(x0, yy, x1, yy + step + 0.6f, p)
            }
            yy += step
        }

        // sand speckles
        for (i in 0 until 900) {
            val y = rng.next() * SCENE_H
            if (cove(y) < 0.22f) continue
            val m = midX(y)
            val rr = tideR(y)
            val x = m - rr + rng.next() * (duneX(y) - m + rr)
            val a = 0.08f + rng.next() * 0.14f
            val light = rng.next() < 0.5f
            solid(if (light) argbF(a, 240, 210, 174) else argbF(a, 154, 107, 71))
            g.drawRect(x, y, x + 1f + rng.next() * 3f, y + 0.8f + rng.next(), p)
        }
        // strand line
        path.reset()
        yy = -4f
        while (yy <= SCENE_H + 4f) {
            val x = midX(yy) + tideR(yy) * 0.95f + 1.5f * sin(yy * 0.3f)
            if (yy == -4f) path.moveTo(x, yy) else path.lineTo(x, yy)
            yy += 4f
        }
        p.shader = null; p.style = Paint.Style.STROKE; p.strokeWidth = 1.2f; p.color = argbF(0.5f, 90, 70, 54)
        p.pathEffect = DashPathEffect(floatArrayOf(2f, 3f, 1f, 5f), 0f)
        g.drawPath(path, p)
        p.pathEffect = null

        // dune grass
        val grass = intArrayOf(
            Color.parseColor("#7c8a4c"), Color.parseColor("#97a35e"),
            Color.parseColor("#6a7a3f"), Color.parseColor("#b5b06a")
        )
        for (i in 0 until 420) {
            val y = rng.next() * SCENE_H
            val x = duneX(y) + (-4f + rng.next() * 12f)
            val a = 0.5f + rng.next() * 0.4f
            val c = grass[(rng.next() * 4f).toInt() % 4]
            solid(Color.argb((a * 255f).toInt(), Color.red(c), Color.green(c), Color.blue(c)))
            g.drawCircle(x, y, 1.5f + rng.next() * 3.2f, p)
        }

        // rocks on the points
        val rocks = ArrayList<Rock>()
        for (i in 0 until 130) {
            val y = rng.next() * SCENE_H
            if (cove(y) > 0.4f && rng.next() < 0.85f) continue
            val big = rng.next() < 0.25f
            val off = if (rng.next() < 0.35f) rng.next() * 70f else rng.next() * 8f
            val rx = midX(y) - tideR(y) - off + rng.next() * 12f
            val r = if (big) 6f + rng.next() * 8f else 2.2f + rng.next() * 4.5f
            rocks.add(Rock(rx, y, r, rng.next() * 100f))
        }
        rocks.sortBy { it.y }
        for (k in rocks) drawRock(g, p, path, k)

        return BgResult(bmp, rocks, cabanons)
    }

    private fun drawRock(g: Canvas, p: Paint, path: Path, k: Rock) {
        val rr = Mulberry((k.s * 1000f).toInt())
        val n = 8
        val px = FloatArray(n)
        val py = FloatArray(n)
        for (i in 0 until n) {
            val a = i.toFloat() / n.toFloat() * TAU
            val j = 0.72f + rr.next() * 0.5f
            px[i] = cos(a) * k.r * j
            py[i] = sin(a) * k.r * j * 0.9f
        }
        fun poly(ox: Float, oy: Float, sc: Float) {
            path.reset()
            for (i in 0 until n) {
                val x = k.x + ox + px[i] * sc
                val y = k.y + oy + py[i] * sc
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            path.close()
        }
        p.shader = null; p.pathEffect = null; p.style = Paint.Style.FILL
        p.color = argbF(0.38f, 8, 18, 20); poly(1.8f, 2.4f, 1.05f); g.drawPath(path, p)
        p.color = Color.parseColor("#4b463f"); poly(0f, 0f, 1f); g.drawPath(path, p)
        p.color = argbF(0.85f, 139, 131, 115); poly(-k.r * 0.14f, -k.r * 0.18f, 0.62f); g.drawPath(path, p)
        if (k.r > 5f) {
            p.color = argbF(0.5f, 122, 138, 80); poly(k.r * 0.2f, k.r * 0.1f, 0.28f); g.drawPath(path, p)
        }
    }
}
