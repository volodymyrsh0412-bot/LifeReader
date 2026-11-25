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

package com.google.android.accessibility.talkback.keyactions

import android.app.KeyguardManager
import android.content.Context
import android.content.SharedPreferences
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.KeyEvent
import android.widget.Toast
import androidx.preference.PreferenceManager
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.SharedPreferencesUtils

/**
 * Nika: runs a gesture action when a key is pressed, held, pressed twice or pressed three times.
 * The keys and their actions are set in the key settings. A key with no action works as usual.
 * With the diagnostics switch on, a message tells what happened with every key that reaches this
 * code.
 */
class NikaKeyActions(private val service: TalkBackService) {
  companion object {
    private const val TAG = "NikaKeys"

    /** True while the settings wait for the user to press a key to assign, so keys pass through. */
    @JvmField @Volatile var capturing = false

    const val UNASSIGNED = "UNASSIGNED"
    const val PREF_ENABLED = "nika_keys_enabled"
    const val PREF_ARROWS = "nika_keys_arrows"
    const val PREF_SKIP_EDIT = "nika_keys_skip_edit"
    const val PREF_HOLD_MS = "nika_keys_hold_ms"
    const val PREF_MULTI_MS = "nika_keys_multi_ms"
    const val PREF_DEBUG = "nika_keys_debug"
    val TYPES = arrayOf("tap1", "hold", "tap2", "tap3")

    fun customCodeKey(n: Int) = "nika_key_c${n}_code"

    fun actionKey(slot: String, type: String) = "nika_key_${slot}_$type"
  }

  private class State {
    var down = false
    var held = false
    var taps = 0
    var holdTask: Runnable? = null
    var tapTask: Runnable? = null
  }

  private val handler = Handler(Looper.getMainLooper())
  private val states = HashMap<Int, State>()

  /** The settings of the screens may be stored in the device or in the user storage. */
  private fun userPrefs(): SharedPreferences? =
    try {
      PreferenceManager.getDefaultSharedPreferences(service)
    } catch (e: RuntimeException) {
      null
    }

  private fun readAction(prefs: SharedPreferences, slot: String, type: String): String {
    val key = actionKey(slot, type)
    val first = prefs.getString(key, null)
    if (first != null && first != UNASSIGNED) return first
    val second = userPrefs()?.getString(key, null)
    return if (second != null && second != UNASSIGNED) second else first ?: UNASSIGNED
  }

  private fun slotFor(prefs: SharedPreferences, code: Int): String? {
    for (n in 1..4) {
      if (prefs.getInt(customCodeKey(n), 0) == code) {
        return "c$n"
      }
    }
    return when (code) {
      KeyEvent.KEYCODE_VOLUME_UP -> "vol_up"
      KeyEvent.KEYCODE_VOLUME_DOWN -> "vol_down"
      in KeyEvent.KEYCODE_0..KeyEvent.KEYCODE_9 -> "d${code - KeyEvent.KEYCODE_0}"
      in KeyEvent.KEYCODE_NUMPAD_0..KeyEvent.KEYCODE_NUMPAD_9 -> "d${code - KeyEvent.KEYCODE_NUMPAD_0}"
      KeyEvent.KEYCODE_STAR,
      KeyEvent.KEYCODE_NUMPAD_MULTIPLY -> "star"
      KeyEvent.KEYCODE_POUND -> "pound"
      KeyEvent.KEYCODE_DPAD_UP -> "up"
      KeyEvent.KEYCODE_DPAD_DOWN -> "down"
      KeyEvent.KEYCODE_DPAD_LEFT -> "left"
      KeyEvent.KEYCODE_DPAD_RIGHT -> "right"
      else -> null
    }
  }

  private fun isLocked(): Boolean =
    try {
      (service.getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager).isKeyguardLocked
    } catch (e: RuntimeException) {
      false
    }

  /** The actions of a key. On the lock screen the volume keys use their own actions, if any. */
  private fun actionsFor(prefs: SharedPreferences, slot: String, isVolume: Boolean): Array<String> {
    val normal = Array(4) { readAction(prefs, slot, TYPES[it]) }
    if (!isVolume || !isLocked()) return normal
    val locked = Array(4) { readAction(prefs, slot + "_lock", TYPES[it]) }
    return if (locked.any { it != UNASSIGNED }) locked else normal
  }

  /** Returns true if the key event was used. */
  fun onKeyEvent(event: KeyEvent, inEditField: Boolean): Boolean {
    val prefs = SharedPreferencesUtils.getSharedPreferences(service)
    val note = StringBuilder()
    val used = handle(event, inEditField, prefs, note)
    if (
      event.action == KeyEvent.ACTION_DOWN &&
        event.repeatCount == 0 &&
        (prefs.getBoolean(PREF_DEBUG, false) || userPrefs()?.getBoolean(PREF_DEBUG, false) == true)
    ) {
      val text =
        KeyEvent.keyCodeToString(event.keyCode).removePrefix("KEYCODE_") +
          ": " +
          (if (used) "used" else "passed on") +
          " (" +
          note +
          ")"
      Log.d(TAG, text)
      Toast.makeText(service, text, Toast.LENGTH_SHORT).show()
    }
    return used
  }

  private fun handle(
    event: KeyEvent,
    inEditField: Boolean,
    prefs: SharedPreferences,
    note: StringBuilder,
  ): Boolean {
    if (capturing) {
      note.append("waiting for a key to assign")
      return false
    }
    val code = event.keyCode
    // The volume keys do not depend on the common switch: their actions are set on their own screen.
    val isVolume = code == KeyEvent.KEYCODE_VOLUME_UP || code == KeyEvent.KEYCODE_VOLUME_DOWN
    if (!isVolume && !prefs.getBoolean(PREF_ENABLED, true)) {
      note.append("key actions are off")
      return false
    }
    val slot = slotFor(prefs, code)
    if (slot == null) {
      note.append("no settings for this key")
      return false
    }
    note.append(slot)
    val isArrow = code in KeyEvent.KEYCODE_DPAD_UP..KeyEvent.KEYCODE_DPAD_RIGHT
    if (isArrow && !prefs.getBoolean(PREF_ARROWS, false)) {
      note.append(", arrows are off")
      return false
    }
    if (!isVolume) {
      if (inEditField && prefs.getBoolean(PREF_SKIP_EDIT, true)) {
        note.append(", text field")
        return false
      }
      if (event.isShiftPressed) {
        note.append(", Shift")
        return false
      }
      if (event.isCtrlPressed || event.isAltPressed || event.isMetaPressed) {
        note.append(", modifier key")
        return false
      }
    }
    val actions = actionsFor(prefs, slot, isVolume)
    val hasTap1 = actions[0] != UNASSIGNED
    val hasHold = actions[1] != UNASSIGNED
    val hasTap2 = actions[2] != UNASSIGNED
    val hasTap3 = actions[3] != UNASSIGNED
    note.append(": ").append(actions.joinToString(" / "))
    if (!hasTap1 && !hasHold && !hasTap2 && !hasTap3) {
      return false
    }
    val holdMs = prefs.getString(PREF_HOLD_MS, "500")?.toLongOrNull() ?: 500L
    val multiMs = prefs.getString(PREF_MULTI_MS, "350")?.toLongOrNull() ?: 350L
    val state = states.getOrPut(code) { State() }

    if (event.action == KeyEvent.ACTION_DOWN) {
      if (event.repeatCount > 0) {
        return true
      }
      state.down = true
      state.held = false
      state.tapTask?.let { handler.removeCallbacks(it) }
      state.tapTask = null
      state.holdTask?.let { handler.removeCallbacks(it) }
      state.holdTask = null
      if (hasHold) {
        val task = Runnable {
          if (state.down) {
            state.held = true
            state.taps = 0
            perform(actions[1])
          }
        }
        state.holdTask = task
        handler.postDelayed(task, holdMs)
      }
      return true
    }
    if (event.action == KeyEvent.ACTION_UP) {
      state.holdTask?.let { handler.removeCallbacks(it) }
      state.holdTask = null
      if (!state.down) {
        return true
      }
      state.down = false
      if (state.held) {
        state.held = false
        return true
      }
      state.taps++
      val maxTaps =
        when {
          hasTap3 -> 3
          hasTap2 -> 2
          else -> 1
        }
      if (state.taps >= maxTaps) {
        fire(state, actions, code)
      } else {
        val task = Runnable { fire(state, actions, code) }
        state.tapTask = task
        handler.postDelayed(task, multiMs)
      }
      return true
    }
    return false
  }

  private fun fire(state: State, actions: Array<String>, code: Int) {
    val count = state.taps.coerceIn(1, 3)
    state.taps = 0
    state.tapTask = null
    val action =
      when (count) {
        1 -> actions[0]
        2 -> actions[2]
        else -> actions[3]
      }
    if (action != UNASSIGNED) {
      perform(action)
    } else if (count == 1) {
      fallback(code)
    }
  }

  private fun perform(action: String) {
    service.performNikaKeyAction(action)
  }

  /** A short press of a volume key with no action of its own still changes the volume. */
  private fun fallback(code: Int) {
    val direction =
      when (code) {
        KeyEvent.KEYCODE_VOLUME_UP -> AudioManager.ADJUST_RAISE
        KeyEvent.KEYCODE_VOLUME_DOWN -> AudioManager.ADJUST_LOWER
        else -> return
      }
    service
      .getSystemService(AudioManager::class.java)
      ?.adjustSuggestedStreamVolume(
        direction,
        AudioManager.USE_DEFAULT_STREAM_TYPE,
        if (com.google.android.accessibility.utils.SharedPreferencesUtils.getSharedPreferences(service)
            .getBoolean("nika_vol_hide_panel", false)
        ) 0 else AudioManager.FLAG_SHOW_UI,
      )
  }
}
