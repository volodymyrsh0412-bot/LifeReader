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

package com.google.android.accessibility.talkback.notifications

import android.app.Notification
import android.content.Context
import android.content.pm.PackageManager
import android.text.TextUtils
import android.text.format.DateFormat
import com.google.android.accessibility.utils.SharedPreferencesUtils
import java.util.Date

/**
 * Nika: builds the spoken text of a notification from the parts the user chose: app name, time,
 * type, title and text. With the default choices the text is the same as before.
 */
object NikaNotificationFormat {
  const val PREF_APP = "nika_notif_app"
  const val PREF_TIME = "nika_notif_time"
  const val PREF_TYPE = "nika_notif_type"
  const val PREF_HEAD = "nika_notif_head"
  const val PREF_TEXT = "nika_notif_text"

  @JvmStatic
  fun compose(
    context: Context,
    notification: Notification?,
    packageName: CharSequence?,
    type: CharSequence,
    defaultText: CharSequence,
  ): CharSequence {
    if (notification == null || Notification.CATEGORY_CALL == notification.category) {
      return defaultText
    }
    val prefs = SharedPreferencesUtils.getSharedPreferences(context)
    val app = prefs.getBoolean(PREF_APP, false)
    val time = prefs.getBoolean(PREF_TIME, false)
    val typeOn = prefs.getBoolean(PREF_TYPE, true)
    val head = prefs.getBoolean(PREF_HEAD, true)
    val text = prefs.getBoolean(PREF_TEXT, true)
    if (!app && !time && typeOn && head && text) {
      return defaultText
    }
    val parts = ArrayList<CharSequence>()
    if (app) {
      appLabel(context, packageName)?.let { parts.add(it) }
    }
    if (time) {
      val millis = if (notification.`when` > 0) notification.`when` else System.currentTimeMillis()
      parts.add(DateFormat.getTimeFormat(context).format(Date(millis)))
    }
    if (typeOn && !TextUtils.isEmpty(type)) {
      parts.add(type)
    }
    val extras = notification.extras
    val title = extras?.getCharSequence("android.title")
    val body = extras?.getCharSequence("android.text")
    if (head && !TextUtils.isEmpty(title)) {
      parts.add(title!!)
    }
    if (text) {
      if (!TextUtils.isEmpty(body)) {
        parts.add(body!!)
      } else if (
        !TextUtils.isEmpty(notification.tickerText) &&
          notification.tickerText.toString().trim() != title?.toString()?.trim()
      ) {
        parts.add(notification.tickerText)
      }
    }
    return TextUtils.join(". ", parts)
  }

  private fun appLabel(context: Context, packageName: CharSequence?): CharSequence? {
    val pkg = packageName?.toString() ?: return null
    return try {
      val pm = context.packageManager
      pm.getApplicationLabel(pm.getApplicationInfo(pkg, 0))
    } catch (e: PackageManager.NameNotFoundException) {
      pkg
    } catch (e: RuntimeException) {
      pkg
    }
  }
}
