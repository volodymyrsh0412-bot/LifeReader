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

package com.google.android.accessibility.talkback.scripting

import android.content.Context

/**
 * Nika: installs the built-in script "Nika AI" (the AI agent) from the app's assets. It is
 * installed again only when the version in the app is not the version that was installed last
 * time, so a script that the person removed stays removed until the next version.
 */
object NikaBuiltInAgent {
  private const val ASSET = "scripting/nika-ai-agent.js"
  private const val DIR = "scripting/builtin"
  private const val PREFS = "nika_builtin_scripts"
  private const val KEY = "ai_agent_version"

  @JvmStatic
  fun ensureInstalled(context: Context, store: ScriptStore) {
    try {
      install(context, store, ASSET, KEY)
    } catch (e: Exception) {
      // The script engine works without the built-in script.
    }
    // The other built-in scripts: each is installed on its own, so one failing does not stop the rest.
    val names = try { context.assets.list(DIR) ?: emptyArray<String>() } catch (e: Exception) { emptyArray<String>() }
    for (name in names) {
      if (!name.endsWith(".js")) continue
      try {
        install(context, store, DIR + "/" + name, "builtin_" + name)
      } catch (e: Exception) {
        // skip
      }
    }
  }

  private fun install(context: Context, store: ScriptStore, asset: String, key: String) {
    val bytes = context.assets.open(asset).use { it.readBytes() }
    val pkg = ScriptPackage.read(bytes)
    val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    if (prefs.getString(key, null) == pkg.manifest.version) return
    store.install(pkg, pkg.manifest.permissions, true)
    prefs.edit().putString(key, pkg.manifest.version).apply()
  }
}
