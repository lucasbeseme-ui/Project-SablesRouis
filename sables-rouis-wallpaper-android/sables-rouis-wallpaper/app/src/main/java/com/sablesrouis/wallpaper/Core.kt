package com.sablesrouis.wallpaper

import kotlin.math.abs
import kotlin.math.exp
import kotlin.math.sin

/*
 * Core maths, the geography of the beach and the "is it on?" engine.
 * North is up on screen, the sea is on the left, the land on the right.
 */

const val SCENE_W = 360f
const val SCENE_H = 808f
const val TAU = 6.2831855f

fun clamp(v: Float, a: Float, b: Float): Float = if (v < a) a else if (v > b) b else v
fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t
fun smooth(a: Float, b: Float, x: Float): Float {
    val t = clamp((x - a) / (b - a), 0f, 1f)
    return t * t * (3f - 2f * t)
}
fun rad(d: Float): Float = d * 0.017453292f
fun angDiff(a: Float, b: Float): Float {
    val d = (((a - b) % 360f) + 540f) % 360f - 180f
    return abs(d)
}

/** Small seeded random generator so the painted background is identical on every launch. */
class Mulberry(seed: Int) {
    private var a: Int = seed
    fun next(): Float {
        a += 0x6D2B79F5
        var t = a
        t = (t xor (t ushr 15)) * (1 or t)
        t = (t + ((t xor (t ushr 7)) * (61 or t))) xor t
        val r = t xor (t ushr 14)
        return (r.toLong() and 0xFFFFFFFFL).toFloat() / 4294967296f
    }
}

/* ---------- Geography ---------- */

fun gauss(y: Float, c: Float, s: Float): Float {
    val d = (y - c) / s
    return exp(-d * d)
}

fun cove(y: Float): Float {
    val d = (y - 425f) / 150f
    return exp(-d * d * d * d)
}

/** x of the waterline at mid tide. */
fun midX(y: Float): Float =
    214f +
        14f * sin(y * 0.0125f + 1.3f) + 8f * sin(y * 0.031f + 0.4f) + 3.5f * sin(y * 0.071f + 2.0f) -
        84f * gauss(y, 118f, 46f) - 96f * gauss(y, 708f, 52f) - 30f * gauss(y, 655f, 22f) +
        22f * cove(y)

fun tideR(y: Float): Float = 6f + 30f * cove(y)
fun duneX(y: Float): Float = midX(y) + 14f + 36f * cove(y) + 5f * sin(y * 0.045f + 0.7f)

/** x of the waterline for a tide position from 0 (low) to 100 (high). */
fun wlX(y: Float, tide: Float): Float = midX(y) + (tide - 50f) / 50f * tideR(y)

/* ---------- Conditions and the "is it on?" engine ---------- */

class Conditions(
    var wind: Float,
    var windDir: Float,
    var swellH: Float,
    var swellT: Float,
    var swellDir: Float,
    var tide: Float,
    var weather: String,
    var hour: Float
) {
    fun copyFrom(o: Conditions) {
        wind = o.wind; windDir = o.windDir; swellH = o.swellH; swellT = o.swellT
        swellDir = o.swellDir; tide = o.tide; weather = o.weather; hour = o.hour
    }
}

class Range(val lo: Float, val hi: Float)

class Profile(
    val tide: Range,
    val height: Range,
    val period: Range,
    val wind: Range,
    val angle: Range,
    val types: Set<String>
)

class EvalResult(val pass: Boolean, val q: Float)

fun windType(from: Float, facing: Float): String {
    val d = angDiff(from, facing)
    return if (d < 55f) "onshore" else if (d > 125f) "offshore" else "cross"
}

fun evaluateProfile(p: Profile, c: Conditions, facing: Float): EvalResult {
    var pass = true
    var sum = 0f
    var n = 0

    fun num(v: Float, r: Range) {
        val ok = v >= r.lo - 1e-4f && v <= r.hi + 1e-4f
        val half = (r.hi - r.lo) / 2f
        val cc = if (half <= 0f) 1f else 1f - abs(v - (r.lo + r.hi) / 2f) / half
        if (!ok) pass = false
        sum += if (ok) 0.5f + 0.5f * clamp(cc, 0f, 1f) else 0f
        n += 1
    }

    num(c.tide, p.tide)
    num(c.swellH, p.height)
    num(c.swellT, p.period)
    num(c.wind, p.wind)
    num(angDiff(c.swellDir, facing), p.angle)

    val typeOk = p.types.contains(windType(c.windDir, facing))
    if (!typeOk) pass = false
    sum += if (typeOk) 1f else 0f
    n += 1

    return EvalResult(pass, if (pass) sum / n.toFloat() else 0f)
}

/** Default ranges, the same as the mockup. Editable ranges come in a later version. */
val SURF_PROFILE = Profile(
    tide = Range(25f, 75f), height = Range(0.8f, 2.5f), period = Range(8f, 16f),
    wind = Range(0f, 15f), angle = Range(0f, 70f), types = setOf("offshore", "cross")
)
val KITE_PROFILE = Profile(
    tide = Range(0f, 100f), height = Range(0f, 2.0f), period = Range(4f, 18f),
    wind = Range(14f, 30f), angle = Range(0f, 90f), types = setOf("onshore", "cross")
)

/** Direction the beach faces, in degrees (270 = west). Assumed for Sables Rouis, to confirm on site. */
const val BEACH_FACING = 270f

/** Tide as a position in the day's range, a simple 12.42 h cycle. */
fun tideFromHour(h: Float): Float = 50f + 50f * kotlin.math.cos(TAU * (h - 3.0f) / 12.42f)

/* ---------- Scenarios ---------- */

class Scenario(val id: String, val name: String, val c: Conditions)

val SCENARIOS: List<Scenario> = listOf(
    Scenario("both", "Both on, golden hour", Conditions(15f, 5f, 1.3f, 10f, 270f, 50f, "clear", 17.5f)),
    Scenario("surf", "Clean surf day", Conditions(8f, 75f, 1.7f, 12f, 280f, 50f, "clear", 9.5f)),
    Scenario("kite", "Kite day", Conditions(21f, 250f, 1.1f, 7f, 265f, 65f, "cloudy", 15f)),
    Scenario("calm", "Flat calm", Conditions(3f, 90f, 0.2f, 5f, 270f, 40f, "clear", 11.5f)),
    Scenario("storm", "Rainy blow (empty sea)", Conditions(28f, 230f, 3.2f, 9f, 250f, 60f, "rain", 14f)),
    Scenario("fog", "Fog at dawn", Conditions(5f, 60f, 0.9f, 11f, 275f, 30f, "fog", 8.25f))
)

fun scenarioById(id: String?): Scenario {
    for (s in SCENARIOS) if (s.id == id) return s
    return SCENARIOS[0]
}

const val PREFS = "sables_prefs"
const val KEY_SCENARIO = "scenario"
const val KEY_LIGHT = "light"           // "scenario", "clock" or "demo"
