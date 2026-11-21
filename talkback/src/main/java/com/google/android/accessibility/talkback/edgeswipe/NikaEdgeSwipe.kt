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
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.view.MotionEvent
import android.view.WindowManager
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.talkback.gesture.GestureShortcutMapping
import com.google.android.accessibility.utils.Performance
import com.google.android.accessibility.utils.SharedPreferencesUtils
import com.google.android.accessibility.utils.output.FeedbackItem
import com.google.android.accessibility.utils.output.SpeechController
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.sin

/**
 * Nika: one-finger swipes from the four edges of the screen, like on an iPhone. A swipe has a short
 * and a long length, and each of them runs any action of the gesture settings. While the finger
 * moves, rising sounds and the spoken name of the action tell what will happen on release. While
 * such a touch is in progress TalkBack does not explore or detect gestures.
 *
 * The side edges are split into an upper and a lower half of the screen.
 */
class NikaEdgeSwipe(
  private val service: TalkBackService,
  private val context: Context,
) {
  private enum class Edge {
    BOTTOM,
    TOP,
    LEFT,
    RIGHT,
  }

  private val handler = Handler(Looper.getMainLooper())
  private var active = false
  private var candidates = emptySet<Edge>()
  private var startX = 0f
  private var startY = 0f
  private var screenW = 0
  private var screenH = 0
  private var upperHalf = false
  private var edge: Edge? = null
  private var level = 0
  private var ticks = 0
  private var speakOn = true
  private var soundOn = true

  private fun prefs() = SharedPreferencesUtils.getSharedPreferences(context)

  private fun intPref(key: String, default: Int): Int =
    prefs().getString(key, null)?.toIntOrNull() ?: default

  private fun actionPref(key: String, default: String): String =
    prefs().getString(key, null) ?: default

  /** Millimetres to pixels. The density is used because the reported physical dpi is often wrong. */
  private fun mmToPx(mm: Int): Float = mm * context.resources.displayMetrics.densityDpi / 25.4f

  private fun fullScreenSize() {
    val metrics = context.resources.displayMetrics
    screenW = metrics.widthPixels
    screenH = metrics.heightPixels
    try {
      if (Build.VERSION.SDK_INT >= 30) {
        val bounds =
          context.getSystemService(WindowManager::class.java)?.maximumWindowMetrics?.bounds
        if (bounds != null && bounds.height() > 0 && bounds.width() > 0) {
          screenW = bounds.width()
          screenH = bounds.height()
        }
      }
    } catch (e: RuntimeException) {
      // Falls back to the display metrics.
    }
  }

  /** Returns true while this touch belongs to the edge swipe, so TalkBack must ignore it. */
  fun onMotionEvent(event: MotionEvent): Boolean {
    when (event.actionMasked) {
      MotionEvent.ACTION_DOWN -> {
        active = false
        if (!prefs().getBoolean(PREF_ENABLED, false)) return false
        fullScreenSize()
        val band = mmToPx(intPref(PREF_BAND, 10))
        val x = event.rawX
        val y = event.rawY
        val found = HashSet<Edge>()
        if (y >= screenH - band) found.add(Edge.BOTTOM)
        if (y <= band) found.add(Edge.TOP)
        if (x <= band) found.add(Edge.LEFT)
        if (x >= screenW - band) found.add(Edge.RIGHT)
        if (found.isEmpty()) return false
        candidates = found
        active = true
        startX = x
        startY = y
        upperHalf = y < screenH / 2f
        edge = null
        level = 0
        ticks = 0
        speakOn = prefs().getBoolean(PREF_SPEAK, true)
        soundOn = prefs().getBoolean(PREF_SOUND, true)
        if (soundOn) beep(400, 35)
        return true
      }
      MotionEvent.ACTION_MOVE -> {
        if (active) update(event.rawX, event.rawY)
        return active
      }
      MotionEvent.ACTION_POINTER_DOWN -> {
        // A second finger: this is not an edge swipe any more, but the touch is still ignored.
        val was = active
        active = false
        return was
      }
      MotionEvent.ACTION_UP -> {
        if (!active) return false
        active = false
        update(event.rawX, event.rawY)
        val action = currentAction()
        if (action != null) {
          service.performNikaKeyAction(action)
        }
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

  /** How far the finger has gone from the edge into the screen, or 0 if it is going another way. */
  private fun progress(e: Edge, x: Float, y: Float): Float {
    val dx = x - startX
    val dy = y - startY
    return when (e) {
      Edge.BOTTOM -> if (-dy > abs(dx)) -dy else 0f
      Edge.TOP -> if (dy > abs(dx)) dy else 0f
      Edge.LEFT -> if (dx > abs(dy)) dx else 0f
      Edge.RIGHT -> if (-dx > abs(dy)) -dx else 0f
    }
  }

  private fun lengthMm(e: Edge, long: Boolean): Int =
    when (e) {
      Edge.BOTTOM -> if (long) intPref(PREF_B_LONG_LEN, 20) else intPref(PREF_B_SHORT_LEN, 10)
      Edge.TOP -> if (long) intPref(PREF_T_LONG_LEN, 25) else intPref(PREF_T_SHORT_LEN, 10)
      else -> if (long) intPref(PREF_SIDE_LONG_LEN, 25) else intPref(PREF_SIDE_SHORT_LEN, 10)
    }

  private fun actionFor(e: Edge, long: Boolean): String =
    when (e) {
      Edge.BOTTOM ->
        if (long) actionPref(PREF_B_LONG_ACT, "OVERVIEW") else actionPref(PREF_B_SHORT_ACT, "HOME")
      Edge.TOP ->
        if (long) actionPref(PREF_T_LONG_ACT, "QUICK_SETTINGS")
        else actionPref(PREF_T_SHORT_ACT, "NOTIFICATIONS")
      else -> {
        val side = if (e == Edge.LEFT) "l" else "r"
        val half = if (upperHalf) "u" else "d"
        val type = if (long) "long" else "short"
        val default = if (e == Edge.LEFT && !upperHalf && !long) "BACK" else UNASSIGNED
        actionPref("nika_edge___act", default)
      }
    }

  /** The action that a release now would run, or null. */
  private fun currentAction(): String? {
    val e = edge ?: return null
    if (level == 0) return null
    val action = actionFor(e, level == 2)
    return if (action == UNASSIGNED) null else action
  }

  private fun update(x: Float, y: Float) {
    var best: Edge? = null
    var bestProgress = 0f
    for (c in candidates) {
      val p = progress(c, x, y)
      if (p > bestProgress) {
        best = c
        bestProgress = p
      }
    }
    var newLevel = 0
    if (best != null) {
      val longOk =
        actionFor(best, true) != UNASSIGNED && bestProgress >= mmToPx(lengthMm(best, true))
      val shortOk =
        actionFor(best, false) != UNASSIGNED && bestProgress >= mmToPx(lengthMm(best, false))
      newLevel = if (longOk) 2 else if (shortOk) 1 else 0
    }
    // Rising ticks while the finger goes further, one every 5 mm.
    if (soundOn && best != null) {
      val k = (bestProgress / mmToPx(5)).toInt()
      if (k > ticks) {
        ticks = k
        if (newLevel == level) beep(450 + 60 * minOf(k, 24), 30)
      }
    }
    if (best != edge || newLevel != level) {
      val changedUp = newLevel > level
      edge = best
      level = newLevel
      if (level > 0 && best != null) {
        if (soundOn) beep(if (changedUp) 900 + 250 * level else 700, 90)
        if (speakOn) {
          val name = GestureShortcutMapping.getActionString(context, actionFor(best, level == 2))
          service
            .speechController
            .speak(
              name,
              SpeechController.QUEUE_MODE_FLUSH_ALL,
              FeedbackItem.FLAG_NO_HISTORY or FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
              null,
              Performance.EVENT_ID_UNTRACKED,
            )
        }
      }
    }
  }

  /** Plays a short sine tone. */
  private fun beep(freq: Int, ms: Int) {
    try {
      val rate = 22050
      val n = rate * ms / 1000
      val data = ShortArray(n)
      for (i in 0 until n) {
        val fade = minOf(1f, minOf(i, n - 1 - i) / (rate * 0.005f))
        data[i] = (sin(2.0 * PI * freq * i / rate) * 9000 * fade).toInt().toShort()
      }
      val track =
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(rate)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(n * 2)
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()
      track.write(data, 0, n)
      track.play()
      handler.postDelayed({ track.release() }, ms + 150L)
    } catch (e: RuntimeException) {
      // No sound is not worth a crash.
    }
  }

  companion object {
    const val UNASSIGNED = "UNASSIGNED"
    const val PREF_ENABLED = "nika_edge_enabled"
    const val PREF_SPEAK = "nika_edge_speak"
    const val PREF_SOUND = "nika_edge_sound"
    const val PREF_BAND = "nika_edge_band"
    const val PREF_B_SHORT_ACT = "nika_edge_b_short_act"
    const val PREF_B_SHORT_LEN = "nika_edge_b_short_len"
    const val PREF_B_LONG_ACT = "nika_edge_b_long_act"
    const val PREF_B_LONG_LEN = "nika_edge_b_long_len"
    const val PREF_T_SHORT_ACT = "nika_edge_t_short_act"
    const val PREF_T_SHORT_LEN = "nika_edge_t_short_len"
    const val PREF_T_LONG_ACT = "nika_edge_t_long_act"
    const val PREF_T_LONG_LEN = "nika_edge_t_long_len"
    const val PREF_SIDE_SHORT_LEN = "nika_edge_side_short_len"
    const val PREF_SIDE_LONG_LEN = "nika_edge_side_long_len"
  }
}
