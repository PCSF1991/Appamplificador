package com.pcsf.amplificador

import android.media.audiofx.BassBoost
import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.Equalizer
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.os.Build
import android.util.Log
import androidx.annotation.RequiresApi
import kotlin.math.roundToInt
import kotlin.math.sqrt

private const val TAG = "AmpEngine"
private const val PRIORITY = 1000

/**
 * Efeitos ligados a uma sessão de áudio.
 * Sessão 0 = mixagem global (todo o áudio do aparelho, onde o fabricante permite).
 * Sessões > 0 = players que anunciam sua sessão (Spotify, YouTube Music, Deezer...).
 */
class SessionFx(val session: Int) {
    private var dp: DynamicsProcessing? = null
    private var eq: Equalizer? = null
    private var loud: LoudnessEnhancer? = null
    private var bassBoost: BassBoost? = null
    private var virt: Virtualizer? = null

    var engineName: String = "indisponível"
        private set
    val works: Boolean get() = dp != null || eq != null || loud != null

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            dp = runCatching { createDp() }
                .onFailure { Log.w(TAG, "DynamicsProcessing falhou na sessão $session", it) }
                .getOrNull()
        }
        if (dp != null) {
            engineName = "DynamicsProcessing 10 bandas"
        } else {
            eq = runCatching { Equalizer(PRIORITY, session) }
                .onFailure { Log.w(TAG, "Equalizer falhou na sessão $session", it) }.getOrNull()
            loud = runCatching { LoudnessEnhancer(session) }
                .onFailure { Log.w(TAG, "LoudnessEnhancer falhou na sessão $session", it) }.getOrNull()
            bassBoost = runCatching {
                BassBoost(PRIORITY, session).let { if (it.strengthSupported) it else { it.release(); null } }
            }.getOrNull()
            engineName = when {
                eq != null -> "Equalizador clássico ${eq!!.numberOfBands} bandas"
                loud != null -> "Somente amplificador"
                else -> "indisponível"
            }
        }
        virt = runCatching {
            Virtualizer(PRIORITY, session).let { if (it.strengthSupported) it else { it.release(); null } }
        }.getOrNull()
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun createDp(): DynamicsProcessing {
        val cfg = DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            2,                       // canais
            true, BAND_FREQS.size,   // pré-EQ
            false, 0,                // multibanda (não usado)
            false, 0,                // pós-EQ (não usado)
            true,                    // limitador
        ).build()
        return DynamicsProcessing(PRIORITY, session, cfg)
    }

    fun apply(s: EqSettings) {
        dp?.let { runCatching { applyDp(it, s) }.onFailure { e -> Log.w(TAG, "apply DP", e) } }

        eq?.let { e ->
            runCatching {
                val range = e.bandLevelRange
                val lo = range[0].toInt()
                val hi = range[1].toInt()
                val curveHasBass = bassBoost == null
                for (b in 0 until e.numberOfBands.toInt()) {
                    val fHz = e.getCenterFreq(b.toShort()) / 1000.0
                    val mb = (s.dbAt(fHz, curveHasBass) * 100f).roundToInt().coerceIn(lo, hi)
                    e.setBandLevel(b.toShort(), mb.toShort())
                }
                e.setEnabled(s.enabled)
            }.onFailure { Log.w(TAG, "apply EQ", it) }
        }
        loud?.let { l ->
            runCatching {
                l.setTargetGain((s.gainDb * 100f).roundToInt())
                l.setEnabled(s.enabled && s.gainDb > 0f)
            }
        }
        bassBoost?.let { bb ->
            runCatching {
                bb.setStrength((s.bass * 1000f).roundToInt().coerceIn(0, 1000).toShort())
                bb.setEnabled(s.enabled && s.bass > 0f)
            }
        }
        virt?.let { v ->
            runCatching {
                v.setStrength((s.spatial * 1000f).roundToInt().coerceIn(0, 1000).toShort())
                v.setEnabled(s.enabled && s.spatial > 0f)
            }
        }
    }

    @RequiresApi(Build.VERSION_CODES.P)
    private fun applyDp(d: DynamicsProcessing, s: EqSettings) {
        val curve = s.curveDb(includeBass = true)
        val n = BAND_FREQS.size
        for (i in 0 until n) {
            // cutoff = limite superior da banda (média geométrica entre centros)
            val cutoff = if (i < n - 1) {
                sqrt(BAND_FREQS[i].toDouble() * BAND_FREQS[i + 1].toDouble()).toFloat()
            } else 20000f
            d.setPreEqBandAllChannelsTo(i, DynamicsProcessing.EqBand(true, cutoff, curve[i]))
        }
        d.setInputGainAllChannelsTo(s.gainDb)
        d.setLimiterAllChannelsTo(
            DynamicsProcessing.Limiter(
                true,          // inUse
                s.limiter,     // enabled
                0,             // linkGroup
                1f,            // attack ms
                60f,           // release ms
                10f,           // ratio
                -1f,           // threshold dBFS
                0f,            // postGain
            )
        )
        d.setEnabled(s.enabled)
    }

    fun release() {
        listOf(dp, eq, loud, bassBoost, virt).forEach { fx ->
            runCatching { fx?.setEnabled(false); fx?.release() }
        }
        dp = null; eq = null; loud = null; bassBoost = null; virt = null
    }
}

/** Gerencia todas as sessões ativas. Usar sempre na thread principal. */
class AudioEngine {
    private val sessions = LinkedHashMap<Int, SessionFx>()
    private var last: EqSettings? = null

    fun attach(session: Int) {
        if (sessions.containsKey(session)) return
        val fx = SessionFx(session)
        if (!fx.works && session != 0) { fx.release(); return }
        sessions[session] = fx
        last?.let(fx::apply)
        publish()
    }

    fun detach(session: Int) {
        if (session == 0) return
        sessions.remove(session)?.release()
        publish()
    }

    fun apply(s: EqSettings) {
        last = s
        sessions.values.forEach { it.apply(s) }
    }

    fun releaseAll() {
        sessions.values.forEach { it.release() }
        sessions.clear()
        EqRepository.setEngineStatus("Desligado")
    }

    private fun publish() {
        val global = sessions[0]
        val apps = sessions.keys.count { it != 0 }
        val g = if (global?.works == true) "Global: ${global.engineName}" else "Global: não suportado neste aparelho"
        val a = if (apps > 0) " • $apps player(s) conectado(s)" else ""
        EqRepository.setEngineStatus(g + a)
    }
}
