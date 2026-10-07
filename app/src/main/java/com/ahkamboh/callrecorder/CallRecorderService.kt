package com.ahkamboh.callrecorder

import android.Manifest
import android.accessibilityservice.AccessibilityService
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.provider.CallLog
import android.telephony.TelephonyCallback
import android.telephony.TelephonyManager
import android.util.Log
import android.view.accessibility.AccessibilityEvent

/**
 * Always-on call watcher. The system keeps an enabled accessibility service bound,
 * and binds it with the capabilities that let it use the microphone during a call.
 *
 * Two independent signals say "a call is up":
 *  - telephony call state OFF_HOOK (needs READ_PHONE_STATE), the primary one;
 *  - audio mode MODE_IN_CALL (no permission), a backup that also serves as the
 *    hook to register the telephony callback once the permission is granted.
 * Recording runs while either signal is true.
 *
 * The other party's number arrives through the PHONE_STATE broadcast (needs
 * READ_CALL_LOG). When it is known at call start the number rules decide up front;
 * otherwise the call is recorded provisionally and the decision is made from the
 * call log after hang-up, deleting the file if the number is excluded.
 */
class CallRecorderService : AccessibilityService() {

    private lateinit var audioManager: AudioManager
    private lateinit var telephonyManager: TelephonyManager
    private val mainHandler = Handler(Looper.getMainLooper())

    private var recorder: Recorder? = null
    private var callStartedAt = 0L
    private var telephonyInCall = false
    private var modeInCall = false
    private var telephonyRegistered = false
    private var receiverRegistered = false
    private var currentNumber: String? = null
    /** True once the keep/skip decision for the current recording is final. */
    private var decided = false

    private val modeListener = AudioManager.OnModeChangedListener { mode ->
        Log.d(TAG, "audio mode $mode")
        modeInCall = mode == AudioManager.MODE_IN_CALL
        registerTelephonyIfAllowed()
        evaluate()
    }

    private val callStateListener = object : TelephonyCallback(), TelephonyCallback.CallStateListener {
        override fun onCallStateChanged(state: Int) {
            Log.d(TAG, "call state $state")
            telephonyInCall = state == TelephonyManager.CALL_STATE_OFFHOOK
            evaluate()
        }
    }

    private val phoneStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val state = intent.getStringExtra(TelephonyManager.EXTRA_STATE)
            @Suppress("DEPRECATION")
            val number = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER)
            Log.d(TAG, "phone state broadcast $state number=${if (number.isNullOrBlank()) "unknown" else "known"}")
            if (state == TelephonyManager.EXTRA_STATE_IDLE) {
                currentNumber = null
            } else if (!number.isNullOrBlank()) {
                onNumberKnown(number)
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        audioManager = getSystemService(AUDIO_SERVICE) as AudioManager
        telephonyManager = getSystemService(TELEPHONY_SERVICE) as TelephonyManager
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        audioManager.addOnModeChangedListener(mainExecutor, modeListener)
        modeInCall = audioManager.mode == AudioManager.MODE_IN_CALL
        if (!receiverRegistered) {
            // Protected system broadcast: only the system can send it, so exporting is safe.
            registerReceiver(phoneStateReceiver, IntentFilter(TelephonyManager.ACTION_PHONE_STATE_CHANGED), RECEIVER_EXPORTED)
            receiverRegistered = true
        }
        registerTelephonyIfAllowed()
        evaluate()
        Log.i(TAG, "accessibility service connected")
    }

    override fun onDestroy() {
        audioManager.removeOnModeChangedListener(modeListener)
        if (telephonyRegistered) telephonyManager.unregisterTelephonyCallback(callStateListener)
        if (receiverRegistered) unregisterReceiver(phoneStateReceiver)
        stopRecording()
        super.onDestroy()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit
    override fun onInterrupt() = Unit

    private fun registerTelephonyIfAllowed() {
        if (telephonyRegistered) return
        if (checkSelfPermission(Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) return
        try {
            // Delivers the current state immediately on registration.
            telephonyManager.registerTelephonyCallback(mainExecutor, callStateListener)
            telephonyRegistered = true
            Log.i(TAG, "telephony callback registered")
        } catch (e: SecurityException) {
            Log.w(TAG, "telephony callback refused", e)
        }
    }

    private fun evaluate() {
        if (telephonyInCall || modeInCall) startRecording() else stopRecording()
    }

    private fun onNumberKnown(number: String) {
        currentNumber = number
        val r = recorder ?: return
        if (decided) return
        decided = true
        if (!Rules.load(this).shouldRecord(number)) {
            Log.i(TAG, "number excluded by rules, discarding")
            recorder = null
            r.discard()
            cancelNotification()
        }
    }

    private fun startRecording() {
        if (recorder != null) return
        if (!Prefs.enabled(this)) {
            Log.i(TAG, "recording switched off, ignoring call")
            return
        }
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "RECORD_AUDIO not granted, not recording")
            return
        }
        val number = currentNumber
        val rules = Rules.load(this)
        if (number != null) {
            if (!rules.shouldRecord(number)) {
                Log.i(TAG, "number excluded by rules, not recording")
                return
            }
            decided = true
        } else {
            // Record now, decide from the call log after hang-up.
            decided = rules.mode == RuleMode.ALL
        }
        callStartedAt = System.currentTimeMillis()
        val r = Recorder(this)
        if (r.start(Prefs.source(this))) {
            recorder = r
            showNotification(r.sourceName)
        } else {
            Log.e(TAG, "no audio source could be started")
        }
    }

    private fun stopRecording() {
        val r = recorder ?: return
        recorder = null
        val startedAt = callStartedAt
        val keepDecided = decided
        val rules = Rules.load(this)
        currentNumber = null
        r.stop()
        cancelNotification()
        // The call-log row appears shortly after hang-up; use it to name and place the file.
        mainHandler.postDelayed({
            Thread {
                val info = lookupCall(startedAt)
                if (keepDecided || rules.shouldRecord(info?.number)) {
                    r.finish(info)
                } else {
                    Log.i(TAG, "number excluded by rules, deleting recording")
                    r.discard()
                }
            }.start()
        }, 1500)
    }

    private fun lookupCall(since: Long): CallInfo? {
        if (checkSelfPermission(Manifest.permission.READ_CALL_LOG) != PackageManager.PERMISSION_GRANTED) return null
        val projection = arrayOf(CallLog.Calls.NUMBER, CallLog.Calls.TYPE)
        // Incoming calls are logged with their ring time, which precedes our start.
        val selection = "${CallLog.Calls.DATE} >= ?"
        val args = arrayOf((since - 120_000L).toString())
        return try {
            contentResolver.query(CallLog.Calls.CONTENT_URI, projection, selection, args, "${CallLog.Calls.DATE} DESC")
                ?.use { c -> if (c.moveToFirst()) CallInfo(c.getString(0) ?: "", c.getInt(1)) else null }
        } catch (e: Exception) {
            Log.w(TAG, "call log lookup failed", e)
            null
        }
    }

    private fun showNotification(source: String) {
        val nm = getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL, getString(R.string.notif_channel), NotificationManager.IMPORTANCE_LOW)
        )
        val open = PendingIntent.getActivity(
            this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_IMMUTABLE
        )
        val n = Notification.Builder(this, CHANNEL)
            .setSmallIcon(R.drawable.ic_mic)
            .setContentTitle(getString(R.string.notif_title))
            .setContentText(source)
            .setOngoing(true)
            .setContentIntent(open)
            .build()
        nm.notify(NOTIF_ID, n)
    }

    private fun cancelNotification() {
        getSystemService(NotificationManager::class.java).cancel(NOTIF_ID)
    }

    companion object {
        private const val TAG = "CallRecorder"
        private const val CHANNEL = "recording"
        private const val NOTIF_ID = 1
    }
}
