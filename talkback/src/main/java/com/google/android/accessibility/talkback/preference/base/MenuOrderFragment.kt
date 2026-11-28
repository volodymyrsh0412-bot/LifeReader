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
import android.view.View
import androidx.core.view.ViewCompat
import androidx.preference.Preference
import androidx.preference.PreferenceGroup
import androidx.preference.PreferenceViewHolder
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.contextmenu.NikaMenuOrder

/**
 * Nika: order of the items of the Nika menu. The top section holds the items that are in the menu,
 * in their order; the bottom section holds the items that can be added. Delete moves an item to
 * the bottom section; a click there puts it back.
 */
class MenuOrderFragment : NikaKeysBaseFragment(R.xml.menu_order_preferences) {
  override val collapseSections: Boolean = false

  override fun getTitle(): CharSequence = getText(R.string.nika_mo_title)

  override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
    super.onCreatePreferences(savedInstanceState, rootKey)
    rebuild()
  }

  private fun heading(ctx: android.content.Context, text: String): Preference {
    val h = NikaHeadingPreference(ctx)
    h.title = text
    h.isSelectable = false
    h.isPersistent = false
    return h
  }

  private fun rebuild() {
    val ctx = context ?: return
    // An empty group from the XML is removed by the settings filter, so the rows go on the screen itself.
    val group: PreferenceGroup = preferenceScreen ?: return
    group.removeAll()
    // Items that were only hidden earlier are now simply in the "can be added" section.
    val oldHidden = NikaMenuOrder.load(ctx, NikaMenuOrder.HIDDEN_KEY)
    if (oldHidden.isNotEmpty()) {
      val gone0 = NikaMenuOrder.load(ctx, NikaMenuOrder.DELETED_KEY)
      for (k in oldHidden) if (!gone0.contains(k)) gone0.add(k)
      NikaMenuOrder.save(ctx, NikaMenuOrder.DELETED_KEY, gone0)
      NikaMenuOrder.save(ctx, NikaMenuOrder.HIDDEN_KEY, emptyList())
      NikaMenuOrder.setCustom(ctx)
    }
    val list = NikaMenuOrder.listForScreen(ctx)
    val order = list.map { it.first }
    group.addPreference(heading(ctx, getString(R.string.nika_mo_in)))
    if (order.isEmpty()) {
      val empty = Preference(ctx)
      empty.title = getString(R.string.nika_mo_empty)
      empty.isSelectable = false
      empty.isPersistent = false
      group.addPreference(empty)
    }
    for ((i, entry) in list.withIndex()) {
      val key = entry.first
      val p = MovePreference(ctx)
      p.title = entry.second
      p.isPersistent = false
      p.up = if (i > 0) ({ move(key, -1) }) else null
      p.down = if (i < order.size - 1) ({ move(key, 1) }) else null
      p.remove = { delete(key) }
      p.setOnPreferenceClickListener {
        showMenu(entry.second, key, i, order.size)
        true
      }
      group.addPreference(p)
    }
    val gone = NikaMenuOrder.deletedList(ctx)
    group.addPreference(heading(ctx, getString(R.string.nika_mo_can_add)))
    if (gone.isEmpty()) {
      val none = Preference(ctx)
      none.title = getString(R.string.nika_mo_none)
      none.isSelectable = false
      none.isPersistent = false
      group.addPreference(none)
    }
    for (entry in gone) {
      val key = entry.first
      val p = AddPreference(ctx)
      p.title = entry.second
      p.isPersistent = false
      p.add = { restore(key) }
      p.setOnPreferenceClickListener {
        restore(key)
        true
      }
      group.addPreference(p)
    }
  }

  private fun delete(key: String) {
    val ctx = context ?: return
    val order = NikaMenuOrder.load(ctx, NikaMenuOrder.ORDER_KEY)
    order.remove(key)
    NikaMenuOrder.save(ctx, NikaMenuOrder.ORDER_KEY, order)
    val gone = NikaMenuOrder.load(ctx, NikaMenuOrder.DELETED_KEY)
    if (!gone.contains(key)) gone.add(key)
    NikaMenuOrder.save(ctx, NikaMenuOrder.DELETED_KEY, gone)
    NikaMenuOrder.setCustom(ctx)
    rebuild()
  }

  private fun restore(key: String) {
    val ctx = context ?: return
    val gone = NikaMenuOrder.load(ctx, NikaMenuOrder.DELETED_KEY)
    gone.remove(key)
    NikaMenuOrder.save(ctx, NikaMenuOrder.DELETED_KEY, gone)
    val order = NikaMenuOrder.load(ctx, NikaMenuOrder.ORDER_KEY)
    if (!order.contains(key)) order.add(key)
    NikaMenuOrder.save(ctx, NikaMenuOrder.ORDER_KEY, order)
    NikaMenuOrder.setCustom(ctx)
    rebuild()
  }

  private fun showMenu(title: String, key: String, index: Int, size: Int) {
    val act = activity ?: return
    val labels = ArrayList<String>()
    val actions = ArrayList<() -> Unit>()
    if (index > 0) {
      labels.add(getString(R.string.nika_mo_up)); actions.add { move(key, -1) }
    }
    if (index < size - 1) {
      labels.add(getString(R.string.nika_mo_down)); actions.add { move(key, 1) }
    }
    labels.add(getString(R.string.nika_mo_delete))
    actions.add { delete(key) }
    AlertDialog.Builder(act)
      .setTitle(title)
      .setItems(labels.toTypedArray()) { _, which -> actions[which]() }
      .setNegativeButton(android.R.string.cancel, null)
      .show()
  }

  private fun move(key: String, delta: Int) {
    val ctx = context ?: return
    NikaMenuOrder.listForScreen(ctx)
    val order = NikaMenuOrder.load(ctx, NikaMenuOrder.ORDER_KEY)
    val i = order.indexOf(key)
    val j = i + delta
    if (i < 0 || j < 0 || j >= order.size) return
    order.removeAt(i)
    order.add(j, key)
    NikaMenuOrder.save(ctx, NikaMenuOrder.ORDER_KEY, order)
    NikaMenuOrder.setCustom(ctx)
    rebuild()
  }

  /** A row of the menu with the accessibility actions "move up", "move down" and "delete". */
  class MovePreference(context: android.content.Context) : Preference(context) {
    var up: (() -> Unit)? = null
    var down: (() -> Unit)? = null
    var remove: (() -> Unit)? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
      super.onBindViewHolder(holder)
      val v: View = holder.itemView
      up?.let { f ->
        ViewCompat.addAccessibilityAction(v, context.getString(R.string.nika_mo_up)) { _, _ -> f(); true }
      }
      down?.let { f ->
        ViewCompat.addAccessibilityAction(v, context.getString(R.string.nika_mo_down)) { _, _ -> f(); true }
      }
      remove?.let { f ->
        ViewCompat.addAccessibilityAction(v, context.getString(R.string.nika_mo_delete)) { _, _ -> f(); true }
      }
    }
  }

  /** A row of the "can be added" section with the accessibility action "add". */
  class AddPreference(context: android.content.Context) : Preference(context) {
    var add: (() -> Unit)? = null

    override fun onBindViewHolder(holder: PreferenceViewHolder) {
      super.onBindViewHolder(holder)
      add?.let { f ->
        ViewCompat.addAccessibilityAction(holder.itemView, context.getString(R.string.nika_mo_add)) { _, _ -> f(); true }
      }
    }
  }
}
