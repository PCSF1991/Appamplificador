package com.pcsf.amplificador

import android.Manifest
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat

class MainActivity : ComponentActivity() {

    private val notifPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { turnOn() }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) =
            OutputMonitor.refresh(this@MainActivity)

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) =
            OutputMonitor.refresh(this@MainActivity)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        EqRepository.init(this)
        OutputMonitor.refresh(this)
        if (EqRepository.settings.value.enabled) AmpService.start(this)

        setContent {
            AmpTheme {
                AmpScreen(onPowerChange = ::setPower)
            }
        }
    }

    override fun onStart() {
        super.onStart()
        getSystemService(AudioManager::class.java)
            .registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        OutputMonitor.refresh(this)
    }

    override fun onStop() {
        getSystemService(AudioManager::class.java).unregisterAudioDeviceCallback(deviceCallback)
        super.onStop()
    }

    private fun setPower(on: Boolean) {
        if (!on) {
            EqRepository.update { it.copy(enabled = false) }
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            notifPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            turnOn()
        }
    }

    private fun turnOn() {
        EqRepository.update { it.copy(enabled = true) }
        AmpService.start(this)
    }
}
