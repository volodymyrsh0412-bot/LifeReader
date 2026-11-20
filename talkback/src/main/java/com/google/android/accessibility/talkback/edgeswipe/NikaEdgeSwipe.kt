/*
 * Copyright 2026 Nika contributors
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.google.android.accessibility.talkback.edgeswipe

import android.content.Context
import android.os.Build
import android.view.MotionEvent
import android.view.WindowManager
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.SharedPreferencesUtils
import kotlin.math.abs

/**
 * Nika: one-finger swipes from the bottom and top edges of the screen, like on an iPhone. Each of
 * the four swipes (bottom short/long, top short/long) runs any action of the gesture settings. While
 * such a touch is in progress TalkBack does not explore or detect gestures.
 */
class NikaEdgeSwipe(
  private val service: TalkBackService,
  private val context: Context,
) {
  private var active = false
  private var bottom = false
  private var startX = 0f
  private var startY = 0f
  private var lastX = 0f
  private var lastY = 0f

  private fun prefs() = SharedPreferencesUtils.getSharedPreferences(context)

  private fun intPref(key: String, default: Int): Int =
    prefs().getString(key, null)?.toIntOrNull() ?: default

  private fun actionPref(key: String, default: String): String =
    prefs().getString(key, null) ?: default

  /** Millimetres to pixels. The density is used because the reported physical dpi is often wrong. */
  private fun mmToPx(mm: Int): Float = mm * context.resources.displayMetrics.densityDpi / 25.4f

  /** The full height of the screen, with the navigation bar. */
  private fun screenHeight(): Int {
    try {
      if (Build.VERSION.SDK_INT >= 30) {
        val wm = context.getSystemService(WindowManager::class.java)
        val bounds = wm?.maximumWindowMetrics?.bounds
        if (bounds != null && bounds.height() > 0) {
          return bounds.height()
        }
      }
    } catch (e: RuntimeException) {
      // Falls back to the display metrics.
    }
    return context.resources.displayMetrics.heightPixels
  }

  /** Returns true while this touch belongs to the edge swipe, so TalkBack must ignore it. */
  fun onMotionEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        active = false
        if (!prefs().getBoolean(PREF_ENABLED, false)) return false
        val height = screenHeight()
        val band = mmToPx(intPref(PREF_BAND, 10))
        val y = event.rawY
        if (y >= height - band) {
          bottom = true
        } else if (y <= band) {
          bottom = false
        } else {
          return false
        }
        active = true
        startX = event.rawX
        startY = y
        lastX = startX
        lastY = startY
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (active) {
          lastX = event.rawX
          lastY = event.rawY
        }
        return active
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        // A second finger: this is not an edge swipe any more, but keep ignoring the touch.
        if (active) {
          active = false
          return true
        }
        return false
      }
      MotionEvent.ACTION_UP -> {
        if (!active) return false
        active = false
        lastX = event.rawX
        lastY = event.rawY
        finish()
        return true
      }
      MotionEvent.ACTION_CANCEL -> {
        val was = active
        active = false
        return was
      }
      else -> return active
    }
  }

  private fun finish() {
    val dx = abs(lastX - startX)
    val dy = if (bottom) startY - lastY else lastY - startY
    if (dy <= dx) return
    val action: String
    if (bottom) {
      val long = mmToPx(intPref(PREF_B_LONG_LEN, 20))
      val short = mmToPx(intPref(PREF_B_SHORT_LEN, 10))
      action =
        if (dy >= long) actionPref(PREF_B_LONG_ACT, "OVERVIEW")
        else if (dy >= short) actionPref(PREF_B_SHORT_ACT, "HOME")
        else return
    } else {
      val long = mmToPx(intPref(PREF_T_LONG_LEN, 25))
      val short = mmToPx(intPref(PREF_T_SHORT_LEN, 10))
      action =
        if (dy >= long) actionPref(PREF_T_LONG_ACT, "QUICK_SETTINGS")
        else if (dy >= short) actionPref(PREF_T_SHORT_ACT, "NOTIFICATIONS")
        else return
    }
    service.performNikaKeyAction(action)
  }

  companion object {
    const val PREF_ENABLED = "nika_edge_enabled"
    const val PREF_BAND = "nika_edge_band"
    const val PREF_B_SHORT_ACT = "nika_edge_b_short_act"
    const val PREF_B_SHORT_LEN = "nika_edge_b_short_len"
    const val PREF_B_LONG_ACT = "nika_edge_b_long_act"
    const val PREF_B_LONG_LEN = "nika_edge_b_long_len"
    const val PREF_T_SHORT_ACT = "nika_edge_t_short_act"
    const val PREF_T_SHORT_LEN = "nika_edge_t_short_len"
    const val PREF_T_LONG_ACT = "nika_edge_t_long_act"
    const val PREF_T_LONG_LEN = "nika_edge_t_long_len"
  }
}
