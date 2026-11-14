package com.pcsf.amplificador

import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.ln

/** Frequências centrais (Hz) das 10 bandas do equalizador. */
val BAND_FREQS = intArrayOf(31, 62, 125, 250, 500, 1000, 2000, 4000, 8000, 16000)
const val BAND_MIN_DB = -12f
const val BAND_MAX_DB = 12f
const val GAIN_MAX_DB = 15f
const val BASS_MAX_DB = 9f

/** Quanto do "reforço de graves" vai para cada banda. */
private val BASS_WEIGHTS = floatArrayOf(1f, 0.9f, 0.6f, 0.25f, 0f, 0f, 0f, 0f, 0f, 0f)

fun bandLabel(f: Int): String = if (f >= 1000) "${f / 1000}k" else "$f"

data class EqSettings(
    val enabled: Boolean = false,
    /** Amplificação (pré-ganho) em dB, 0..GAIN_MAX_DB. */
    val gainDb: Float = 0f,
    /** Limitador anti-distorção. */
    val limiter: Boolean = true,
    val bands: List<Float> = List(BAND_FREQS.size) { 0f },
    /** Reforço de graves 0..1. */
    val bass: Float = 0f,
    /** Efeito espacial (Virtualizer) 0..1. */
    val spatial: Float = 0f,
    val preset: String = "Plano",
) {
    /** Curva final (dB) por banda, somando as bandas e opcionalmente o reforço de graves. */
    fun curveDb(includeBass: Boolean = true): FloatArray = FloatArray(BAND_FREQS.size) { i ->
        val b = bands.getOrElse(i) { 0f }
        if (includeBass) b + bass * BASS_MAX_DB * BASS_WEIGHTS[i] else b
    }

    /** Ganho (dB) interpolado em escala log para uma frequência qualquer. */
    fun dbAt(freqHz: Double, includeBass: Boolean = true): Float {
        val c = curveDb(includeBass)
        if (freqHz <= BAND_FREQS.first()) return c.first()
        if (freqHz >= BAND_FREQS.last()) return c.last()
        val lf = ln(freqHz)
        for (i in 0 until BAND_FREQS.size - 1) {
            val a = ln(BAND_FREQS[i].toDouble())
            val b = ln(BAND_FREQS[i + 1].toDouble())
            if (lf in a..b) {
                val t = ((lf - a) / (b - a)).toFloat()
                return c[i] + (c[i + 1] - c[i]) * t
            }
        }
        return 0f
    }

    fun toJson(): String = JSONObject().apply {
        put("enabled", enabled)
        put("gainDb", gainDb.toDouble())
        put("limiter", limiter)
        put("bands", JSONArray().apply { bands.forEach { put(it.toDouble()) } })
        put("bass", bass.toDouble())
        put("spatial", spatial.toDouble())
        put("preset", preset)
    }.toString()

    companion object {
        fun fromJson(s: String): EqSettings = runCatching {
            val o = JSONObject(s)
            val arr = o.optJSONArray("bands")
            val bands = List(BAND_FREQS.size) { i ->
                (arr?.optDouble(i, 0.0) ?: 0.0).toFloat().coerceIn(BAND_MIN_DB, BAND_MAX_DB)
            }
            EqSettings(
                enabled = o.optBoolean("enabled", false),
                gainDb = o.optDouble("gainDb", 0.0).toFloat().coerceIn(0f, GAIN_MAX_DB),
                limiter = o.optBoolean("limiter", true),
                bands = bands,
                bass = o.optDouble("bass", 0.0).toFloat().coerceIn(0f, 1f),
                spatial = o.optDouble("spatial", 0.0).toFloat().coerceIn(0f, 1f),
                preset = o.optString("preset", "Personalizado"),
            )
        }.getOrDefault(EqSettings())
    }
}

data class Preset(val name: String, val bands: List<Float>)

val PRESETS = listOf(
    Preset("Plano", listOf(0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f, 0f)),
    Preset("Bluetooth claro", listOf(2f, 2f, 1f, 0f, -1f, 0f, 1f, 2.5f, 3f, 3f)),
    Preset("Graves fortes", listOf(6f, 5f, 4f, 2f, 0f, 0f, 0f, 0f, 0f, 0f)),
    Preset("Vocal / Podcast", listOf(-3f, -2f, -1f, 1f, 3f, 4f, 3.5f, 1.5f, 0f, -1f)),
    Preset("Rock", listOf(5f, 4f, 2f, -1f, -2f, -1f, 2f, 3f, 4f, 4f)),
    Preset("Pop", listOf(-1f, 1f, 3f, 4f, 3f, 0f, -1f, -1f, 0f, 1f)),
    Preset("Sertanejo", listOf(3f, 3f, 2f, 0f, 0f, 2f, 3f, 2f, 1f, 1f)),
    Preset("Eletrônica", listOf(6f, 5f, 2f, 0f, -2f, 1f, 0f, 2f, 4f, 5f)),
    Preset("Clássica", listOf(4f, 3f, 2f, 1f, -1f, -1f, 0f, 2f, 3f, 4f)),
    Preset("Agudos", listOf(0f, 0f, 0f, 0f, 0f, 1f, 3f, 5f, 6f, 6f)),
    Preset("Filmes", listOf(4f, 3f, 1f, 0f, 1f, 2f, 3f, 2f, 1f, 0f)),
)
