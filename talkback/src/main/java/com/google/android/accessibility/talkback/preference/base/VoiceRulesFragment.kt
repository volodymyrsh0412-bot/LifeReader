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
import android.speech.tts.TextToSpeech
import android.speech.tts.Voice
import android.widget.Toast
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.utils.output.FailoverTextToSpeech
import com.google.android.accessibility.utils.output.NikaVoiceRules
import java.util.Locale

/** Nika: voices by language and by alphabet (Cyrillic, Latin ...), from the voices of the engine. */
class VoiceRulesFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  private var tts: TextToSpeech? = null
  private var voices: List<Voice> = emptyList()
  private var ready = false

  override fun getTitle(): CharSequence = getText(R.string.nika_vr_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    val ctx = context
    if (ctx != null) {
      try {
        val engine = FailoverTextToSpeech.getSelectedEngine(ctx)
        tts =
          TextToSpeech(
            ctx.applicationContext,
            { status ->
              if (status == TextToSpeech.SUCCESS) {
                try {
                  voices =
                    (tts?.voices ?: emptySet<Voice>())
                      .sortedBy { it.locale.toLanguageTag() + it.name }
                } catch (e: RuntimeException) {
                  voices = emptyList()
                }
              }
              ready = true
            },
            engine,
          )
      } catch (e: RuntimeException) {
        ready = true
      }
    }
    rebuild()
  }

  override fun onResume() {
    super.onResume()
    rebuild()
  }

  override fun onDestroy() {
    try {
      tts?.shutdown()
    } catch (e: RuntimeException) {
      // skip
    }
    tts = null
    super.onDestroy()
  }

  private fun scriptName(value: String): String =
    when (value) {
      "cyrillic" -> getString(R.string.nika_vr_script_cyrillic)
      "latin" -> getString(R.string.nika_vr_script_latin)
      "greek" -> getString(R.string.nika_vr_script_greek)
      "arabic" -> getString(R.string.nika_vr_script_arabic)
      "hebrew" -> getString(R.string.nika_vr_script_hebrew)
      else -> getString(R.string.nika_vr_script_cjk)
    }

  private fun ruleTitle(rule: NikaVoiceRules.Rule): String {
    val what =
      if (rule.kind == NikaVoiceRules.KIND_SCRIPT) scriptName(rule.value)
      else Locale.forLanguageTag(rule.value).let { it.getDisplayName(it).ifEmpty { rule.value } + " (" + rule.value + ")" }
    return getString(R.string.nika_vr_row, what, rule.voice)
  }

  private fun rebuild() {
    val ctx = context ?: return
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    val note = Preference(group.context)
    note.title = getString(R.string.nika_vr_note)
    note.isPersistent = false
    note.isSelectable = false
    group.addPreference(note)
    row(group, getString(R.string.nika_vr_add), null) { addRule() }
    for (rule in NikaVoiceRules.load(ctx)) {
      row(group, ruleTitle(rule), getString(R.string.nika_vr_tap_remove)) { removeRule(rule) }
    }
  }

  private fun row(group: PreferenceGroup, title: String, summary: String?, click: () -> Unit) {
    val p = Preference(group.context)
    p.title = title
    p.summary = summary
    p.isPersistent = false
    p.setOnPreferenceClickListener {
      click()
      true
    }
    group.addPreference(p)
  }

  private fun removeRule(rule: NikaVoiceRules.Rule) {
    val act = activity ?: return
    AlertDialog.Builder(act)
      .setTitle(ruleTitle(rule))
      .setPositiveButton(R.string.nika_vr_remove) { _, _ ->
        val ctx = context ?: return@setPositiveButton
        NikaVoiceRules.save(ctx, NikaVoiceRules.load(ctx).filter { it != rule })
        rebuild()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun addRule() {
    val act = activity ?: return
    if (!ready || voices.isEmpty()) {
      Toast.makeText(act, if (ready) R.string.nika_vr_novoices else R.string.nika_vr_loading, Toast.LENGTH_LONG).show()
      return
    }
    // The kinds: every language that has voices, then the alphabets.
    val languages = voices.map { it.locale.language }.distinct().sorted()
    val scripts = listOf("cyrillic", "latin", "greek", "arabic", "hebrew", "cjk")
    val labels = ArrayList<String>()
    for (l in languages) {
      val loc = Locale.forLanguageTag(l)
      labels.add(getString(R.string.nika_vr_kind_lang, loc.getDisplayLanguage(loc).ifEmpty { l }, l))
    }
    for (s in scripts) labels.add(getString(R.string.nika_vr_kind_script, scriptName(s)))
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vr_add)
      .setItems(labels.toTypedArray()) { _, which ->
        if (which < languages.size) {
          pickVoice(NikaVoiceRules.KIND_LANG, languages[which], voices.filter { it.locale.language == languages[which] })
        } else {
          pickVoice(NikaVoiceRules.KIND_SCRIPT, scripts[which - languages.size], voices)
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun pickVoice(kind: String, value: String, list: List<Voice>) {
    val act = activity ?: return
    if (list.isEmpty()) {
      Toast.makeText(act, R.string.nika_vr_novoices, Toast.LENGTH_LONG).show()
      return
    }
    val labels = list.map { it.name + " — " + it.locale.let { l -> l.getDisplayName(l) } }
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_vr_pick_voice)
      .setItems(labels.toTypedArray()) { _, which ->
        val ctx = context ?: return@setItems
        val all = NikaVoiceRules.load(ctx).filter { !(it.kind == kind && it.value == value) }.toMutableList()
        all.add(NikaVoiceRules.Rule(kind, value, list[which].name))
        NikaVoiceRules.save(ctx, all)
        rebuild()
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }
}
