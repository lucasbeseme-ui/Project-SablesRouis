package com.sablesrouis.wallpaper

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RadialGradient
import android.graphics.RectF
import android.graphics.LinearGradient
import android.graphics.Shader
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt
import kotlin.random.Random

private const val DEG = 57.29578f

private class Particle(var x: Float, var y: Float, var age: Float, var life: Float)
private class Paw(var x: Float, var y: Float, val rx: Float, val ry: Float, val k: Float)
private class Cloud(var x: Float, var y: Float, val r: Float, val k: Float)
private class Sparkle(val x: Float, val y: Float, val ph: Float, val f: Float)
private class Drop(var x: Float, var y: Float, val sp: Float)
private class Ring(val x: Float, val y: Float, var age: Float)
private class Mist(var x: Float, val y: Float, val r: Float, val k: Float)
private class TrailPt(val x: Float, val y: Float, val t: Float, val gap: Boolean)

private const val WAIT = 0
private const val PADDLE = 1
private const val RIDE = 2
private const val RETURN = 3
private const val DIVE = 4

private class Surfer(val col: Int, val bob: Float) {
    var y0 = 340f; var d0 = 90f
    var state = WAIT
    var timer = 2f
    var t = 0f
    var a = 0f
    var x = 0f; var y = 0f
    var hd = 3.1415927f
    var side = 1f
    var rx = 1f; var ry = 0f
    var sp = 0f
    var ducked = false
}

private class Kiter(val col: Int, val y0: Float, val dc: Float, val a0: Float, val w: Float, val sway: Float) {
    var th = 0f
    var a = 0f
    var x = 0f; var y = 0f
    var hd = 0f
    var jt = -1f
    var nj = 4f
    var tt = 0f
    var wasJump = false
    val trail = ArrayList<TrailPt>()
}

class Scene(private val pxW: Int, private val pxH: Int) {

    val st = Conditions(15f, 5f, 1.3f, 10f, 270f, 50f, "clear", 17.5f)
    var showSurf = false
    var showKite = false
    var qSurf = 0.7f
    var qKite = 0.7f

    private val rnd = Random.Default
    private val bgScale = clamp(max(pxW / SCENE_W, pxH / SCENE_H), 1f, 3f)
    private val bg: BgResult = BackgroundBuilder.build((SCENE_W * bgScale).roundToInt(), (SCENE_H * bgScale).roundToInt())
    private val bgBitmap: Bitmap = bg.bitmap
    private val rocks: List<Rock> = bg.rocks
    private val cabanons: List<FloatArray> = bg.cabanons

    private var time = 0f
    private var cloudA = 0.1f
    private var rainA = 0f
    private var fogA = 0f

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bmpPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val waterPath = Path()
    private val tmpPath = Path()
    private val sceneRect = RectF(0f, 0f, SCENE_W, SCENE_H)
    private val dropLines = FloatArray(300 * 4)

    private val multiply = PorterDuffXfermode(PorterDuff.Mode.MULTIPLY)
    private val screen = PorterDuffXfermode(PorterDuff.Mode.SCREEN)
    private val additive = PorterDuffXfermode(PorterDuff.Mode.ADD)

    // reusable unit radial gradients (drawn with the canvas scaled to the wanted radius)
    private val unitDark = RadialGradient(0f, 0f, 1f, argbF(1f, 2, 34, 48), argbF(0f, 2, 34, 48), Shader.TileMode.CLAMP)
    private val unitLight = RadialGradient(0f, 0f, 1f, argbF(1f, 170, 230, 224), argbF(0f, 170, 230, 224), Shader.TileMode.CLAMP)
    private val unitShoal = RadialGradient(0f, 0f, 1f, argbF(0.42f, 150, 226, 206), argbF(0f, 150, 226, 206), Shader.TileMode.CLAMP)
    private val unitCloud = RadialGradient(0f, 0f, 1f, argbF(1f, 0, 18, 28), argbF(0f, 0, 18, 28), Shader.TileMode.CLAMP)
    private val unitMist = RadialGradient(0f, 0f, 1f, argbF(1f, 238, 246, 248), argbF(0f, 238, 246, 248), Shader.TileMode.CLAMP)
    private val unitLamp = RadialGradient(0f, 0f, 1f, argbF(1f, 255, 200, 110), argbF(0f, 255, 170, 80), Shader.TileMode.CLAMP)
    private val waterShader = LinearGradient(
        0f, 0f, 250f, 0f,
        intArrayOf(argbF(0.97f, 9, 56, 78), argbF(0.94f, 12, 90, 108), argbF(0.88f, 22, 122, 132), argbF(0.74f, 70, 176, 170), argbF(0.52f, 140, 214, 196)),
        floatArrayOf(0f, 0.35f, 0.65f, 0.85f, 1f), Shader.TileMode.CLAMP
    )

    private val flow = ArrayList<Particle>()
    private val paws = ArrayList<Paw>()
    private val clouds = ArrayList<Cloud>()
    private val sparkles = ArrayList<Sparkle>()
    private val drops = ArrayList<Drop>()
    private val rings = ArrayList<Ring>()
    private val mist = ArrayList<Mist>()
    private val surfers = ArrayList<Surfer>()
    private val kiters = ArrayList<Kiter>()

    private val shoals = arrayOf(
        floatArrayOf(132f, 118f, 70f, 70f), floatArrayOf(122f, 708f, 78f, 78f), floatArrayOf(168f, 430f, 18f, 120f),
        floatArrayOf(150f, 372f, 12f, 60f), floatArrayOf(160f, 520f, 14f, 70f)
    )

    private val boardCols = intArrayOf(
        Color.parseColor("#f6f2e4"), Color.parseColor("#ffd23f"), Color.parseColor("#ff6b4a"),
        Color.parseColor("#42c7e2"), Color.parseColor("#e8eef0"), Color.parseColor("#9be07a")
    )
    private val kiteCols = intArrayOf(
        Color.parseColor("#ff5a36"), Color.parseColor("#ffd23f"), Color.parseColor("#e0307a"),
        Color.parseColor("#2fd3bb"), Color.parseColor("#7a5cff"), Color.parseColor("#fff0d0")
    )

    init {
        for (i in 0 until 130) flow.add(Particle(rnd.nextFloat() * SCENE_W, rnd.nextFloat() * SCENE_H, rnd.nextFloat() * 2f, 1.4f + rnd.nextFloat() * 1.8f))
        for (i in 0 until 26) paws.add(Paw(rnd.nextFloat() * SCENE_W, rnd.nextFloat() * SCENE_H, 14f + rnd.nextFloat() * 22f, 5f + rnd.nextFloat() * 7f, rnd.nextFloat()))
        for (i in 0 until 8) clouds.add(Cloud(rnd.nextFloat() * (SCENE_W + 300f) - 150f, rnd.nextFloat() * (SCENE_H + 200f) - 100f, 110f + rnd.nextFloat() * 110f, 0.5f + rnd.nextFloat() * 0.5f))
        for (i in 0 until 160) sparkles.add(Sparkle(rnd.nextFloat() * SCENE_W, rnd.nextFloat() * SCENE_H, rnd.nextFloat() * TAU, 0.6f + rnd.nextFloat() * 1.7f))
        for (i in 0 until 300) drops.add(Drop(rnd.nextFloat() * SCENE_W, rnd.nextFloat() * SCENE_H, 0.7f + rnd.nextFloat() * 0.6f))
        for (i in 0 until 7) mist.add(Mist(rnd.nextFloat() * (SCENE_W + 200f) - 100f, rnd.nextFloat() * SCENE_H, 120f + rnd.nextFloat() * 120f, rnd.nextFloat()))
        for (i in 0 until 6) {
            val s = Surfer(boardCols[i % boardCols.size], rnd.nextFloat() * TAU)
            s.y0 = 340f + rnd.nextFloat() * 170f
            s.d0 = 80f + rnd.nextFloat() * 60f
            s.timer = 1f + rnd.nextFloat() * 6f
            s.side = if (rnd.nextFloat() < 0.5f) -1f else 1f
            surfers.add(s)
        }
        for (i in 0 until 5) {
            kiters.add(Kiter(kiteCols[i % kiteCols.size], 270f + rnd.nextFloat() * 270f, 150f + rnd.nextFloat() * 95f,
                80f + rnd.nextFloat() * 55f, 0.36f + rnd.nextFloat() * 0.12f, rnd.nextFloat() * TAU).also {
                it.th = rnd.nextFloat() * TAU
                it.nj = 3f + rnd.nextFloat() * 6f
            })
        }
    }

    /* ---------- small drawing helpers ---------- */

    private fun solid(color: Int) {
        p.shader = null; p.xfermode = null; p.pathEffect = null; p.style = Paint.Style.FILL; p.color = color
    }

    private fun stroke(color: Int, w: Float) {
        p.shader = null; p.xfermode = null; p.pathEffect = null; p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND; p.strokeJoin = Paint.Join.ROUND; p.strokeWidth = w; p.color = color
    }

    /** Fills a unit radial gradient scaled to (rx, ry) at (cx, cy) with the given alpha. */
    private fun blob(c: Canvas, shader: Shader, cx: Float, cy: Float, rx: Float, ry: Float, rot: Float, alpha: Float, xfer: PorterDuffXfermode? = null) {
        p.pathEffect = null; p.xfermode = xfer; p.style = Paint.Style.FILL
        p.shader = shader
        p.alpha = (clamp(alpha, 0f, 1f) * 255f).toInt()
        c.save()
        c.translate(cx, cy)
        if (rot != 0f) c.rotate(rot * DEG)
        c.scale(rx, ry)
        c.drawCircle(0f, 0f, 1f, p)
        c.restore()
    }

    private fun buildWaterPath() {
        waterPath.reset()
        waterPath.moveTo(-10f, -10f)
        var y = -10f
        while (y <= SCENE_H + 10f) { waterPath.lineTo(wlX(y, st.tide), y); y += 4f }
        waterPath.lineTo(-10f, SCENE_H + 10f)
        waterPath.close()
    }

    private fun wlLine(out: Path, off: Float, wob: Float, tt: Float) {
        out.reset()
        var y = -10f
        var first = true
        while (y <= SCENE_H + 10f) {
            val x = wlX(y, st.tide) - off + (if (wob != 0f) wob * sin(y * 0.05f + tt) else 0f)
            if (first) { out.moveTo(x, y); first = false } else out.lineTo(x, y)
            y += 5f
        }
    }

    /* ---------- time of day ---------- */

    private val tintH = floatArrayOf(0f, 6.3f, 7.4f, 8.2f, 9.5f, 11f, 16f, 17.8f, 19.2f, 20.3f, 21.3f, 24.01f)
    private val tintC = arrayOf(
        intArrayOf(74, 92, 146), intArrayOf(74, 92, 146), intArrayOf(170, 122, 142), intArrayOf(255, 196, 150),
        intArrayOf(255, 240, 220), intArrayOf(255, 255, 255), intArrayOf(255, 255, 255), intArrayOf(255, 226, 184),
        intArrayOf(255, 172, 118), intArrayOf(132, 110, 150), intArrayOf(74, 92, 146), intArrayOf(74, 92, 146)
    )

    private fun tintAt(h: Float): FloatArray {
        for (i in 1 until tintH.size) {
            if (h <= tintH[i]) {
                val a = tintC[i - 1]
                val b = tintC[i]
                val t = (h - tintH[i - 1]) / (tintH[i] - tintH[i - 1])
                return floatArrayOf(lerp(a[0].toFloat(), b[0].toFloat(), t), lerp(a[1].toFloat(), b[1].toFloat(), t), lerp(a[2].toFloat(), b[2].toFloat(), t))
            }
        }
        return floatArrayOf(74f, 92f, 146f)
    }

    private fun cloudTarget(w: String): Float = when (w) { "cloudy" -> 0.75f; "rain" -> 0.95f; "fog" -> 0.5f; else -> 0.1f }
    private fun rainTarget(w: String): Float = if (w == "rain") 1f else 0f
    private fun fogTarget(w: String): Float = when (w) { "rain" -> 0.1f; "fog" -> 1f; else -> 0f }

    /* ---------- swell crests ---------- */

    private fun drawSwell(c: Canvas, hN: Float) {
        if (st.swellH < 0.12f) return
        val dvx = -sin(rad(st.swellDir))
        val dvy = cos(rad(st.swellDir))
        val pvx = -dvy
        val pvy = dvx
        val spacing = 26f + st.swellT * st.swellT * 0.62f
        val speed = spacing / st.swellT * 1.5f
        val phase = (speed * time) % spacing
        val cx = SCENE_W / 2f
        val cy = SCENE_H / 2f
        val half = 520f
        val k0 = floor(-half / spacing).toInt() - 1
        val k1 = ceil(half / spacing).toInt() + 1
        val aMain = 0.22f + 0.34f * hN
        val wMain = 1.5f + 2.0f * hN
        for (k in k0..k1) {
            val u = k * spacing + phase
            val amp = 0.55f + 0.45f * sin(k * 2.3f + 1.1f)
            for (seg in 0 until 2) {
                val back = if (seg == 0) 0f else -spacing * 0.34f
                stroke(0, if (seg == 0) wMain else wMain * 2.2f)
                var px = 0f
                var py = 0f
                var has = false
                var s = -640f
                while (s <= 640f) {
                    val wob = 6f * sin(s * 0.021f + k * 1.7f + time * 0.4f) + 3f * sin(s * 0.057f + k * 3.1f)
                    val uu = u + back + wob
                    val x = cx + dvx * uu + pvx * s
                    val y = cy + dvy * uu + pvy * s
                    if (has) {
                        val pat = 0.5f + 0.5f * sin(s * 0.018f + k * 2.9f)
                        val al = if (seg == 0) aMain * amp * (0.25f + 0.75f * pat) else 0.17f * hN * amp * pat
                        if (al > 0.01f) {
                            p.color = if (seg == 0) argbF(al, 228, 250, 246) else argbF(al, 0, 34, 48)
                            c.drawLine(px, py, x, y, p)
                        }
                    }
                    px = x; py = y; has = true
                    s += 26f
                }
            }
        }
    }

    private fun drawPaws(c: Canvas, wvx: Float, wvy: Float, dt: Float) {
        val kn = st.wind
        if (kn < 3f) return
        val sp = 8f + kn * 2.4f
        val ang = atan2(wvy, wvx)
        val a = min(0.3f, kn / 70f)
        for (pw in paws) {
            pw.x += wvx * sp * dt; pw.y += wvy * sp * dt
            if (pw.x < -60f) pw.x += SCENE_W + 120f
            if (pw.x > SCENE_W + 60f) pw.x -= SCENE_W + 120f
            if (pw.y < -60f) pw.y += SCENE_H + 120f
            if (pw.y > SCENE_H + 60f) pw.y -= SCENE_H + 120f
            val dark = pw.k < 0.62f
            blob(c, if (dark) unitDark else unitLight, pw.x, pw.y, pw.rx * (0.6f + kn / 40f), pw.ry, ang, if (dark) a else a * 0.55f)
        }
    }

    /* ---------- sun ---------- */

    private var sunS = 0f
    private var sunEl = 0f
    private var sunAz = 0f
    private var sunVis = 0f

    private fun updateSun() {
        val s = (st.hour - 8f) / 11.5f
        val day = s > 0f && s < 1f
        sunS = s
        sunEl = if (day) sin(3.1415927f * s) * 34f else 0f
        sunAz = rad(90f + clamp(s, 0f, 1f) * 180f)
        sunVis = smooth(0f, 0.05f, s) * smooth(1f, 0.95f, s)
    }

    /* ---------- surfers ---------- */

    private fun waitPos(s: Surfer) {
        s.x = wlX(s.y0, st.tide) - s.d0 + 1.4f * sin(time * 0.8f + s.bob)
        s.y = s.y0 + 1.8f * sin(time * 0.6f + s.bob * 2f)
    }

    private fun turnTo(h: Float, target: Float, k: Float): Float =
        h + atan2(sin(target - h), cos(target - h)) * k

    private fun updateSurfer(s: Surfer, dt: Float, on: Boolean) {
        s.a += clamp((if (on) 1f else 0f) - s.a, -dt / 0.9f, dt / 0.9f)
        when (s.state) {
            WAIT -> {
                waitPos(s)
                s.hd = 3.1415927f + 0.25f * sin(time * 0.5f + s.bob)
                s.timer -= dt
                if (s.timer <= 0f && on && s.a > 0.85f) { s.state = PADDLE; s.t = 0f }
            }
            PADDLE -> {
                s.t += dt
                val k = smooth(0f, 0.6f, s.t)
                s.hd = if (s.side > 0f) 3.1415927f * (1f - k) else -3.1415927f * (1f - k)
                s.x += 26f * dt * smooth(0.25f, 0.8f, s.t)
                if (s.t > 1.1f) {
                    s.state = RIDE; s.sp = 26f
                    val ang = s.side * 0.8f
                    s.rx = cos(ang); s.ry = sin(ang); s.t = 0f
                }
            }
            RIDE -> {
                s.t += dt
                s.sp = min(80f, s.sp + 60f * dt)
                s.x += s.rx * s.sp * dt; s.y += s.ry * s.sp * dt
                s.hd = atan2(s.ry, s.rx)
                if (s.x > wlX(s.y, st.tide) - 15f || s.t > 5.5f || s.y < 300f || s.y > 600f) {
                    s.state = RETURN; s.t = 0f; s.ducked = false
                    s.y0 = clamp(s.y + (rnd.nextFloat() - 0.5f) * 90f, 345f, 510f)
                    s.d0 = 84f + rnd.nextFloat() * 56f
                }
            }
            RETURN -> {
                val dx = wlX(s.y0, st.tide) - s.d0 - s.x
                val dy = s.y0 - s.y
                val dl = sqrt(dx * dx + dy * dy)
                if (dl < 4f) {
                    s.state = WAIT; s.timer = 3f + rnd.nextFloat() * 7f
                } else {
                    s.hd = turnTo(s.hd, atan2(dy, dx), min(1f, dt * 3f))
                    s.x += cos(s.hd) * 15f * dt
                    s.y += sin(s.hd) * 15f * dt
                    if (!s.ducked && wlX(s.y, st.tide) - s.x < 66f && dl > 24f) { s.state = DIVE; s.t = 0f; s.ducked = true }
                }
            }
            DIVE -> {
                s.t += dt / 1.2f
                s.x += cos(s.hd) * 22f * dt
                s.y += sin(s.hd) * 22f * dt
                if (s.t >= 1f) s.state = RETURN
            }
        }
    }

    private fun drawSurfer(c: Canvas, s: Surfer) {
        val al = s.a
        if (al < 0.02f) return
        val dive = s.state == DIVE
        val depth = if (dive) sin(3.1415927f * s.t) else 0f
        c.save()
        c.translate(s.x, s.y)
        c.rotate(s.hd * DEG)
        if (dive) {
            val r1 = if (s.t < 0.4f) s.t / 0.4f else 1f
            val r2 = if (s.t > 0.6f) (s.t - 0.6f) / 0.4f else 0f
            c.save()
            c.rotate(-s.hd * DEG)
            stroke(argbF(0.5f * (1f - r1) * al, 255, 255, 255), 1f)
            c.drawCircle(0f, 0f, 2f + r1 * 11f, p)
            if (r2 > 0f) {
                stroke(argbF(0.5f * (1f - r2) * al, 255, 255, 255), 1f)
                c.drawCircle(0f, 0f, 2f + r2 * 12f, p)
            }
            solid(argbF(0.5f * depth * al, 255, 255, 255))
            for (b in 0 until 5) {
                val bu = (s.t * 1.4f + b * 0.21f) % 1f
                c.drawCircle(sin(b * 2.3f + s.bob) * 6f, -2f - bu * 7f + cos(b * 1.7f) * 3f, 0.7f + 0.8f * (1f - bu), p)
            }
            c.restore()
        }
        val fade = al * (1f - 0.72f * depth)
        c.scale(1f - 0.34f * depth, 1f - 0.18f * depth)
        // shadow
        solid(argbF(0.28f * fade, 0, 24, 34))
        c.drawOval(RectF(1.2f - 6.4f, 1.8f - 2.4f, 1.2f + 6.4f, 1.8f + 2.4f), p)
        // board
        tmpPath.reset()
        tmpPath.moveTo(6.5f, 0f)
        tmpPath.quadTo(3f, -2.4f, -5f, -2.1f)
        tmpPath.quadTo(-6.6f, 0f, -5f, 2.1f)
        tmpPath.quadTo(3f, 2.4f, 6.5f, 0f)
        solid(Color.argb((fade * 255f).toInt(), Color.red(s.col), Color.green(s.col), Color.blue(s.col)))
        c.drawPath(tmpPath, p)
        stroke(argbF(0.35f * fade, 20, 30, 34), 0.5f)
        c.drawPath(tmpPath, p)
        stroke(argbF(0.3f * fade, 20, 30, 34), 0.5f)
        c.drawLine(-5f, 0f, 6f, 0f, p)
        val body = argbF(fade, 23, 37, 45)
        val skin = argbF(fade, 232, 180, 138)
        if (s.state == RIDE) {
            solid(body); c.drawOval(RectF(-0.6f - 1.7f, -3.1f, -0.6f + 1.7f, 3.1f), p)
            solid(skin); c.drawCircle(0.3f, 0f, 1.6f, p)
        } else if (s.state == PADDLE || s.state == RETURN || s.state == DIVE) {
            solid(body); c.drawOval(RectF(-1.2f - 3.6f, -1.5f, -1.2f + 3.6f, 1.5f), p)
            solid(skin); c.drawCircle(2.6f, 0f, 1.5f, p)
            val sw = sin(time * (if (s.state == RETURN) 4.2f else 9f) + s.bob) * 2f
            stroke(body, 0.9f)
            c.drawLine(1f, -1.2f, 2.6f + sw, -3.4f, p)
            c.drawLine(1f, 1.2f, 2.6f - sw, 3.4f, p)
        } else {
            solid(body); c.drawOval(RectF(-1f - 2.8f, -1.7f, -1f + 2.8f, 1.7f), p)
            solid(skin); c.drawCircle(1.7f, 0f, 1.4f, p)
        }
        c.restore()
    }

    /* ---------- kitesurfers ---------- */

    private fun updateKiter(k: Kiter, dt: Float, on: Boolean, px: Float, py: Float) {
        k.a += clamp((if (on) 1f else 0f) - k.a, -dt / 0.9f, dt / 0.9f)
        val cxp = wlX(k.y0, st.tide) - k.dc
        val cyp = k.y0
        val sh = abs(px)
        val maxA = (k.dc - 48f) / max(0.25f, sh)
        val amp = min(k.a0, maxA)
        val w = k.w * (0.55f + st.wind / 26f)
        k.th += w * dt
        val sn = sin(k.th)
        val cs = cos(k.th)
        k.x = max(18f, cxp + px * amp * sn)
        k.y = clamp(cyp + py * amp * sn, 30f, SCENE_H - 30f)
        val vx = px * amp * w * cs
        val vy = py * amp * w * cs
        if (abs(vx) + abs(vy) > 2f) k.hd = atan2(vy, vx)
        k.nj -= dt
        if (k.jt < 0f && k.nj <= 0f && on && k.a > 0.8f) k.jt = 0f
        if (k.jt >= 0f) {
            k.jt += dt / 0.95f
            if (k.jt >= 1f) { k.jt = -1f; k.nj = 4f + rnd.nextFloat() * 8f }
        }
        while (k.trail.isNotEmpty() && time - k.trail[0].t > 1.3f) k.trail.removeAt(0)
        k.tt += dt
        if (k.tt > 0.08f) {
            k.tt = 0f
            if (k.jt < 0.1f) {
                val lp = if (k.trail.isEmpty()) null else k.trail[k.trail.size - 1]
                if (lp == null || k.wasJump || abs(lp.x - k.x) + abs(lp.y - k.y) > 3.5f) {
                    k.trail.add(TrailPt(k.x, k.y, time, k.wasJump))
                    k.wasJump = false
                }
            } else k.wasJump = true
        }
    }

    /** A tiny wake that widens a little and dissolves within about a second. */
    private fun drawWake(c: Canvas, k: Kiter, wvx: Float, wvy: Float) {
        val n = k.trail.size
        if (n < 1 || k.a < 0.02f) return
        val life = 1.3f
        val wMax = 2.4f
        val aMax = k.a * 0.5f
        for (pass in 0 until 2) {
            for (q in 0 until n) {
                val pt = k.trail[q]
                val hasNext = q < n - 1
                val nx = if (hasNext) k.trail[q + 1].x else k.x
                val ny = if (hasNext) k.trail[q + 1].y else k.y
                if (hasNext && k.trail[q + 1].gap) continue
                val age = time - pt.t
                val u = clamp(age / life, 0f, 1f)
                val drift = age * 2.4f
                val nAge = if (hasNext) time - k.trail[q + 1].t else 0f
                val nDrift = nAge * 2.4f
                var al = aMax * (1f - u).pow(1.25f) * (0.8f + 0.2f * sin(time * 5f + q * 1.7f))
                var wd = 1.5f + (wMax - 1.5f) * u.pow(0.55f)
                if (pass == 0) { al *= 0.28f; wd *= 1.9f }
                if (al < 0.012f) continue
                stroke(argbF(al, 255, 255, 255), wd)
                c.drawLine(pt.x + wvx * drift, pt.y + wvy * drift, nx + wvx * nDrift, ny + wvy * nDrift, p)
            }
        }
    }

    private fun kitePath(sc: Float) {
        tmpPath.reset()
        tmpPath.moveTo(0f, -11f * sc)
        tmpPath.quadTo(10f * sc, 0f, 0f, 11f * sc)
        tmpPath.quadTo(3.6f * sc, 0f, 0f, -11f * sc)
        tmpPath.close()
    }

    private fun drawKiterRider(c: Canvas, k: Kiter) {
        if (k.a < 0.02f) return
        val j = if (k.jt >= 0f) sin(3.1415927f * k.jt) else 0f
        val sx = 2f + 8f * j
        val sy = 3f + 10f * j
        c.save()
        c.translate(k.x, k.y)
        c.save()
        c.translate(sx, sy); c.rotate(k.hd * DEG)
        solid(argbF((0.28f - 0.1f * j) * k.a, 0, 24, 34))
        c.drawOval(RectF(-5.2f, -1.8f, 5.2f, 1.8f), p)
        c.restore()
        c.save()
        c.rotate(k.hd * DEG)
        c.scale(1f + 0.5f * j, 1f + 0.5f * j)
        solid(argbF(k.a, 32, 40, 44)); c.drawOval(RectF(-4.8f, -1.55f, 4.8f, 1.55f), p)
        solid(Color.argb((k.a * 255f).toInt(), Color.red(k.col), Color.green(k.col), Color.blue(k.col)))
        c.drawOval(RectF(-3.2f, -0.8f, 3.2f, 0.8f), p)
        solid(argbF(k.a, 23, 37, 45)); c.drawOval(RectF(-0.2f - 1.3f, -2.4f, -0.2f + 1.3f, 2.4f), p)
        solid(argbF(k.a, 232, 180, 138)); c.drawCircle(0.2f, 0f, 1.4f, p)
        c.restore()
        c.restore()
    }

    private fun drawKite(c: Canvas, k: Kiter, ux: Float, uy: Float) {
        if (k.a < 0.02f) return
        val j = if (k.jt >= 0f) sin(3.1415927f * k.jt) else 0f
        val sway = sin(time * 1.3f + k.sway) * 0.12f
        val ang = atan2(uy, ux) + sway
        val dist = 27f * (1f - 0.32f * j)
        val sc = 1f + 0.26f * j
        val kx = k.x + cos(ang) * dist
        val ky = k.y + sin(ang) * dist
        val px = -sin(ang)
        val py = cos(ang)
        stroke(argbF(0.38f * k.a, 255, 255, 255), 0.6f)
        c.drawLine(k.x, k.y, kx + px * 9f * sc, ky + py * 9f * sc, p)
        c.drawLine(k.x, k.y, kx - px * 9f * sc, ky - py * 9f * sc, p)
        // shadow
        c.save()
        c.translate(kx + 4f + 5f * j, ky + 5f + 7f * j); c.rotate(ang * DEG)
        kitePath(sc); solid(argbF(0.22f * k.a, 0, 24, 34)); c.drawPath(tmpPath, p)
        c.restore()
        c.save()
        c.translate(kx, ky); c.rotate(ang * DEG)
        kitePath(sc)
        solid(Color.argb((k.a * 255f).toInt(), Color.red(k.col), Color.green(k.col), Color.blue(k.col)))
        c.drawPath(tmpPath, p)
        stroke(argbF(0.45f * k.a, 20, 24, 28), 0.7f)
        c.drawPath(tmpPath, p)
        stroke(argbF(0.4f * k.a, 20, 24, 28), 0.6f)
        c.drawLine(0f, 0f, 5.2f * sc, 0f, p)
        c.restore()
    }

    /* ---------- frame ---------- */

    fun render(canvas: Canvas, dtIn: Float) {
        val dt = min(dtIn, 0.05f)
        time += dt

        val ct = cloudTarget(st.weather)
        val rt = rainTarget(st.weather)
        val ft = fogTarget(st.weather)
        val sm = min(1f, dt * 2.2f)
        cloudA += (ct - cloudA) * sm
        rainA += (rt - rainA) * sm
        fogA += (ft - fogA) * sm

        val wvx = -sin(rad(st.windDir))
        val wvy = cos(rad(st.windDir))
        val perpX = -wvy
        val perpY = wvx
        val hN = clamp(st.swellH / 2.6f, 0f, 1f)
        val dvx = -sin(rad(st.swellDir))
        val shore = clamp(dvx + 0.12f, 0f, 1f)
        val tv = max(3f, st.swellT * 0.5f)
        updateSun()
        val kn = st.wind

        canvas.drawColor(Color.BLACK)
        val s = max(pxW / SCENE_W, pxH / SCENE_H)
        canvas.save()
        canvas.translate((pxW - SCENE_W * s) / 2f, (pxH - SCENE_H * s) / 2f)
        canvas.scale(s, s)
        val c = canvas

        c.drawBitmap(bgBitmap, null, sceneRect, bmpPaint)

        /* ---- water ---- */
        c.save()
        buildWaterPath()
        p.xfermode = null; p.pathEffect = null; p.style = Paint.Style.FILL
        p.shader = waterShader
        p.alpha = 255
        c.drawPath(waterPath, p)
        c.clipPath(waterPath)
        for (sh in shoals) blob(c, unitShoal, sh[0], sh[1], sh[2], sh[3], 0f, 1f)
        drawSwell(c, hN)
        drawPaws(c, wvx, wvy, dt)

        // sun glitter
        val gl = (1f - cloudA * 0.85f) * (1f - fogA) * (1f - rainA) * sunVis * 0.95f
        if (gl > 0.02f) {
            val sdx = sin(sunAz)
            val sdy = -cos(sunAz)
            for (sp in sparkles) {
                if (sp.x > wlX(sp.y, st.tide) - 6f) continue
                val dxs = sp.x - SCENE_W / 2f
                val dys = sp.y - SCENE_H / 2f
                val dl = max(0.001f, sqrt(dxs * dxs + dys * dys))
                val wgt = 0.35f + 0.65f * max(0f, (dxs * sdx + dys * sdy) / dl)
                val a = max(0f, sin(time * sp.f + sp.ph)).pow(10) * gl * wgt
                if (a < 0.04f) continue
                solid(argbF(a, 255, 244, 214))
                c.drawRect(sp.x - 0.8f, sp.y - 0.8f, sp.x + 0.8f, sp.y + 0.8f, p)
                if (a > 0.5f) {
                    c.drawRect(sp.x - 2.6f, sp.y - 0.3f, sp.x + 2.6f, sp.y + 0.3f, p)
                    c.drawRect(sp.x - 0.3f, sp.y - 2.6f, sp.x + 0.3f, sp.y + 2.6f, p)
                }
            }
        }
        // rain ripples
        if (rainA > 0.05f) {
            var nr = 230f * rainA * dt
            while (nr > 0f) {
                if (nr >= 1f || rnd.nextFloat() < nr) {
                    val yy = rnd.nextFloat() * SCENE_H
                    rings.add(Ring(rnd.nextFloat() * max(10f, wlX(yy, st.tide) - 8f), yy, 0f))
                }
                nr -= 1f
            }
            while (rings.size > 170) rings.removeAt(0)
        }
        var ri = rings.size - 1
        while (ri >= 0) {
            val rg = rings[ri]
            rg.age += dt
            if (rg.age > 0.75f) {
                rings.removeAt(ri)
            } else {
                val u = rg.age / 0.75f
                stroke(argbF(0.34f * (1f - u), 235, 248, 250), 0.8f)
                c.drawCircle(rg.x, rg.y, 1f + u * 9f, p)
            }
            ri -= 1
        }
        c.restore()

        /* ---- shore foam, wash and rock splashes ---- */
        val foamK = clamp(hN * 1.15f, 0f, 1f) * (0.25f + 0.75f * shore)
        if (foamK > 0.03f) {
            for (b in 0 until 3) {
                val ph = ((time / tv) + b / 3f) % 1f
                val d = 8f + (1f - ph) * (26f + foamK * 30f)
                stroke(argbF(foamK * 0.34f * sin(3.1415927f * ph), 255, 255, 255), 1.6f + foamK * 2f)
                p.pathEffect = android.graphics.DashPathEffect(floatArrayOf(16f, 10f, 6f, 12f), time * 9f + b * 7f)
                wlLine(tmpPath, d, 3f, b * 2f + time * 0.6f)
                c.drawPath(tmpPath, p)
            }
            p.pathEffect = null
            tmpPath.reset()
            tmpPath.moveTo(wlX(-10f, st.tide), -10f)
            var y = -10f
            while (y <= SCENE_H + 10f) {
                val wd = 2f + 13f * foamK * (0.5f + 0.5f * sin(TAU * time / tv - y * 0.02f + 1.3f * sin(y * 0.07f)))
                tmpPath.lineTo(wlX(y, st.tide) - wd, y)
                y += 5f
            }
            y = SCENE_H + 10f
            while (y >= -10f) { tmpPath.lineTo(wlX(y, st.tide), y); y -= 5f }
            tmpPath.close()
            solid(argbF(0.18f + 0.2f * foamK, 255, 255, 255))
            c.drawPath(tmpPath, p)
        }
        wlLine(tmpPath, 0f, 0f, 0f)
        stroke(argbF(0.22f, 190, 240, 230), 6f); c.drawPath(tmpPath, p)
        stroke(argbF(0.78f, 255, 255, 255), 1.5f); c.drawPath(tmpPath, p)
        for (rk in rocks) {
            val wlr = wlX(rk.y, st.tide)
            if (rk.x > wlr + rk.r) continue
            if (rk.r < 4.5f && wlr - rk.x > rk.r + 6f) continue
            val pulse = 0.5f + 0.5f * sin(TAU * time / tv + rk.s)
            val ra = (0.12f + 0.28f * pulse) * (0.3f + 0.7f * max(foamK, 0.12f))
            stroke(argbF(ra, 255, 255, 255), 1.3f)
            c.drawCircle(rk.x, rk.y, rk.r + 1.5f + pulse * 1.8f, p)
        }

        /* ---- wind flow lines ---- */
        if (kn > 2f) {
            val active = (flow.size * smooth(2f, 9f, kn)).roundToInt()
            val spd = 14f + kn * 5.5f
            val len = 5f + kn * 1.15f
            for (j in 0 until flow.size) {
                val pt = flow[j]
                pt.age += dt
                val swv = 0.4f * sin(pt.x * 0.011f + pt.y * 0.014f + time * 0.7f)
                val cx2 = wvx * cos(swv) - wvy * sin(swv)
                val cy2 = wvx * sin(swv) + wvy * cos(swv)
                pt.x += cx2 * spd * dt; pt.y += cy2 * spd * dt
                if (pt.age > pt.life || pt.x < -20f || pt.x > SCENE_W + 20f || pt.y < -20f || pt.y > SCENE_H + 20f) {
                    pt.x = rnd.nextFloat() * SCENE_W; pt.y = rnd.nextFloat() * SCENE_H
                    pt.age = 0f; pt.life = 1.4f + rnd.nextFloat() * 1.8f
                }
                if (j >= active) continue
                val fa = sin(3.1415927f * clamp(pt.age / pt.life, 0f, 1f)) * 0.3f
                stroke(argbF(fa, 255, 255, 255), 1f)
                c.drawLine(pt.x, pt.y, pt.x - cx2 * len, pt.y - cy2 * len, p)
            }
        }

        /* ---- figures (never more than five out at once) ---- */
        val cap = 5
        var nS = 0
        var nK = 0
        if (showSurf && showKite) {
            val tq = qSurf + qKite
            nS = clamp((cap * qSurf / (if (tq == 0f) 1f else tq)).roundToInt().toFloat(), 1f, (cap - 1).toFloat()).toInt()
            nK = cap - nS
        } else if (showSurf) nS = min(cap, (2f + 3f * qSurf).roundToInt())
        else if (showKite) nK = min(cap, (2f + 3f * qKite).roundToInt())

        for (j in 0 until surfers.size) updateSurfer(surfers[j], dt, showSurf && j < nS)
        for (j in 0 until kiters.size) updateKiter(kiters[j], dt, showKite && j < nK, perpX, perpY)
        for (k in kiters) drawWake(c, k, wvx, wvy)
        for (sf in surfers) drawSurfer(c, sf)
        for (k in kiters) drawKiterRider(c, k)
        for (k in kiters) drawKite(c, k, -wvx, -wvy)

        /* ---- cloud shadows and dull light ---- */
        if (cloudA > 0.02f) {
            val cs = 6f + kn * 0.8f
            for (cl in clouds) {
                cl.x += wvx * cs * dt; cl.y += wvy * cs * dt
                if (cl.x < -250f) cl.x += SCENE_W + 500f
                if (cl.x > SCENE_W + 250f) cl.x -= SCENE_W + 500f
                if (cl.y < -250f) cl.y += SCENE_H + 500f
                if (cl.y > SCENE_H + 250f) cl.y -= SCENE_H + 500f
                blob(c, unitCloud, cl.x, cl.y, cl.r, cl.r, 0f, 0.42f * cloudA * cl.k)
            }
            solid(argbF(0.1f * cloudA + 0.14f * rainA, 78, 92, 100))
            c.drawRect(-600f, -600f, SCENE_W + 600f, SCENE_H + 600f, p)
        }

        /* ---- time-of-day grade (multiply) ---- */
        val tn = tintAt(st.hour % 24f)
        p.shader = null; p.pathEffect = null; p.style = Paint.Style.FILL
        p.xfermode = multiply
        p.color = Color.rgb(tn[0].roundToInt(), tn[1].roundToInt(), tn[2].roundToInt())
        c.drawRect(-600f, -600f, SCENE_W + 600f, SCENE_H + 600f, p)
        p.xfermode = null

        /* ---- sun glare (screen) ---- */
        val glare = 0.3f * (1f - cloudA * 0.8f) * (1f - fogA * 0.6f) * sunVis * (0.35f + 0.65f * (1f - sunEl / 34f))
        if (glare > 0.01f) {
            val gx = SCENE_W / 2f + sin(sunAz) * SCENE_W * 0.95f
            val gy = SCENE_H / 2f - cos(sunAz) * SCENE_H * 0.78f
            p.style = Paint.Style.FILL
            p.shader = RadialGradient(
                gx, gy, SCENE_H * 0.95f,
                intArrayOf(argbF(glare, 255, 232, 185), argbF(glare * 0.35f, 255, 214, 160), argbF(0f, 255, 214, 160)),
                floatArrayOf(0f, 0.5f, 1f), Shader.TileMode.CLAMP
            )
            p.xfermode = screen
            p.alpha = 255
            c.drawRect(-600f, -600f, SCENE_W + 600f, SCENE_H + 600f, p)
            p.xfermode = null
            p.shader = null
        }

        /* ---- fog ---- */
        if (fogA > 0.02f) {
            solid(argbF(0.55f * fogA, 208, 224, 228))
            c.drawRect(-600f, -600f, SCENE_W + 600f, SCENE_H + 600f, p)
            for (m in mist) {
                m.x += (6f + kn * 0.6f) * (wvx * 0.6f + 0.2f) * dt
                if (m.x > SCENE_W + 200f) m.x = -200f
                blob(c, unitMist, m.x, m.y, m.r, m.r, 0f, 0.3f * fogA * (0.4f + m.k * 0.6f))
            }
        }

        /* ---- rain streaks ---- */
        if (rainA > 0.03f) {
            var rvx = wvx * 0.55f + 0.22f
            var rvy = wvy * 0.55f + 0.8f
            val rl = sqrt(rvx * rvx + rvy * rvy)
            rvx /= rl; rvy /= rl
            val na = min(drops.size, (drops.size * rainA).roundToInt())
            for (j in 0 until na) {
                val d2 = drops[j]
                d2.x += rvx * 520f * d2.sp * dt
                d2.y += rvy * 520f * d2.sp * dt
                if (d2.x > SCENE_W + 20f) d2.x -= SCENE_W + 40f
                if (d2.x < -20f) d2.x += SCENE_W + 40f
                if (d2.y > SCENE_H + 20f) d2.y -= SCENE_H + 40f
                dropLines[j * 4] = d2.x
                dropLines[j * 4 + 1] = d2.y
                dropLines[j * 4 + 2] = d2.x - rvx * 13f
                dropLines[j * 4 + 3] = d2.y - rvy * 13f
            }
            stroke(argbF(0.3f, 226, 240, 246), 0.9f)
            c.drawLines(dropLines, 0, na * 4, p)
        }

        /* ---- village lights at night ---- */
        val lum = (tn[0] + tn[1] + tn[2]) / 3f
        val night = clamp((235f - lum) / 125f, 0f, 1f)
        if (night > 0.05f) {
            for (cb in cabanons) {
                blob(c, unitLamp, cb[0] + 4.5f, cb[1] + 3f, 9f, 9f, 0f, 0.7f * night, additive)
            }
        }
        p.xfermode = null
        p.shader = null

        canvas.restore()
    }
}
