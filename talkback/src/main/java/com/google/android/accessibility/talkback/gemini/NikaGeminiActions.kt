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

package com.google.android.accessibility.talkback.gemini

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.view.accessibility.AccessibilityNodeInfo
import androidx.annotation.StringRes
import com.google.android.accessibility.talkback.R
import com.google.android.accessibility.talkback.TalkBackService
import com.google.android.accessibility.utils.SharedPreferencesUtils

/**
 * Nika: actions for the Gemini app that can be assigned to gestures and keys. Each one finds the
 * button on the screen by words in its name (the words can be changed in the settings) and
 * presses it.
 */
object NikaGeminiActions {

  /** [value] is saved in the gesture preference, so it must never change once released. */
  class Action(val value: String, @StringRes val labelRes: Int, val defaults: String)

  @JvmField
  val ACTIONS: List<Action> =
    listOf(
      Action("nika_gem_live", R.string.nika_act_gem_live, "live,лайв,розмов,conversation,voice,голос"),
      Action("nika_gem_video", R.string.nika_act_gem_video, "камер,відео,видео,camera,video"),
      Action("nika_gem_screen", R.string.nika_act_gem_screen, "екран,экран,screen,share,поділитися,показати"),
      Action("nika_gem_text", R.string.nika_act_gem_text, "запитайте,спросите,ask gemini,ask,введіть,type"),
      Action("nika_gem_dump", R.string.nika_act_gem_dump, ""),
    )

  private fun find(value: String?): Action? = ACTIONS.firstOrNull { it.value == value }

  @JvmStatic fun isAction(value: String?): Boolean = find(value) != null

  @JvmStatic
  fun label(context: Context, value: String?): String? =
    find(value)?.let { context.getString(it.labelRes) }

  @JvmStatic
  fun keywordsKey(value: String): String = "${value}_words"

  @JvmStatic
  fun defaultsOf(value: String): String = find(value)?.defaults ?: ""

  @JvmStatic
  fun perform(service: TalkBackService, value: String): Boolean {
    val action = find(value) ?: return false
    try {
      val root = service.rootInActiveWindow
      if (root == null) {
        say(service, service.getString(R.string.nika_gem_not_found))
        return true
      }
      if (value == "nika_gem_dump") {
        dump(service, root)
        return true
      }
      val saved =
        android.preference.PreferenceManager.getDefaultSharedPreferences(service)
          .getString(keywordsKey(value), null)
          ?: SharedPreferencesUtils.getSharedPreferences(service).getString(keywordsKey(value), null)
      val words =
        (saved?.takeIf { it.isNotBlank() } ?: action.defaults)
          .lowercase()
          .split(",")
          .map { it.trim() }
          .filter { it.isNotEmpty() }
      val found = findNode(root, words, value == "nika_gem_text")
      if (found == null) {
        say(service, service.getString(R.string.nika_gem_not_found))
        return true
      }
      if (value == "nika_gem_text") {
        found.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        found.performAction(AccessibilityNodeInfo.ACTION_ACCESSIBILITY_FOCUS)
        found.performAction(AccessibilityNodeInfo.ACTION_CLICK)
      } else {
        clickable(found)?.performAction(AccessibilityNodeInfo.ACTION_CLICK)
          ?: say(service, service.getString(R.string.nika_gem_not_found))
      }
    } catch (e: RuntimeException) {
      say(service, service.getString(R.string.nika_gem_not_found))
    }
    return true
  }

  private fun labelOf(node: AccessibilityNodeInfo): String =
    ((node.contentDescription ?: node.text ?: node.hintText ?: "").toString()).lowercase()

  /** Walks the screen; the first node whose name has one of the words wins (earlier words first). */
  private fun findNode(
    root: AccessibilityNodeInfo,
    words: List<String>,
    editOnly: Boolean,
  ): AccessibilityNodeInfo? {
    val all = ArrayList<AccessibilityNodeInfo>()
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    stack.addLast(root)
    while (stack.isNotEmpty() && all.size < 1500) {
      val n = stack.removeFirst()
      all.add(n)
      for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
    }
    for (w in words) {
      for (n in all) {
        if (!n.isVisibleToUser) continue
        if (editOnly && !n.isEditable && n.className?.toString()?.contains("EditText") != true) continue
        if (labelOf(n).contains(w)) return n
      }
    }
    return null
  }

  private fun clickable(node: AccessibilityNodeInfo): AccessibilityNodeInfo? {
    var n: AccessibilityNodeInfo? = node
    var depth = 0
    while (n != null && depth < 6) {
      if (n.isClickable) return n
      n = n.parent
      depth++
    }
    return null
  }

  /** Copies the names of all buttons on the screen, so the words in the settings can be chosen. */
  private fun dump(service: TalkBackService, root: AccessibilityNodeInfo) {
    val lines = ArrayList<String>()
    val stack = ArrayDeque<AccessibilityNodeInfo>()
    stack.addLast(root)
    var visited = 0
    while (stack.isNotEmpty() && visited < 1500) {
      val n = stack.removeFirst()
      visited++
      val label = labelOf(n)
      if (n.isVisibleToUser && (n.isClickable || n.isEditable) && label.isNotEmpty()) {
        lines.add(label)
      }
      for (i in 0 until n.childCount) n.getChild(i)?.let { stack.addLast(it) }
    }
    try {
      service
        .getSystemService(ClipboardManager::class.java)
        ?.setPrimaryClip(ClipData.newPlainText("nika-gemini", root.packageName.toString() + "\n" + lines.joinToString("\n")))
    } catch (e: RuntimeException) {
      // skip
    }
    say(service, service.getString(R.string.nika_gem_dumped, lines.size))
  }

  private fun say(service: TalkBackService, text: String) {
    try {
      service.speechController.speak(
        text,
        com.google.android.accessibility.utils.output.SpeechController.QUEUE_MODE_FLUSH_ALL,
        com.google.android.accessibility.utils.output.FeedbackItem.FLAG_NO_HISTORY or
          com.google.android.accessibility.utils.output.FeedbackItem.FLAG_FORCE_FEEDBACK_ALL,
        null,
        com.google.android.accessibility.utils.Performance.EVENT_ID_UNTRACKED,
      )
    } catch (e: RuntimeException) {
      // skip
    }
  }
}
