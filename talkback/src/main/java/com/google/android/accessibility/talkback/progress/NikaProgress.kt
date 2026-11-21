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

package com.google.android.accessibility.talkback.progress

import android.content.Context
import android.content.SharedPreferences
import androidx.core.view.accessibility.AccessibilityNodeInfoCompat
import androidx.preference.PreferenceManager
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.AccessibilityNodeInfoUtils
import com.google.android.accessibility.utils.Role
import com.google.android.accessibility.utils.SharedPreferencesUtils
import java.util.Optional
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * Nika: how progress indicators are announced. The mode chooses voice, sound (the rising tone that
 * the screen reader already plays), both, or nothing. The voice can say the value in percent, and
 * only every few percent.
 */
object NikaProgress {

  private const val PREF_MODE = "nika_progress_mode"
  private const val PREF_PERCENT = "nika_progress_percent"
  private const val PREF_STEP = "nika_progress_step"

  private var lastSpoken = -1000

  private fun stores(context: Context): List<SharedPreferences> {
    val list = ArrayList<SharedPreferences>(2)
    try {
      list.add(PreferenceManager.getDefaultSharedPreferences(context))
    } catch (e: RuntimeException) {
      // skip
    }
    try {
      val other = SharedPreferencesUtils.getSharedPreferences(context)
      if (list.none { it === other }) list.add(other)
    } catch (e: RuntimeException) {
      // skip
    }
    return list
  }

  private fun string(key: String, default: String): String {
    val context = TalkBackService.getInstance() ?: return default
    for (store in stores(context)) {
      if (store.contains(key)) {
        return try {
          store.getString(key, default) ?: default
        } catch (e: ClassCastException) {
          default
        }
      }
    }
    return default
  }

  private fun bool(key: String, default: Boolean): Boolean {
    val context = TalkBackService.getInstance() ?: return default
    for (store in stores(context)) {
      if (store.contains(key)) {
        return try {
          store.getBoolean(key, default)
        } catch (e: ClassCastException) {
          default
        }
      }
    }
    return default
  }

  private fun mode(): String = string(PREF_MODE, "both")

  /** Whether the progress tone may play. */
  @JvmStatic
  fun soundOn(): Boolean {
    val m = mode()
    return m == "both" || m == "sound"
  }

  /** The spoken text for a progress indicator; other nodes keep their text. */
  @JvmStatic
  fun speech(node: AccessibilityNodeInfoCompat?, text: CharSequence?): CharSequence? {
    if (node == null || Role.getRole(node) != Role.ROLE_PROGRESS_BAR) return text
    val m = mode()
    if (m == "sound" || m == "off") return ""
    if (text.isNullOrEmpty()) return text
    val percent = AccessibilityNodeInfoUtils.getProgressPercent(node)
    if (percent.isNaN() || percent < 0f || percent > 100.5f) return text
    val value = percent.roundToInt()
    val step = string(PREF_STEP, "5").toIntOrNull() ?: 5
    val due = lastSpoken < 0 || value >= 100 || value < lastSpoken || abs(value - lastSpoken) >= step
    if (!due) return ""
    lastSpoken = value
    if (!bool(PREF_PERCENT, true)) return text
    val context = TalkBackService.getInstance() ?: return text
    return context.getString(R.string.nika_progress_percent_fmt, value)
  }

  /** Same as [speech] for the compositor rules that keep the text in an Optional. */
  @JvmStatic
  fun speech(
    node: AccessibilityNodeInfoCompat?,
    text: Optional<CharSequence>,
  ): Optional<CharSequence> {
    val out = speech(node, text.orElse(null))
    return if (out == null) text else Optional.of(out)
  }
}
