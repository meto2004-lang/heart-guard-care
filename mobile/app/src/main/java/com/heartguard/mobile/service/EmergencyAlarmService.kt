package com.heartguard.mobile.service

import android.annotation.SuppressLint
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.graphics.Color
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.media.ToneGenerator
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.heartguard.mobile.R
import com.heartguard.mobile.ui.alarm.EmergencyAlarmActivity
import com.heartguard.shared.models.AlertType
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * حالة إنذار الطوارئ - تُقرأ من الواجهات (لوحة التحكم وشاشة الإنذار).
 */
sealed class SosAlarmState {
    object Idle : SosAlarmState()
    data class Active(
        val alertType: String,
        val message: String,
        val startedAt: Long
    ) : SosAlarmState()
}

/**
 * خدمة أمامية (Foreground Service) تشغّل صفارة إنذار عالية ومستمرة على الجوال
 * عند الضغط على زر الطوارئ SOS (من الساعة أو من تطبيق الجوال).
 *
 * ما تفعله الخدمة:
 *  1. ترفع صوت تيار الإنذار (STREAM_ALARM) إلى أقصى درجة.
 *  2. تشغّل صفارة الطوارئ (res/raw/emergency_siren.wav) في حلقة مستمرة
 *     عبر USAGE_ALARM حتى يسمعها من في المكان حتى لو كان الهاتف صامتاً.
 *  3. تهتز باستمرار + تضيء الشاشة عبر شاشة الإنذار الكاملة.
 *  4. تعرض إشعاراً بأولوية قصوى مع زر "إيقاف الإنذار".
 *  5. تتوقف تلقائياً بعد [AUTO_STOP_MINUTES] دقائق حفاظاً على البطارية.
 */
class EmergencyAlarmService : Service() {

    companion object {
        private const val TAG = "EmergencyAlarmService"

        const val ACTION_START_ALARM = "com.heartguard.mobile.action.START_SOS_ALARM"
        const val ACTION_STOP_ALARM = "com.heartguard.mobile.action.STOP_SOS_ALARM"
        const val EXTRA_ALERT_TYPE = "extra_alert_type"
        const val EXTRA_MESSAGE = "extra_message"

        const val ALARM_CHANNEL_ID = "sos_alarm_channel"
        const val ALARM_NOTIFICATION_ID = 9111

        /** مدة الإنذار القصوى قبل الإيقاف التلقائي */
        const val AUTO_STOP_MINUTES = 5
        private const val AUTO_STOP_TIMEOUT_MS = AUTO_STOP_MINUTES * 60_000L
        private const val WAKE_LOCK_TIMEOUT_MS = AUTO_STOP_TIMEOUT_MS + 60_000L

        /**
         * التنبيه يصل عادةً مرتين (DataClient + MessageClient) خلال أجزاء من الثانية،
         * وهذه النافذة تمنع إعادة تشغيل الإنذار أو تكرار الإشعار.
         */
        private const val DUPLICATE_TRIGGER_WINDOW_MS = 3_000L

        private const val TONE_BEEP_DURATION_MS = 900
        private const val TONE_BEEP_INTERVAL_MS = 1_050L

        private val VIBRATION_PATTERN = longArrayOf(0, 700, 250, 700, 250, 700)
        private const val VIBRATION_REPEAT_INDEX = 1

        private val _alarmState = MutableStateFlow<SosAlarmState>(SosAlarmState.Idle)
        val alarmState: StateFlow<SosAlarmState> = _alarmState.asStateFlow()

        val isAlarming: Boolean
            get() = _alarmState.value is SosAlarmState.Active

        private var lastTriggerAt = 0L

        /**
         * يشغّل إنذار الطوارئ بأعلى صوت ممكن.
         * تُستدعى من [DataLayerListenerService] عند وصول SOS من الساعة،
         * ومن لوحة التحكم عند الضغط على زر SOS في الجوال.
         */
        @SuppressLint("MissingPermission")
        fun trigger(context: Context, alertType: String?, message: String?) {
            val appContext = context.applicationContext
            val now = System.currentTimeMillis()

            if (isAlarming) {
                Log.d(TAG, "Alarm is already active - ignoring duplicate trigger")
                return
            }
            if (now - lastTriggerAt < DUPLICATE_TRIGGER_WINDOW_MS) {
                Log.d(TAG, "Ignoring duplicate SOS trigger (${now - lastTriggerAt}ms since last)")
                return
            }
            lastTriggerAt = now

            val text = message?.takeIf { it.isNotBlank() }
                ?: appContext.getString(R.string.sos_alarm_default_message)
            val type = alertType ?: AlertType.SOS_MANUAL.name

            Log.w(TAG, "Triggering loud SOS alarm: type=$type message=$text")

            // 1) إشعار بأولوية قصوى + Full-Screen Intent:
            //    يفتح شاشة الإنذار والشاشة مقفلة، ويشغّل صوت الصفارة من قناة الإشعار
            //    حتى لو منع النظام بدء خدمة من الخلفية.
            if (hasNotificationPermission(appContext)) {
                try {
                    val notification = buildAlarmNotification(appContext, text, ongoing = false)
                    NotificationManagerCompat.from(appContext).notify(ALARM_NOTIFICATION_ID, notification)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to post alarm notification", e)
                }
            } else {
                Log.w(TAG, "POST_NOTIFICATIONS not granted - relying on the foreground service siren")
            }

            // 2) الخدمة الأمامية: صفارة مستمرة بلا توقف + اهتزاز + Wake Lock.
            try {
                ContextCompat.startForegroundService(
                    appContext,
                    startIntent(appContext, type, text)
                )
            } catch (e: Exception) {
                // Android 12+ قد يمنع بدء خدمة أمامية من الخلفية -> نفتح شاشة الإنذار
                // مباشرة (وهي في المقدمة فتستطيع تشغيل الخدمة بلا قيود).
                Log.e(TAG, "Foreground start blocked, launching alarm screen as fallback", e)
                launchAlarmScreen(appContext, type, text)
            }
        }

        /** يوقف الإنذار من أي مكان (الإشعار، شاشة الإنذار، لوحة التحكم). */
        fun stop(context: Context) {
            val appContext = context.applicationContext
            try {
                val intent = Intent(appContext, EmergencyAlarmService::class.java).apply {
                    action = ACTION_STOP_ALARM
                }
                ContextCompat.startForegroundService(appContext, intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to request alarm stop", e)
            }
        }

        private fun hasNotificationPermission(context: Context): Boolean =
            Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
                ContextCompat.checkSelfPermission(
                    context,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) == android.content.pm.PackageManager.PERMISSION_GRANTED

        private fun startIntent(context: Context, alertType: String, message: String) =
            Intent(context, EmergencyAlarmService::class.java).apply {
                action = ACTION_START_ALARM
                putExtra(EXTRA_ALERT_TYPE, alertType)
                putExtra(EXTRA_MESSAGE, message)
            }

        /** فتح شاشة الإنذار الكاملة (تُستخدم كخيار احتياطي). */
        fun launchAlarmScreen(context: Context, alertType: String?, message: String) {
            try {
                val intent = Intent(context.applicationContext, EmergencyAlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_ALERT_TYPE, alertType)
                    putExtra(EXTRA_MESSAGE, message)
                }
                context.applicationContext.startActivity(intent)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to launch alarm screen", e)
            }
        }

        /** قناة إشعارات الطوارئ: صوت الصفارة + اهتزاز + تجاوز "عدم الإزعاج". */
        fun ensureAlarmChannel(context: Context) {
            val notificationManager =
                context.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager ?: return
            try {
                val channel = NotificationChannel(
                    ALARM_CHANNEL_ID,
                    context.getString(R.string.sos_alarm_channel_name),
                    NotificationManager.IMPORTANCE_HIGH
                ).apply {
                    description = context.getString(R.string.sos_alarm_channel_description)
                    enableVibration(true)
                    vibrationPattern = VIBRATION_PATTERN
                    enableLights(true)
                    lightColor = Color.RED
                    lockscreenVisibility = Notification.VISIBILITY_PUBLIC
                    setBypassDnd(true)
                    setSound(sirenUri(context), alarmAudioAttributes())
                }
                notificationManager.createNotificationChannel(channel)
            } catch (e: Exception) {
                Log.e(TAG, "Failed to create alarm channel", e)
            }
        }

        fun buildAlarmNotification(context: Context, message: String, ongoing: Boolean): Notification {
            ensureAlarmChannel(context)

            val contentIntent = PendingIntent.getActivity(
                context,
                1001,
                Intent(context, EmergencyAlarmActivity::class.java).apply {
                    flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
                    putExtra(EXTRA_MESSAGE, message)
                },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            val stopIntent = PendingIntent.getService(
                context,
                1002,
                Intent(context, EmergencyAlarmService::class.java).apply { action = ACTION_STOP_ALARM },
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            return NotificationCompat.Builder(context, ALARM_CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setContentTitle(context.getString(R.string.sos_alarm_notification_title))
                .setContentText(message)
                .setStyle(NotificationCompat.BigTextStyle().bigText(message))
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
                .setColor(Color.RED)
                .setColorized(true)
                .setVibrate(VIBRATION_PATTERN)
                .setOngoing(ongoing)
                // لا نعيد تشغيل صوت الإشعار لأن الخدمة تشغّل الصفارة باستمرار
                .setOnlyAlertOnce(ongoing)
                .setAutoCancel(!ongoing)
                .setContentIntent(contentIntent)
                .setFullScreenIntent(contentIntent, true)
                .addAction(
                    android.R.drawable.ic_menu_close_clear_cancel,
                    context.getString(R.string.sos_alarm_stop),
                    stopIntent
                )
                .build()
        }

        private fun sirenUri(context: Context): Uri =
            Uri.parse("android.resource://${context.packageName}/${R.raw.emergency_siren}")

        private fun alarmAudioAttributes(): AudioAttributes = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ALARM)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
    }

    private val handler = Handler(Looper.getMainLooper())

    private var audioManager: AudioManager? = null
    private var mediaPlayer: MediaPlayer? = null
    private var toneGenerator: ToneGenerator? = null
    private var vibrator: Vibrator? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private var audioFocusRequest: AudioFocusRequest? = null
    private var notificationManager: NotificationManager? = null

    private var foregroundStarted = false
    private var alarmCleanupDone = false
    private var previousAlarmVolume: Int = -1
    private var previousInterruptionFilter: Int = -1

    private val autoStopRunnable = Runnable { stopAlarm("auto-timeout") }

    private val toneFallbackRunnable = object : Runnable {
        override fun run() {
            if (!isAlarming) return
            try {
                toneGenerator?.startTone(ToneGenerator.TONE_CDMA_EMERGENCY_RINGBACK, TONE_BEEP_DURATION_MS)
            } catch (e: Exception) {
                Log.e(TAG, "Tone fallback failed", e)
            }
            handler.postDelayed(this, TONE_BEEP_INTERVAL_MS)
        }
    }

    private val audioFocusListener = AudioManager.OnAudioFocusChangeListener { change ->
        when (change) {
            AudioManager.AUDIOFOCUS_GAIN -> resumeSiren()
            AudioManager.AUDIOFOCUS_LOSS,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT,
            AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK -> {
                // مكالمة طوارئ أو تنبيه آخر أخذ الصوت -> نوقف مؤقتاً ونعود بعده
                Log.w(TAG, "Audio focus lost ($change) - pausing siren temporarily")
                pauseSiren()
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        // يجب استدعاء startForeground فوراً عند البدء عبر startForegroundService
        val message = intent?.getStringExtra(EXTRA_MESSAGE)
            ?: currentMessage()
            ?: getString(R.string.sos_alarm_default_message)

        goForeground(buildAlarmNotification(this, message, ongoing = true))

        when (intent?.action) {
            ACTION_STOP_ALARM -> stopAlarm("stop-action")
            else -> startAlarm(
                intent?.getStringExtra(EXTRA_ALERT_TYPE) ?: AlertType.SOS_MANUAL.name,
                message
            )
        }
        return START_STICKY
    }

    override fun onDestroy() {
        cleanup("service-destroyed")
        super.onDestroy()
    }

    // ------------------------------------------------------------------ التشغيل

    private fun startAlarm(alertType: String, message: String) {
        val current = _alarmState.value
        if (current is SosAlarmState.Active) {
            // الإنذار يعمل بالفعل: لا نعيد تشغيل الصفارة، فقط نحدّث الرسالة وعداد الإيقاف التلقائي
            Log.d(TAG, "Alarm already running - refreshing state only")
            _alarmState.value = current.copy(alertType = alertType, message = message)
            goForeground(buildAlarmNotification(this, message, ongoing = true))
            handler.removeCallbacks(autoStopRunnable)
            handler.postDelayed(autoStopRunnable, AUTO_STOP_TIMEOUT_MS)
            return
        }

        Log.w(TAG, "Starting loud SOS alarm: $alertType - $message")

        _alarmState.value = SosAlarmState.Active(
            alertType = alertType,
            message = message,
            startedAt = System.currentTimeMillis()
        )
        alarmCleanupDone = false

        audioManager = getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager

        acquireWakeLock()
        bypassDoNotDisturb()
        maximizeAlarmVolume()
        requestAudioFocus()
        startSiren()
        startVibration()

        handler.removeCallbacks(autoStopRunnable)
        handler.postDelayed(autoStopRunnable, AUTO_STOP_TIMEOUT_MS)
    }

    private fun stopAlarm(reason: String) {
        Log.i(TAG, "Stopping SOS alarm ($reason)")
        cleanup(reason)
        if (foregroundStarted) {
            stopForeground(STOP_FOREGROUND_REMOVE)
        }
        stopSelf()
    }

    /** إيقاف الصوت والاهتزاز وإرجاع الإعدادات - آمنة للاستدعاء أكثر من مرة. */
    private fun cleanup(reason: String) {
        if (alarmCleanupDone) return
        alarmCleanupDone = true

        handler.removeCallbacks(autoStopRunnable)
        handler.removeCallbacks(toneFallbackRunnable)
        stopSiren()
        stopVibration()
        restoreAlarmVolume()
        restoreDoNotDisturb()
        abandonAudioFocus()
        releaseWakeLock()

        _alarmState.value = SosAlarmState.Idle
        lastTriggerAt = 0L

        try {
            notificationManager?.cancel(ALARM_NOTIFICATION_ID)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to cancel notification", e)
        }
        Log.i(TAG, "Alarm cleanup finished ($reason)")
    }

    // ------------------------------------------------------------------ الصوت

    private fun maximizeAlarmVolume() {
        val am = audioManager ?: return
        try {
            // نحفظ مستوى الصوت الأصلي مرة واحدة فقط حتى لا نسجّل "أقصى صوت" كمستوى أصلي
            if (previousAlarmVolume < 0) {
                previousAlarmVolume = am.getStreamVolume(AudioManager.STREAM_ALARM)
            }
            val maxVolume = am.getStreamMaxVolume(AudioManager.STREAM_ALARM)
            am.setStreamVolume(AudioManager.STREAM_ALARM, maxVolume, 0)
            Log.i(TAG, "Alarm volume raised to max ($maxVolume)")
            if (maxVolume == 0) {
                Log.w(TAG, "Device reports max alarm volume = 0")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to raise alarm volume", e)
        }
    }

    private fun restoreAlarmVolume() {
        val am = audioManager ?: return
        if (previousAlarmVolume < 0) return
        try {
            am.setStreamVolume(AudioManager.STREAM_ALARM, previousAlarmVolume, 0)
            Log.i(TAG, "Alarm volume restored to $previousAlarmVolume")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore alarm volume", e)
        } finally {
            previousAlarmVolume = -1
        }
    }

    private fun startSiren() {
        stopSiren()
        if (startMediaPlayerSiren()) return
        if (startRingtoneSiren()) return
        startToneGeneratorSiren()
    }

    /** الخيار الأساسي: ملف الصفارة المدمج في التطبيق (مستمر بلا انقطاع). */
    private fun startMediaPlayerSiren(): Boolean {
        return try {
            val player = MediaPlayer()
            player.setAudioAttributes(alarmAudioAttributes())

            val afd = resources.openRawResourceFd(R.raw.emergency_siren)
            if (afd != null) {
                player.setDataSource(afd.fileDescriptor, afd.startOffset, afd.length)
                afd.close()
            } else {
                player.setDataSource(this, sirenUri(this))
            }

            player.isLooping = true
            player.setVolume(1f, 1f)
            player.setOnErrorListener { _, what, extra ->
                Log.e(TAG, "MediaPlayer error (what=$what, extra=$extra) - switching to tone fallback")
                startToneGeneratorSiren()
                true
            }
            player.prepare()
            player.start()
            mediaPlayer = player
            Log.i(TAG, "Siren playing (MediaPlayer, looping)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "MediaPlayer siren failed", e)
            releaseMediaPlayer()
            false
        }
    }

    /** احتياطي 1: نغمة المنبّه الافتراضية في الجهاز. */
    private fun startRingtoneSiren(): Boolean {
        return try {
            val uri = RingtoneManager.getActualDefaultRingtoneUri(this, RingtoneManager.TYPE_ALARM)
                ?: return false
            val player = MediaPlayer()
            player.setAudioAttributes(alarmAudioAttributes())
            player.setDataSource(this, uri)
            player.isLooping = true
            player.prepare()
            player.start()
            mediaPlayer = player
            Log.i(TAG, "Siren playing (system alarm ringtone fallback)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "Ringtone fallback failed", e)
            releaseMediaPlayer()
            false
        }
    }

    /** احتياطي 2: نغمات ToneGenerator على تيار الإنذار بأقصى درجة. */
    private fun startToneGeneratorSiren() {
        try {
            if (toneGenerator == null) {
                toneGenerator = ToneGenerator(AudioManager.STREAM_ALARM, ToneGenerator.MAX_VOLUME)
            }
            handler.removeCallbacks(toneFallbackRunnable)
            handler.post(toneFallbackRunnable)
            Log.i(TAG, "Siren playing (ToneGenerator fallback)")
        } catch (e: Exception) {
            Log.e(TAG, "ToneGenerator fallback failed", e)
        }
    }

    private fun pauseSiren() {
        try {
            mediaPlayer?.takeIf { it.isPlaying }?.pause()
            handler.removeCallbacks(toneFallbackRunnable)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to pause siren", e)
        }
    }

    private fun resumeSiren() {
        if (!isAlarming) return
        try {
            val player = mediaPlayer
            if (player != null) {
                if (!player.isPlaying) player.start()
            } else if (toneGenerator != null) {
                handler.removeCallbacks(toneFallbackRunnable)
                handler.post(toneFallbackRunnable)
            } else {
                startSiren()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to resume siren", e)
        }
    }

    private fun stopSiren() {
        handler.removeCallbacks(toneFallbackRunnable)
        releaseMediaPlayer()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release tone generator", e)
        }
        toneGenerator = null
    }

    private fun releaseMediaPlayer() {
        try {
            mediaPlayer?.let {
                if (it.isPlaying) it.stop()
                it.reset()
                it.release()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release MediaPlayer", e)
        }
        mediaPlayer = null
    }

    // ------------------------------------------------------------------ الاهتزاز

    private fun startVibration() {
        try {
            val vib = resolveVibrator() ?: return
            vibrator = vib
            if (!vib.hasVibrator()) return
            val effect = VibrationEffect.createWaveform(VIBRATION_PATTERN, VIBRATION_REPEAT_INDEX)
            vib.vibrate(effect)
            Log.i(TAG, "Emergency vibration started")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start vibration", e)
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to stop vibration", e)
        }
        vibrator = null
    }

    private fun resolveVibrator(): Vibrator? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }

    // ------------------------------------------------------------------ التركيز الصوتي

    private fun requestAudioFocus() {
        val am = audioManager ?: return
        try {
            val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN)
                .setAudioAttributes(alarmAudioAttributes())
                .setOnAudioFocusChangeListener(audioFocusListener, handler)
                .setWillPauseWhenDucked(false)
                .build()
            audioFocusRequest = request
            am.requestAudioFocus(request)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to request audio focus", e)
        }
    }

    private fun abandonAudioFocus() {
        val am = audioManager ?: return
        try {
            audioFocusRequest?.let { am.abandonAudioFocusRequest(it) }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to abandon audio focus", e)
        }
        audioFocusRequest = null
    }

    // ------------------------------------------------------------------ الشاشة والوضع

    private fun acquireWakeLock() {
        try {
            val powerManager = getSystemService(Context.POWER_SERVICE) as? PowerManager ?: return
            if (wakeLock?.isHeld == true) return
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "heartguard:sos-alarm"
            ).apply {
                setReferenceCounted(false)
                acquire(WAKE_LOCK_TIMEOUT_MS)
            }
            Log.i(TAG, "Wake lock acquired")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to acquire wake lock", e)
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.takeIf { it.isHeld }?.release()
        } catch (e: Exception) {
            Log.e(TAG, "Failed to release wake lock", e)
        }
        wakeLock = null
    }

    /** محاولة تجاوز "عدم الإزعاج" حتى لا يُكتم صوت الإنذار. */
    private fun bypassDoNotDisturb() {
        val nm = notificationManager ?: return
        try {
            if (!nm.isNotificationPolicyAccessGranted) {
                Log.w(TAG, "No notification-policy access; DND may silence the alarm")
                return
            }
            previousInterruptionFilter = nm.currentInterruptionFilter
            if (previousInterruptionFilter != NotificationManager.INTERRUPTION_FILTER_ALL) {
                nm.setInterruptionFilter(NotificationManager.INTERRUPTION_FILTER_ALL)
                Log.i(TAG, "Do Not Disturb bypassed for the alarm")
            } else {
                previousInterruptionFilter = -1
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to bypass DND", e)
        }
    }

    private fun restoreDoNotDisturb() {
        val nm = notificationManager ?: return
        if (previousInterruptionFilter < 0) return
        try {
            nm.setInterruptionFilter(previousInterruptionFilter)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to restore interruption filter", e)
        } finally {
            previousInterruptionFilter = -1
        }
    }

    // ------------------------------------------------------------------ الإشعار

    private fun goForeground(notification: Notification) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(
                    ALARM_NOTIFICATION_ID,
                    notification,
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PLAYBACK
                )
            } else {
                startForeground(ALARM_NOTIFICATION_ID, notification)
            }
            foregroundStarted = true
        } catch (e: Exception) {
            Log.e(TAG, "startForeground with type failed, retrying without type", e)
            try {
                startForeground(ALARM_NOTIFICATION_ID, notification)
                foregroundStarted = true
            } catch (e2: Exception) {
                Log.e(TAG, "startForeground failed - the alarm sound may be stopped by the system", e2)
            }
        }
    }

    private fun currentMessage(): String? =
        (_alarmState.value as? SosAlarmState.Active)?.message
}
