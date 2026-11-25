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
package com.google.android.accessibility.utils

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Handler
import android.os.Looper
import kotlin.math.PI
import kotlin.math.sin

/**
 * Nika: short own beeps (microphone on and off, "sound" before a translation). They do not depend
 * on the sound theme, play on the accessibility volume and follow the volume of the sounds.
 */
object NikaBeep {
  const val PREF_MIC = "nika_mic_sounds"
  const val PREF_VOLUME = "nika_soundback_vol"
  const val START = 1
  const val STOP = 2
  const val TRANSLATE = 3

  private const val RATE = 22050
  private val handler = Handler(Looper.getMainLooper())
  @Volatile private var micOn = false

  @JvmStatic
  fun micEnabled(context: Context): Boolean =
    SharedPreferencesUtils.getSharedPreferences(context).getBoolean(PREF_MIC, true)

  /** The microphone started or stopped. Beeps only for voice input, and only if the switch is on. */
  @JvmStatic
  fun mic(context: Context, active: Boolean, voiceInput: Boolean) {
    try {
      NikaDiag.log("mic", (if (active) "on" else "off") + ", voice input: " + voiceInput)
      if (active) {
        micOn = voiceInput
        if (voiceInput && micEnabled(context)) play(context, START)
      } else if (micOn) {
        micOn = false
        if (micEnabled(context)) play(context, STOP)
      }
    } catch (e: Throwable) {
      // skip
    }
  }

  @JvmStatic
  fun play(context: Context, kind: Int) {
    try {
      val pref = SharedPreferencesUtils.getSharedPreferences(context)
      val level = pref.getInt(PREF_VOLUME, 50).coerceIn(0, 100) / 100f
      val tones =
        when (kind) {
          START -> intArrayOf(700, 1000)
          STOP -> intArrayOf(1000, 700)
          else -> intArrayOf(880)
        }
      val perTone = 90
      val samplesEach = RATE * perTone / 1000
      val data = ShortArray(samplesEach * tones.size)
      var at = 0
      for (hz in tones) {
        for (i in 0 until samplesEach) {
          val fade = minOf(1f, i / 200f, (samplesEach - i) / 200f)
          data[at++] = (sin(2.0 * PI * hz * i / RATE) * fade * 12000).toInt().toShort()
        }
      }
      val track =
        AudioTrack.Builder()
          .setAudioAttributes(
            AudioAttributes.Builder()
              .setUsage(AudioAttributes.USAGE_ASSISTANCE_ACCESSIBILITY)
              .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
              .build()
          )
          .setAudioFormat(
            AudioFormat.Builder()
              .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
              .setSampleRate(RATE)
              .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
              .build()
          )
          .setBufferSizeInBytes(data.size * 2)
          .setTransferMode(AudioTrack.MODE_STATIC)
          .build()
      track.write(data, 0, data.size)
      track.setVolume(level)
      track.play()
      handler.postDelayed(
        {
          try {
            track.release()
          } catch (e: Throwable) {
            // skip
          }
        },
        (perTone * tones.size + 300).toLong(),
      )
    } catch (e: Throwable) {
      // skip
    }
  }
}
