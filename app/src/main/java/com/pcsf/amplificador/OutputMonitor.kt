package com.pcsf.amplificador

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager

/** Descobre para onde o áudio de mídia está saindo (Bluetooth, fone com fio, alto-falante). */
object OutputMonitor {
    private val BT_TYPES = setOf(
        AudioDeviceInfo.TYPE_BLUETOOTH_A2DP,
        AudioDeviceInfo.TYPE_BLE_HEADSET,
        AudioDeviceInfo.TYPE_BLE_SPEAKER,
    )
    private val WIRED_TYPES = setOf(
        AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
        AudioDeviceInfo.TYPE_WIRED_HEADSET,
        AudioDeviceInfo.TYPE_USB_HEADSET,
    )

    fun refresh(ctx: Context) {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return
        val outs = am.getDevices(AudioManager.GET_DEVICES_OUTPUTS)

        outs.firstOrNull { it.type in BT_TYPES }?.let { d ->
            val name = d.productName?.toString()?.takeIf { it.isNotBlank() } ?: "Bluetooth"
            EqRepository.onOutputChanged("bt:$name", "Bluetooth: $name", true)
            return
        }
        outs.firstOrNull { it.type in WIRED_TYPES }?.let {
            EqRepository.onOutputChanged("wired", "Fone com fio / USB", false)
            return
        }
        EqRepository.onOutputChanged("speaker", "Alto-falante do celular", false)
    }
}
