package com.sablesrouis.wallpaper

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Canvas
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.service.wallpaper.WallpaperService
import android.view.SurfaceHolder
import java.util.Calendar

/**
 * The live wallpaper. Drawing happens on its own thread, only while the wallpaper is visible,
 * at about 25 frames per second. When the screen is off or an app covers it, nothing runs.
 */
class SablesWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = SablesEngine()

    inner class SablesEngine : Engine() {

        private val frameMs = 40L
        private var thread: HandlerThread? = null
        private var handler: Handler? = null

        @Volatile private var scene: Scene? = null
        @Volatile private var visible = false

        private var lastNanos = 0L
        private var appliedScenario: String? = null
        private var demoHour = 8f
        private val prefs: SharedPreferences by lazy {
            applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        }

        private val frameRunnable = object : Runnable {
            override fun run() {
                drawFrame()
                if (visible) handler?.postDelayed(this, frameMs)
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            val t = HandlerThread("sables-render")
            t.start()
            thread = t
            handler = Handler(t.looper)
        }

        private var builtW = 0
        private var builtH = 0

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            handler?.removeCallbacks(frameRunnable)
            handler?.post {
                // The painted background is only rebuilt when the screen size really changes.
                if (scene == null || width != builtW || height != builtH) {
                    scene = Scene(width, height)
                    builtW = width
                    builtH = height
                    appliedScenario = null
                }
                lastNanos = 0L
                if (visible) handler?.post(frameRunnable)
            }
        }

        override fun onVisibilityChanged(isVisible: Boolean) {
            visible = isVisible
            handler?.removeCallbacks(frameRunnable)
            if (isVisible) {
                lastNanos = 0L
                handler?.post(frameRunnable)
            }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            visible = false
            handler?.removeCallbacks(frameRunnable)
            super.onSurfaceDestroyed(holder)
        }

        override fun onDestroy() {
            visible = false
            handler?.removeCallbacksAndMessages(null)
            thread?.quitSafely()
            thread = null
            handler = null
            scene = null
            super.onDestroy()
        }

        private fun drawFrame() {
            val sc = scene ?: return
            val holder = surfaceHolder ?: return
            val now = System.nanoTime()
            val dt = if (lastNanos == 0L) 0.04f else (now - lastNanos) / 1_000_000_000f
            lastNanos = now

            applySettings(sc, dt)

            var canvas: Canvas? = null
            try {
                canvas = if (Build.VERSION.SDK_INT >= 26) holder.lockHardwareCanvas() else holder.lockCanvas()
                if (canvas != null) sc.render(canvas, dt)
            } catch (e: Exception) {
                // a dropped frame is fine
            } finally {
                if (canvas != null) {
                    try { holder.unlockCanvasAndPost(canvas) } catch (e: Exception) { }
                }
            }
        }

        /** Reads the chosen scenario and light mode, and decides which sports are "on". */
        private fun applySettings(sc: Scene, dt: Float) {
            val scenarioId = prefs.getString(KEY_SCENARIO, "both")
            if (scenarioId != appliedScenario) {
                sc.st.copyFrom(scenarioById(scenarioId).c)
                appliedScenario = scenarioId
            }
            val base = scenarioById(scenarioId).c
            when (prefs.getString(KEY_LIGHT, "scenario")) {
                "clock" -> {
                    val cal = Calendar.getInstance()
                    val h = cal.get(Calendar.HOUR_OF_DAY) + cal.get(Calendar.MINUTE) / 60f
                    sc.st.hour = h
                    sc.st.tide = tideFromHour(h)
                }
                "demo" -> {
                    demoHour = (demoHour + dt * 1.3f) % 24f
                    sc.st.hour = demoHour
                    sc.st.tide = tideFromHour(demoHour)
                }
                else -> {
                    sc.st.hour = base.hour
                    sc.st.tide = base.tide
                }
            }
            val surf = evaluateProfile(SURF_PROFILE, sc.st, BEACH_FACING)
            val kite = evaluateProfile(KITE_PROFILE, sc.st, BEACH_FACING)
            sc.showSurf = surf.pass
            sc.showKite = kite.pass
            sc.qSurf = surf.q
            sc.qKite = kite.q
        }
    }
}
