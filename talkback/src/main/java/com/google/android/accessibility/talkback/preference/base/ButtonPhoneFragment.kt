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

package com.google.android.accessibility.talkback.preference.base

import android.app.AlertDialog
import android.os.Bundle
import android.view.KeyEvent
import androidx.preference.Preference
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.keyactions.NikaKeyActions
import com.google.android.accessibility.utils.SharedPreferencesUtils

/** Nika: settings for the actions of keys, for phones with buttons. */
class ButtonPhoneFragment : TalkbackBaseFragment(R.xml.button_phone_preferences) {
  override fun getTitle(): CharSequence = getText(R.string.nika_keys_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    for (n in 1..4) {
      val pref = findPreference<Preference>("nika_key_c${n}_pick") ?: continue
      showChosenKey(pref, n)
      pref.setOnPreferenceClickListener {
        capture(pref, n)
        true
      }
    }
    collapseCategories()
  }

  private fun showChosenKey(pref: Preference, n: Int) {
    val ctx = context ?: return
    val code =
      SharedPreferencesUtils.getSharedPreferences(ctx).getInt(NikaKeyActions.customCodeKey(n), 0)
    pref.summary =
      if (code == 0) {
        getString(R.string.nika_keys_pick_none)
      } else {
        KeyEvent.keyCodeToString(code).removePrefix("KEYCODE_")
      }
  }

  private fun save(n: Int, code: Int, pref: Preference) {
    val ctx = context ?: return
    SharedPreferencesUtils.getSharedPreferences(ctx)
      .edit()
      .putInt(NikaKeyActions.customCodeKey(n), code)
      .apply()
    showChosenKey(pref, n)
  }

  private fun capture(pref: Preference, n: Int) {
    val ctx = activity ?: return
    NikaKeyActions.capturing = true
    AlertDialog.Builder(ctx)
      .setTitle(R.string.nika_keys_capture_title)
      .setMessage(R.string.nika_keys_capture_message)
      .setNegativeButton(android.R.string.cancel, null)
      .setNeutralButton(R.string.nika_keys_capture_clear) { _, _ -> save(n, 0, pref) }
      .setOnKeyListener { dialog, keyCode, event ->
        if (
          event.action == KeyEvent.ACTION_DOWN &&
            keyCode != KeyEvent.KEYCODE_BACK &&
            keyCode != KeyEvent.KEYCODE_HOME &&
            keyCode != KeyEvent.KEYCODE_POWER &&
            keyCode != KeyEvent.KEYCODE_UNKNOWN &&
            !KeyEvent.isModifierKey(keyCode)
        ) {
          save(n, keyCode, pref)
          dialog.dismiss()
          true
        } else {
          false
        }
      }
      .setOnDismissListener { NikaKeyActions.capturing = false }
      .create()
      .show()
  }
}
