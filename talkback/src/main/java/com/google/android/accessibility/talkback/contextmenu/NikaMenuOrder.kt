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

package com.google.android.accessibility.talkback.contextmenu

import android.content.Context
import com.google.android.accessibility.utils.SharedPreferencesUtils

/**
 * Nika: the order of the items of the Nika menu, and which items are hidden, chosen by the person.
 * Items are known by their titles. The saved list also remembers items that are not in the menu
 * right now, so they return to their place when they appear again.
 */
object NikaMenuOrder {
  const val ORDER_KEY = "nika_menu_order"
  const val HIDDEN_KEY = "nika_menu_hidden"
  private const val SEP = "\n"

  @JvmStatic
  fun load(context: Context, key: String): MutableList<String> {
    val raw =
      try {
        SharedPreferencesUtils.getSharedPreferences(context).getString(key, "") ?: ""
      } catch (e: RuntimeException) {
        ""
      }
    return if (raw.isEmpty()) mutableListOf() else raw.split(SEP).filter { it.isNotEmpty() }.toMutableList()
  }

  @JvmStatic
  fun save(context: Context, key: String, list: List<String>) {
    try {
      SharedPreferencesUtils.getSharedPreferences(context)
        .edit()
        .putString(key, list.joinToString(SEP))
        .apply()
    } catch (e: RuntimeException) {
      // skip
    }
  }

  /** Puts the items into the saved order and removes the hidden ones. Never throws. */
  @JvmStatic
  fun apply(context: Context, items: MutableList<ContextMenuItem>) {
    try {
      val stored = load(context, ORDER_KEY)
      val hidden = load(context, HIDDEN_KEY).toHashSet()
      val titles = items.map { it.title?.toString() ?: "" }
      var changed = false
      for ((i, t) in titles.withIndex()) {
        if (t.isEmpty() || stored.contains(t)) continue
        var at = -1
        for (j in i + 1 until titles.size) {
          val idx = stored.indexOf(titles[j])
          if (idx >= 0) {
            at = idx
            break
          }
        }
        if (at >= 0) stored.add(at, t) else stored.add(t)
        changed = true
      }
      if (changed) save(context, ORDER_KEY, stored)
      val sorted = items.sortedBy { val p = stored.indexOf(it.title?.toString() ?: ""); if (p < 0) Int.MAX_VALUE else p }
      val result = sorted.filter { !hidden.contains(it.title?.toString() ?: "") }
      if (result.isEmpty()) return
      items.clear()
      items.addAll(result)
    } catch (e: RuntimeException) {
      // the menu keeps its normal order
    }
  }
}
