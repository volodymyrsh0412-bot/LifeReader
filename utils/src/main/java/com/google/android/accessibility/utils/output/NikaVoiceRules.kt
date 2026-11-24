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
package com.google.android.accessibility.utils.output

import android.content.Context
import com.google.android.accessibility.utils.SharedPreferencesUtils
import java.util.Locale

/**
 * Nika: voices chosen by language or by alphabet. Each rule says "text in this language (or in
 * this alphabet) is spoken with this voice". Rules are kept as lines "kind TAB value TAB voice".
 */
object NikaVoiceRules {
  const val PREF = "nika_voice_rules"
  const val KIND_LANG = "lang"
  const val KIND_SCRIPT = "script"

  data class Rule(val kind: String, val value: String, val voice: String)

  @Volatile private var cachedRaw: String? = null
  @Volatile private var cachedRules: List<Rule> = emptyList()

  @JvmStatic
  fun load(context: Context): List<Rule> {
    val raw = SharedPreferencesUtils.getSharedPreferences(context).getString(PREF, "") ?: ""
    if (raw == cachedRaw) return cachedRules
    val rules = ArrayList<Rule>()
    for (line in raw.split("\n")) {
      val p = line.split("\t")
      if (p.size == 3 && p[0].isNotEmpty() && p[1].isNotEmpty() && p[2].isNotEmpty()) {
        rules.add(Rule(p[0], p[1], p[2]))
      }
    }
    cachedRules = rules
    cachedRaw = raw
    return rules
  }

  @JvmStatic
  fun save(context: Context, rules: List<Rule>) {
    val raw = rules.joinToString("\n") { it.kind + "\t" + it.value + "\t" + it.voice }
    SharedPreferencesUtils.getSharedPreferences(context).edit().putString(PREF, raw).apply()
  }

  /** The alphabet most of the letters of [text] belong to: "cyrillic", "latin", ... or null. */
  @JvmStatic
  fun scriptOf(text: CharSequence?): String? {
    if (text.isNullOrEmpty()) return null
    val counts = HashMap<String, Int>()
    var i = 0
    val limit = minOf(text.length, 300)
    while (i < limit) {
      val cp = Character.codePointAt(text, i)
      i += Character.charCount(cp)
      if (!Character.isLetter(cp)) continue
      val name =
        when (Character.UnicodeScript.of(cp)) {
          Character.UnicodeScript.CYRILLIC -> "cyrillic"
          Character.UnicodeScript.LATIN -> "latin"
          Character.UnicodeScript.GREEK -> "greek"
          Character.UnicodeScript.ARABIC -> "arabic"
          Character.UnicodeScript.HEBREW -> "hebrew"
          Character.UnicodeScript.HAN,
          Character.UnicodeScript.HIRAGANA,
          Character.UnicodeScript.KATAKANA -> "cjk"
          else -> continue
        }
      counts[name] = (counts[name] ?: 0) + 1
    }
    return counts.maxByOrNull { it.value }?.key
  }

  /** The name of the voice to speak [text] with, or null if no rule applies. */
  @JvmStatic
  fun voiceFor(context: Context, text: CharSequence?, locale: Locale?): String? {
    val rules = load(context)
    if (rules.isEmpty()) return null
    if (locale != null) {
      val tag = locale.toLanguageTag()
      val exact = rules.firstOrNull { it.kind == KIND_LANG && it.value.equals(tag, true) }
      if (exact != null) return exact.voice
      val byLanguage =
        rules.firstOrNull { it.kind == KIND_LANG && it.value.equals(locale.language, true) }
      if (byLanguage != null) return byLanguage.voice
    }
    val script = scriptOf(text) ?: return null
    return rules.firstOrNull { it.kind == KIND_SCRIPT && it.value == script }?.voice
  }
}
