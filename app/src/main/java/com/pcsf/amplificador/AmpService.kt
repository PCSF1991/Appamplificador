package com.pcsf.amplificador

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.audiofx.AudioEffect
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/** Serviço em primeiro plano que mantém os efeitos de áudio ativos com a tela desligada. */
class AmpService : Service() {

    companion object {
        private const val CHANNEL = "amp"
        private const val NOTIF_ID = 1
        const val ACTION_STOP = "com.pcsf.amplificador.STOP"

        fun start(ctx: Context) {
            ContextCompat.startForegroundService(ctx, Intent(ctx, AmpService::class.java))
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val engine = AudioEngine()
    private lateinit var am: AudioManager

    private val sessionReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context, i: Intent) {
            val id = i.getIntExtra(AudioEffect.EXTRA_AUDIO_SESSION, AudioEffect.ERROR_BAD_VALUE)
            if (id <= 0) return
            when (i.action) {
                AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION -> engine.attach(id)
                AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION -> engine.detach(id)
            }
        }
    }

    private val deviceCallback = object : AudioDeviceCallback() {
        override fun onAudioDevicesAdded(addedDevices: Array<out AudioDeviceInfo>) =
            OutputMonitor.refresh(this@AmpService)

        override fun onAudioDevicesRemoved(removedDevices: Array<out AudioDeviceInfo>) =
            OutputMonitor.refresh(this@AmpService)
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        EqRepository.init(this)
        createChannel()
        try {
            ServiceCompat.startForeground(
                this, NOTIF_ID, buildNotification(EqRepository.settings.value, EqRepository.output.value),
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK else 0,
            )
        } catch (e: Exception) {
            Log.e("AmpService", "startForeground falhou", e)
            stopSelf()
            return
        }

        am = getSystemService(AudioManager::class.java)
        am.registerAudioDeviceCallback(deviceCallback, Handler(Looper.getMainLooper()))
        OutputMonitor.refresh(this)

        val filter = IntentFilter().apply {
            addAction(AudioEffect.ACTION_OPEN_AUDIO_EFFECT_CONTROL_SESSION)
            addAction(AudioEffect.ACTION_CLOSE_AUDIO_EFFECT_CONTROL_SESSION)
        }
        ContextCompat.registerReceiver(this, sessionReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        engine.attach(0) // mixagem global

        scope.launch {
            combine(EqRepository.settings, EqRepository.output) { s, out -> s to out }
                .collect { (s, out) ->
                    if (!s.enabled) {
                        stopSelf()
                    } else {
                        engine.apply(s)
                        getSystemService(NotificationManager::class.java)
                            .notify(NOTIF_ID, buildNotification(s, out))
                    }
                }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            EqRepository.update { it.copy(enabled = false) }
        }
        return START_STICKY
    }

    override fun onDestroy() {
        scope.cancel()
        runCatching { unregisterReceiver(sessionReceiver) }
        if (::am.isInitialized) runCatching { am.unregisterAudioDeviceCallback(deviceCallback) }
        engine.releaseAll()
        super.onDestroy()
    }

    private fun createChannel() {
        val ch = NotificationChannel(CHANNEL, "Amplificador ativo", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Mantém o amplificador e o equalizador funcionando"
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(ch)
    }

    private fun buildNotification(s: EqSettings, output: String): Notification {
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val stop = PendingIntent.getService(
            this, 1, Intent(this, AmpService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val gain = "%+.1f dB".format(s.gainDb)
        return NotificationCompat.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_stat_amp)
            .setContentTitle("Amplificador $gain • ${s.preset}")
            .setContentText(output)
            .setContentIntent(open)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .addAction(0, "Desligar", stop)
            .build()
    }
}
