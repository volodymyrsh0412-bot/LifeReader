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

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.view.MotionEvent
import com.google.android.accessibility.utils.SharedPreferencesUtils
import kotlin.math.abs

/**
 * Nika: one-finger swipes from the top and bottom edges of the screen, like on an iPhone.
 * Bottom edge, up: short = Home, long = Recents. Top edge, down: short = Notifications, long =
 * Quick settings. While such a touch is in progress TalkBack does not explore or detect gestures.
 */
class NikaEdgeSwipe(
  private val service: AccessibilityService,
  private val context: Context,
) {
  private var active = false
  private var bottom = false
  private var startX = 0f
  private var startY = 0f
  private var lastX = 0f
  private var lastY = 0f

  private fun intPref(key: String, default: Int): Int {
    val prefs = SharedPreferencesUtils.getSharedPreferences(context)
    return prefs.getString(key, null)?.toIntOrNull() ?: default
  }

  private fun mmToPx(mm: Int): Float =
    mm * context.resources.displayMetrics.ydpi / 25.4f

  /** Returns true while this touch belongs to the edge swipe, so TalkBack must ignore it. */
  fun onMotionEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        active = false
        val prefs = SharedPreferencesUtils.getSharedPreferences(context)
        if (!prefs.getBoolean(PREF_ENABLED, false)) return false
        val height = context.resources.displayMetrics.heightPixels
        val band = mmToPx(intPref(PREF_BAND, 6))
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
    if (bottom) {
      val recents = mmToPx(intPref(PREF_RECENTS, 20))
      val home = mmToPx(intPref(PREF_HOME, 10))
      if (dy >= recents) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_RECENTS)
      } else if (dy >= home) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME)
      }
    } else {
      val qs = mmToPx(intPref(PREF_QS, 25))
      val notif = mmToPx(intPref(PREF_NOTIF, 10))
      if (dy >= qs) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_QUICK_SETTINGS)
      } else if (dy >= notif) {
        service.performGlobalAction(AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS)
      }
    }
  }

  companion object {
    const val PREF_ENABLED = "nika_edge_enabled"
    const val PREF_BAND = "nika_edge_band_mm"
    const val PREF_HOME = "nika_edge_home_mm"
    const val PREF_RECENTS = "nika_edge_recents_mm"
    const val PREF_NOTIF = "nika_edge_notif_mm"
    const val PREF_QS = "nika_edge_qs_mm"
  }
}
