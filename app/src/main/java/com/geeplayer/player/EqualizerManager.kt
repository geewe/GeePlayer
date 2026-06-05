package com.geeplayer.player

import android.media.audiofx.*
import android.util.Log

class EqualizerManager(private val audioSessionId: Int = 0) {
    private companion object {
        private const val TAG = "EqualizerManager"
        val PRESET_NAMES = arrayOf(
            "Normal", "Classical", "Dance", "Flat", "Folk",
            "HeavyMetal", "HipHop", "Jazz", "Pop", "Rock",
            "Vocal", "Custom"
        )
    }

    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: PresetReverb? = null
    private var loudnessEnhancer: LoudnessEnhancer? = null

    var isEnabled: Boolean = false
        private set
    var bassBoostLevel: Short = 0
    var virtualizerStrength: Short = 0
    var selectedPreset: Int = 0

    val numberOfBands: Int
        get() = equalizer?.numberOfBands?.toInt() ?: 0

    val centerFreqs: LongArray
        get() {
            val eq = equalizer ?: return LongArray(0)
            val bands = eq.numberOfBands.toInt()
            val freqs = LongArray(bands)
            for (i in 0 until bands) {
                freqs[i] = eq.getCenterFreq(i.toShort()).toLong()
            }
            return freqs
        }

    fun init() {
        release()
        try {
            equalizer = Equalizer(0, audioSessionId).apply { enabled = true }
            bassBoost = BassBoost(0, audioSessionId).apply { enabled = true }
            virtualizer = Virtualizer(0, audioSessionId).apply { enabled = true }
            reverb = PresetReverb(0, audioSessionId).apply { enabled = true }
            loudnessEnhancer = LoudnessEnhancer(audioSessionId)
            isEnabled = true
            Log.i(TAG, "Equalizer initialized (bands=${equalizer?.numberOfBands})")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize equalizer", e)
            isEnabled = false
        }
    }

    fun setBandLevel(band: Int, levelDb: Float) {
        val eq = equalizer ?: return
        try {
            val millibels = (levelDb * 100).toInt().toShort()
            eq.setBandLevel(band.toShort(), millibels)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set band level", e)
        }
    }

    fun applyPreset(preset: Int) {
        if (preset < 0 || preset >= PRESET_NAMES.size) return
        selectedPreset = preset
        if (preset == 11) return

        val presets = arrayOf(
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, -2f, -4f, -6f, -8f),
            floatArrayOf(6f, 4f, 2f, 0f, -2f, 0f, 2f, 4f, 6f, 6f),
            floatArrayOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f),
            floatArrayOf(2f, 4f, 6f, 4f, 0f, -2f, -4f, -4f, 2f, 4f),
            floatArrayOf(6f, 4f, 0f, -4f, -4f, 0f, 4f, 6f, 6f, 6f),
            floatArrayOf(4f, 4f, 2f, 0f, -2f, 0f, 4f, 6f, 6f, 6f),
            floatArrayOf(4f, 4f, 2f, 2f, 0f, 0f, 0f, 2f, 4f, 6f),
            floatArrayOf(-2f, 0f, 4f, 6f, 6f, 4f, 0f, -2f, -2f, 0f),
            floatArrayOf(6f, 4f, 2f, 0f, -2f, -2f, 0f, 2f, 4f, 6f),
            floatArrayOf(0f, 2f, 4f, 4f, 2f, 0f, -2f, -2f, 0f, 2f),
        )

        val levels = presets.getOrNull(preset) ?: return
        for (i in levels.indices) {
            setBandLevel(i, levels[i])
        }
        Log.i(TAG, "Applied preset: ${PRESET_NAMES[preset]}")
    }

    fun setBassBoost(strength: Short) {
        bassBoostLevel = strength
        try { bassBoost?.setStrength(strength) } catch (e: Exception) { Log.w(TAG, "BassBoost error", e) }
    }

    fun setVirtualizer(strength: Short) {
        virtualizerStrength = strength
        try { virtualizer?.setStrength(strength) } catch (e: Exception) { Log.w(TAG, "Virtualizer error", e) }
    }

    fun setReverb(preset: Short) {
        try { reverb?.preset = preset } catch (e: Exception) { Log.w(TAG, "Reverb error", e) }
    }

    fun setLoudnessGain(gain: Int) {
        try { loudnessEnhancer?.let { it.setTargetGain(gain.coerceIn(0, 10000)) } } catch (e: Exception) { Log.w(TAG, "LoudnessEnhancer error", e) }
    }

    fun setEnabled(enabled: Boolean) {
        isEnabled = enabled
        try {
            equalizer?.enabled = enabled
            bassBoost?.enabled = enabled
            virtualizer?.enabled = enabled
            reverb?.enabled = enabled
            loudnessEnhancer?.enabled = enabled
        } catch (e: Exception) { Log.w(TAG, "Toggle error", e) }
    }

    fun release() {
        try {
            equalizer?.release(); bassBoost?.release()
            virtualizer?.release(); reverb?.release()
            loudnessEnhancer?.release()
        } catch (e: Exception) { Log.w(TAG, "Release error", e) }
        equalizer = null; bassBoost = null
        virtualizer = null; reverb = null
        loudnessEnhancer = null
        isEnabled = false
        Log.d(TAG, "Equalizer released")
    }
}
