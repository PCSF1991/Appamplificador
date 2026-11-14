package com.pcsf.amplificador

import android.content.Context
import android.content.SharedPreferences
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

/**
 * Estado único do app (configurações, dispositivo de saída, status do motor),
 * compartilhado entre a tela e o serviço.
 */
object EqRepository {
    private const val KEY_CURRENT = "current"
    private const val KEY_PER_DEVICE = "perDevice"
    private const val PROFILE = "profile_"

    private lateinit var prefs: SharedPreferences
    private var initialized = false
    private var currentKey: String? = null

    private val _settings = MutableStateFlow(EqSettings())
    val settings: StateFlow<EqSettings> = _settings

    private val _output = MutableStateFlow("—")
    val output: StateFlow<String> = _output

    private val _isBluetooth = MutableStateFlow(false)
    val isBluetooth: StateFlow<Boolean> = _isBluetooth

    private val _engine = MutableStateFlow("Desligado")
    val engine: StateFlow<String> = _engine

    private val _perDevice = MutableStateFlow(true)
    val perDevice: StateFlow<Boolean> = _perDevice

    @Synchronized
    fun init(ctx: Context) {
        if (initialized) return
        prefs = ctx.applicationContext.getSharedPreferences("amp", Context.MODE_PRIVATE)
        _settings.value = prefs.getString(KEY_CURRENT, null)?.let(EqSettings::fromJson) ?: EqSettings()
        _perDevice.value = prefs.getBoolean(KEY_PER_DEVICE, true)
        initialized = true
    }

    @Synchronized
    fun update(transform: (EqSettings) -> EqSettings) {
        val n = transform(_settings.value)
        if (n == _settings.value) return
        _settings.value = n
        val e = prefs.edit().putString(KEY_CURRENT, n.toJson())
        if (_perDevice.value) currentKey?.let { e.putString(PROFILE + it, n.toJson()) }
        e.apply()
    }

    @Synchronized
    fun setPerDevice(on: Boolean) {
        _perDevice.value = on
        val e = prefs.edit().putBoolean(KEY_PER_DEVICE, on)
        if (on) currentKey?.let { e.putString(PROFILE + it, _settings.value.toJson()) }
        e.apply()
    }

    /** Chamado quando a saída de áudio muda (ex.: fone Bluetooth conectou). */
    @Synchronized
    fun onOutputChanged(key: String, label: String, bluetooth: Boolean) {
        _output.value = label
        _isBluetooth.value = bluetooth
        if (key == currentKey) return
        currentKey = key
        if (!_perDevice.value) return
        val saved = prefs.getString(PROFILE + key, null)
        if (saved != null) {
            val enabled = _settings.value.enabled
            _settings.value = EqSettings.fromJson(saved).copy(enabled = enabled)
            prefs.edit().putString(KEY_CURRENT, _settings.value.toJson()).apply()
        } else {
            prefs.edit().putString(PROFILE + key, _settings.value.toJson()).apply()
        }
    }

    fun setEngineStatus(text: String) {
        _engine.value = text
    }
}
