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

package com.google.android.accessibility.talkback.macros

import android.content.Context
import com.google.android.accessibility.utils.SharedPreferencesUtils
import org.json.JSONArray
import org.json.JSONObject

/** Nika: macros are lists of steps with a name; they can be assigned to gestures and keys. */
object NikaMacros {
  const val PREF_MACROS = "nika_macros"
  const val PREF_RECORDING = "nika_macro_recording"

  const val CLICK = "click"
  const val CLICK_TEXT = "clicktext"
  const val WAIT = "wait"
  const val SAY = "say"
  const val TYPE = "type"
  const val APP = "app"
  const val GLOBAL = "global"
  const val HOLD = "hold"
  const val RELEASE = "release"

  /** One step. [a] and [b] are texts, [n] is a number; what they mean depends on [type]. */
  data class Step(val type: String, val a: String = "", val b: String = "", val n: Int = 0)

  class Macro(val id: String, var name: String, val steps: MutableList<Step> = ArrayList())

  private fun prefs(context: Context) = SharedPreferencesUtils.getSharedPreferences(context)

  @JvmStatic
  fun load(context: Context): MutableList<Macro> {
    val result = ArrayList<Macro>()
    try {
      val raw = prefs(context).getString(PREF_MACROS, null) ?: return result
      val array = JSONArray(raw)
      for (i in 0 until array.length()) {
        val o = array.getJSONObject(i)
        val macro = Macro(o.getString("id"), o.optString("name", ""))
        val steps = o.optJSONArray("steps") ?: JSONArray()
        for (j in 0 until steps.length()) {
          val s = steps.getJSONObject(j)
          macro.steps.add(Step(s.optString("t"), s.optString("a"), s.optString("b"), s.optInt("n")))
        }
        result.add(macro)
      }
    } catch (e: Exception) {
      // a broken list is shown as empty
    }
    return result
  }

  @JvmStatic
  fun save(context: Context, macros: List<Macro>) {
    try {
      val array = JSONArray()
      for (m in macros) {
        val o = JSONObject()
        o.put("id", m.id)
        o.put("name", m.name)
        val steps = JSONArray()
        for (s in m.steps) {
          steps.put(JSONObject().put("t", s.type).put("a", s.a).put("b", s.b).put("n", s.n))
        }
        o.put("steps", steps)
        array.put(o)
      }
      prefs(context).edit().putString(PREF_MACROS, array.toString()).apply()
    } catch (e: Exception) {
      // skip
    }
  }

  @JvmStatic
  fun find(context: Context, id: String): Macro? = load(context).firstOrNull { it.id == id }

  @JvmStatic
  fun newId(): String = "m" + System.currentTimeMillis().toString(36)

  @JvmStatic
  fun recordingId(context: Context): String = prefs(context).getString(PREF_RECORDING, "") ?: ""

  @JvmStatic
  fun setRecording(context: Context, id: String) {
    prefs(context).edit().putString(PREF_RECORDING, id).apply()
  }

  /** Adds a step to the macro. */
  @JvmStatic
  fun addStep(context: Context, id: String, step: Step): Boolean {
    val all = load(context)
    val macro = all.firstOrNull { it.id == id } ?: return false
    macro.steps.add(step)
    save(context, all)
    return true
  }
}
