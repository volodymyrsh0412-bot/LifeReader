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

import android.accessibilityservice.AccessibilityService
import android.app.AlertDialog
import android.content.Intent
import android.os.Bundle
import android.text.InputType
import android.view.View
import android.widget.EditText
import androidx.core.view.ViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.macros.NikaMacros

/** Nika: the steps of one macro: add, move, delete, record. */
class MacroEditFragment : NikaKeysBaseFragment(R.xml.nika_list_preferences) {
  override val collapseSections: Boolean = false

  private val macroId: String
    get() = arguments?.getString(ARG_ID).orEmpty()

  override fun getTitle(): CharSequence =
    context?.let { NikaMacros.find(it, macroId)?.name } ?: getText(R.string.nika_mac_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    rebuild()
  }

  override fun onResume() {
    super.onResume()
    rebuild()
  }

  private fun rebuild() {
    val ctx = context ?: return
    val group = findPreference<PreferenceGroup>("nika_list_cat") ?: return
    group.removeAll()
    val macro = NikaMacros.find(ctx, macroId)
    if (macro == null) return
    val recording = NikaMacros.recordingId(ctx) == macro.id

    row(group, getString(R.string.nika_mac_rename, macro.name), null) { askText(getString(R.string.nika_mac_name_hint), macro.name, false) { v -> update { it.name = v } } }
    row(
      group,
      getString(if (recording) R.string.nika_mac_record_on else R.string.nika_mac_record_off),
      getString(R.string.nika_mac_record_summary),
    ) {
      NikaMacros.setRecording(ctx, if (recording) "" else macro.id)
      rebuild()
    }
    row(group, getString(R.string.nika_mac_add_step), null) { chooseStepType() }

    for ((i, step) in macro.steps.withIndex()) {
      val p = StepPreference(ctx)
      p.title = describe(step)
      p.isPersistent = false
      p.up = if (i > 0) ({ move(i, -1) }) else null
      p.down = if (i < macro.steps.size - 1) ({ move(i, 1) }) else null
      p.remove = { removeAt(i) }
      p.setOnPreferenceClickListener {
        showStepMenu(p.title.toString(), i, macro.steps.size)
        true
      }
      group.addPreference(p)
    }
    row(group, getString(R.string.nika_mac_delete_macro), null) {
      val act = activity
      if (act != null) {
        AlertDialog.Builder(act)
          .setTitle(macro.name)
          .setMessage(R.string.nika_mac_delete_macro)
          .setPositiveButton(android.R.string.ok) { _, _ ->
            val all = NikaMacros.load(ctx)
            all.removeAll { it.id == macro.id }
            NikaMacros.save(ctx, all)
            if (NikaMacros.recordingId(ctx) == macro.id) NikaMacros.setRecording(ctx, "")
            act.onBackPressedDispatcher.onBackPressed()
          }
          .setNegativeButton(android.R.string.cancel, null)
          .show()
      }
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

  private fun update(change: (NikaMacros.Macro) -> Unit) {
    val ctx = context ?: return
    val all = NikaMacros.load(ctx)
    val m = all.firstOrNull { it.id == macroId } ?: return
    change(m)
    NikaMacros.save(ctx, all)
    rebuild()
  }

  private fun move(index: Int, delta: Int) =
    update { m ->
      val j = index + delta
      if (index in m.steps.indices && j in m.steps.indices) {
        val s = m.steps.removeAt(index)
        m.steps.add(j, s)
      }
    }

  private fun removeAt(index: Int) = update { m -> if (index in m.steps.indices) m.steps.removeAt(index) }

  private fun describe(s: NikaMacros.Step): String =
    when (s.type) {
      NikaMacros.CLICK -> getString(R.string.nika_mac_d_click, s.b.ifEmpty { s.a })
      NikaMacros.CLICK_TEXT -> getString(R.string.nika_mac_d_clicktext, s.a)
      NikaMacros.WAIT -> getString(R.string.nika_mac_d_wait, s.n.toString())
      NikaMacros.SAY -> getString(R.string.nika_mac_d_say, s.a)
      NikaMacros.TYPE -> getString(R.string.nika_mac_d_type, s.a)
      NikaMacros.APP -> getString(R.string.nika_mac_d_app, appLabel(s.a))
      NikaMacros.GLOBAL -> getString(R.string.nika_mac_d_global, globalName(s.n))
      NikaMacros.HOLD -> getString(R.string.nika_mac_d_hold, s.b.ifEmpty { s.a })
      NikaMacros.RELEASE ->
        if (s.a.isEmpty() || s.a == "none" || s.n <= 0) {
          getString(R.string.nika_mac_d_release_still)
        } else {
          getString(R.string.nika_mac_d_release_move, dirName(s.a), s.n)
        }
      else -> s.type
    }

  private fun dirName(dir: String): String =
    when (dir) {
      "up" -> getString(R.string.nika_mac_dir_up)
      "down" -> getString(R.string.nika_mac_dir_down)
      "left" -> getString(R.string.nika_mac_dir_left)
      "right" -> getString(R.string.nika_mac_dir_right)
      else -> getString(R.string.nika_mac_dir_none)
    }

  private fun chooseRelease() {
    val act = activity ?: return
    val dirs = arrayOf("none", "up", "down", "left", "right")
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_mac_t_release)
      .setItems(dirs.map { dirName(it) }.toTypedArray()) { _, which ->
        val dir = dirs[which]
        if (dir == "none") {
          add(NikaMacros.Step(NikaMacros.RELEASE, a = "none", n = 0))
        } else {
          askText(getString(R.string.nika_mac_distance_hint), "20", true) { v ->
            add(NikaMacros.Step(NikaMacros.RELEASE, a = dir, n = (v.toIntOrNull() ?: 20).coerceIn(1, 40)))
          }
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun appLabel(pkg: String): String =
    try {
      val pm = requireContext().packageManager
      pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0)).toString()
    } catch (e: Exception) {
      pkg
    }

  private val globals: List<Pair<Int, Int>> =
    listOf(
      AccessibilityService.GLOBAL_ACTION_BACK to R.string.nika_mac_g_back,
      AccessibilityService.GLOBAL_ACTION_HOME to R.string.nika_mac_g_home,
      AccessibilityService.GLOBAL_ACTION_RECENTS to R.string.nika_mac_g_recents,
      AccessibilityService.GLOBAL_ACTION_NOTIFICATIONS to R.string.nika_mac_g_notifications,
    )

  private fun globalName(code: Int): String =
    globals.firstOrNull { it.first == code }?.let { getString(it.second) } ?: code.toString()

  private fun showStepMenu(title: String, index: Int, size: Int) {
    val act = activity ?: return
    val labels = ArrayList<String>()
    val actions = ArrayList<() -> Unit>()
    if (index > 0) { labels.add(getString(R.string.nika_mo_up)); actions.add { move(index, -1) } }
    if (index < size - 1) { labels.add(getString(R.string.nika_mo_down)); actions.add { move(index, 1) } }
    labels.add(getString(R.string.nika_mac_delete)); actions.add { removeAt(index) }
    AlertDialog.Builder(act)
      .setTitle(title)
      .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun chooseStepType() {
    val act = activity ?: return
    val labels =
      arrayOf(
        getString(R.string.nika_mac_t_wait),
        getString(R.string.nika_mac_t_say),
        getString(R.string.nika_mac_t_type),
        getString(R.string.nika_mac_t_app),
        getString(R.string.nika_mac_t_clicktext),
        getString(R.string.nika_mac_t_global),
        getString(R.string.nika_mac_t_hold),
        getString(R.string.nika_mac_t_release),
      )
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_mac_add_step)
      .setItems(labels) { _, which ->
        when (which) {
          0 -> askText(labels[0], "2", true) { v -> add(NikaMacros.Step(NikaMacros.WAIT, n = v.toIntOrNull() ?: 1)) }
          1 -> askText(labels[1], "", false) { v -> add(NikaMacros.Step(NikaMacros.SAY, a = v)) }
          2 -> askText(labels[2], "", false) { v -> add(NikaMacros.Step(NikaMacros.TYPE, a = v)) }
          3 -> chooseApp()
          4 -> askText(labels[4], "", false) { v -> add(NikaMacros.Step(NikaMacros.CLICK_TEXT, a = v)) }
          5 -> chooseGlobal()
          6 -> askText(labels[6], "", false) { v -> add(NikaMacros.Step(NikaMacros.HOLD, a = v)) }
          7 -> chooseRelease()
        }
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun chooseGlobal() {
    val act = activity ?: return
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_mac_t_global)
      .setItems(globals.map { getString(it.second) }.toTypedArray()) { _, which ->
        add(NikaMacros.Step(NikaMacros.GLOBAL, n = globals[which].first))
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun chooseApp() {
    val act = activity ?: return
    val pm = act.packageManager
    val found = ArrayList<Pair<String, String>>()
    val seen = HashSet<String>()
    try {
      val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
      for (info in pm.queryIntentActivities(intent, 0)) {
        val pkg = info.activityInfo?.packageName ?: continue
        if (seen.add(pkg)) found.add(Pair(info.loadLabel(pm).toString(), pkg))
      }
    } catch (e: RuntimeException) {
      // an empty list
    }
    found.sortBy { it.first.lowercase() }
    AlertDialog.Builder(act)
      .setTitle(R.string.nika_mac_t_app)
      .setItems(found.map { it.first }.toTypedArray()) { _, which ->
        add(NikaMacros.Step(NikaMacros.APP, a = found[which].second))
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun add(step: NikaMacros.Step) = update { it.steps.add(step) }

  private fun askText(title: String, initial: String, number: Boolean, done: (String) -> Unit) {
    val act = activity ?: return
    val edit = EditText(act)
    edit.setText(initial)
    if (number) edit.inputType = InputType.TYPE_CLASS_NUMBER
    AlertDialog.Builder(act)
      .setTitle(title)
      .setView(edit)
      .setPositiveButton(android.R.string.ok) { _, _ ->
        val v = edit.text.toString().trim()
        if (v.isNotEmpty()) done(v)
      }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  /** A row with the accessibility actions "move up", "move down" and "delete". */
  class StepPreference(context: android.content.Context) : Preference(context) {
    var up: (() -> Unit)? = null
    var down: (() -> Unit)? = null
    var remove: (() -> Unit)? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
      super.onBindViewHolder(holder)
      val v: View = holder.itemView
      up?.let { f -> ViewCompat.addAccessibilityAction(v, context.getString(R.string.nika_mo_up)) { _, _ -> f(); true } }
      down?.let { f -> ViewCompat.addAccessibilityAction(v, context.getString(R.string.nika_mo_down)) { _, _ -> f(); true } }
      remove?.let { f -> ViewCompat.addAccessibilityAction(v, context.getString(R.string.nika_mac_delete)) { _, _ -> f(); true } }
    }
  }

  companion object {
    const val ARG_ID = "nika_macro_id"
  }
}
