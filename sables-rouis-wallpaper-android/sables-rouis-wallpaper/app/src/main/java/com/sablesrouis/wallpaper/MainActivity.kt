package com.sablesrouis.wallpaper

import android.app.Activity
import android.app.WallpaperManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.ScrollView
import android.widget.TextView

/** One simple screen: pick a scenario, pick how the light behaves, then set the wallpaper. */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val dp = resources.displayMetrics.density
        val pad = (20 * dp).toInt()

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(pad, (64 * dp).toInt(), pad, pad * 2)

        val title = TextView(this)
        title.text = "Sables Rouis"
        title.textSize = 28f
        root.addView(title)

        val sub = TextView(this)
        sub.text = "Île d'Yeu live wallpaper. Version 0.1 with simulated conditions."
        sub.setPadding(0, (4 * dp).toInt(), 0, (20 * dp).toInt())
        root.addView(sub)

        val setBtn = Button(this)
        setBtn.text = "Set as live wallpaper"
        setBtn.setOnClickListener { openWallpaperPicker() }
        root.addView(setBtn)

        val h1 = TextView(this)
        h1.text = "Scenario"
        h1.textSize = 18f
        h1.setPadding(0, (24 * dp).toInt(), 0, (6 * dp).toInt())
        root.addView(h1)

        val scenarioGroup = RadioGroup(this)
        val savedScenario = prefs.getString(KEY_SCENARIO, "both")
        for (s in SCENARIOS) {
            val rb = RadioButton(this)
            rb.id = View.generateViewId()
            rb.text = s.name
            rb.tag = s.id
            scenarioGroup.addView(rb)
            if (s.id == savedScenario) rb.isChecked = true
        }
        scenarioGroup.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId)
            val id = rb?.tag as? String
            if (id != null) prefs.edit().putString(KEY_SCENARIO, id).apply()
        }
        root.addView(scenarioGroup)

        val h2 = TextView(this)
        h2.text = "Light and tide"
        h2.textSize = 18f
        h2.setPadding(0, (24 * dp).toInt(), 0, (6 * dp).toInt())
        root.addView(h2)

        val lightGroup = RadioGroup(this)
        val lightOptions = arrayOf(
            arrayOf("scenario", "Use the scenario's time of day"),
            arrayOf("clock", "Follow the real clock"),
            arrayOf("demo", "Fast day cycle (demo)")
        )
        val savedLight = prefs.getString(KEY_LIGHT, "scenario")
        for (o in lightOptions) {
            val rb = RadioButton(this)
            rb.id = View.generateViewId()
            rb.text = o[1]
            rb.tag = o[0]
            lightGroup.addView(rb)
            if (o[0] == savedLight) rb.isChecked = true
        }
        lightGroup.setOnCheckedChangeListener { group, checkedId ->
            val rb = group.findViewById<RadioButton>(checkedId)
            val id = rb?.tag as? String
            if (id != null) prefs.edit().putString(KEY_LIGHT, id).apply()
        }
        root.addView(lightGroup)

        val note = TextView(this)
        note.text = "Changes apply to the wallpaper right away. Surfers and kitesurfers only appear when the scenario matches the default Surf and Kite ranges."
        note.setPadding(0, (24 * dp).toInt(), 0, 0)
        root.addView(note)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)
    }

    private fun openWallpaperPicker() {
        try {
            val i = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER)
            i.putExtra(
                WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
                ComponentName(this, SablesWallpaperService::class.java)
            )
            startActivity(i)
        } catch (e: Exception) {
            try {
                startActivity(Intent(WallpaperManager.ACTION_LIVE_WALLPAPER_CHOOSER))
            } catch (e2: Exception) {
                // nothing else to try
            }
        }
    }
}
