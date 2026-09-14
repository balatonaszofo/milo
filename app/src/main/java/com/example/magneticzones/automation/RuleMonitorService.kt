package com.example.magneticzones.automation

import android.Manifest
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.magneticzones.BuildConfig
import com.example.magneticzones.MainActivity
import com.example.magneticzones.R
import com.example.magneticzones.classification.NormalizedKnnClassifier
import com.example.magneticzones.classification.TemporalSmoother
import com.example.magneticzones.data.JsonDataCodec
import com.example.magneticzones.data.StoredData
import com.example.magneticzones.model.DetectionConfig
import com.example.magneticzones.model.RawSensorSample
import com.example.magneticzones.processing.FeatureExtractor
import com.example.magneticzones.sensor.AndroidSensorCollector
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.io.File
import java.util.concurrent.ConcurrentHashMap

class RuleMonitorService : Service() {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private lateinit var collector: AndroidSensorCollector
    private val extractor = FeatureExtractor()
    private val classifier = NormalizedKnnClassifier()
    private val smoother = TemporalSmoother(DetectionConfig())
    private val stoveClient = StoveStatusClient(BuildConfig.STOVE_TRACKER_URL)
    private val recentSamples = ArrayDeque<RawSensorSample>()
    private var data = StoredData()
    private var dataModifiedAt = Long.MIN_VALUE
    private var lastClassificationAt = 0L
    private var activeZoneId: String? = null
    private var stoveStatus = StoveStatus("unknown", false, null, null)
    private val wasTriggered = ConcurrentHashMap<String, Boolean>()

    override fun onCreate() {
        super.onCreate()
        createChannels()
        startForeground(MONITOR_NOTIFICATION_ID, monitorNotification("Connecting to Camera Tracker…"))
        collector = AndroidSensorCollector(applicationContext)
        refreshData(force = true)
        collector.start()
        scope.launch { collector.samples.collect(::onSample) }
        scope.launch { pollTracker() }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        refreshData(force = true)
        if (intent?.action == ACTION_TEST_ALERT) {
            val rule = data.automationRules.firstOrNull { it.id == intent.getStringExtra(EXTRA_RULE_ID) }
            val compiled = rule?.let { RuleCompiler.compile(it, data.zones, data.trainingSessions) }
            if (rule != null && compiled is RuleCompilation.Ready) {
                postTestAlert(rule.name, compiled.value.roomName)
            }
        }
        if (data.automationRules.none { it.enabled }) stopSelf()
        return START_STICKY
    }

    override fun onDestroy() {
        collector.stop()
        scope.cancel()
        _snapshot.value = RuleMonitorSnapshot(running = false, trackerConnection = TrackerConnection.OFFLINE)
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private suspend fun pollTracker() {
        while (scope.isActive) {
            refreshData()
            stoveStatus = try {
                stoveClient.fetch()
            } catch (error: Exception) {
                StoveStatus("unknown", false, null, error.message ?: error.javaClass.simpleName)
            }
            publishSnapshot()
            updateMonitorNotification()
            evaluateRules()
            delay(TRACKER_POLL_MILLIS)
        }
    }

    private fun refreshData(force: Boolean = false) {
        val file = File(filesDir, "magnetic_zone_data.json")
        val modified = file.lastModified()
        if (!force && modified == dataModifiedAt) return
        runCatching { if (file.exists()) JsonDataCodec.decode(file.readText()) else StoredData() }.onSuccess { loaded ->
            data = loaded
            dataModifiedAt = modified
            classifier.fit(loaded.zones, loaded.trainingSessions)
            smoother.reset()
        }
    }

    private fun onSample(sample: RawSensorSample) {
        recentSamples.addLast(sample)
        val tenSecondsAgo = sample.timestampNanos - 10_000_000_000L
        while (recentSamples.firstOrNull()?.timestampNanos?.let { it < tenSecondsAgo } == true) recentSamples.removeFirst()
        if (sample.timestampNanos - lastClassificationAt < 500_000_000L) return
        val window = extractor.extract(recentSamples.filter { it.timestampNanos >= sample.timestampNanos - 2_000_000_000L }) ?: return
        lastClassificationAt = sample.timestampNanos
        activeZoneId = smoother.update(classifier.classify(window, DetectionConfig().unknownDistanceScale)).activeZoneId
        publishSnapshot()
        evaluateRules()
    }

    private fun evaluateRules() {
        val notificationsAllowed = notificationsAllowed()
        data.automationRules.forEach { rule ->
            val compiled = RuleCompiler.compile(rule, data.zones, data.trainingSessions)
            val condition = rule.enabled && compiled is RuleCompilation.Ready && stoveStatus.connected && notificationsAllowed &&
                stoveStatus.state == "on" && activeZoneId == compiled.value.roomId
            val previous = wasTriggered.put(rule.id, condition) ?: false
            if (condition && !previous && outsideCooldown(rule.id)) {
                val ready = (compiled as RuleCompilation.Ready).value
                if (postAlert(rule.id, rule.name, ready.roomName)) rememberAlert(rule.id)
            }
        }
    }

    private fun outsideCooldown(ruleId: String): Boolean {
        val preferences = getSharedPreferences(PREFERENCES, MODE_PRIVATE)
        val key = "last_alert_$ruleId"
        val now = System.currentTimeMillis()
        return now - preferences.getLong(key, 0L) >= ALERT_COOLDOWN_MILLIS
    }

    private fun rememberAlert(ruleId: String) {
        getSharedPreferences(PREFERENCES, MODE_PRIVATE).edit()
            .putLong("last_alert_$ruleId", System.currentTimeMillis()).apply()
    }

    private fun postAlert(ruleId: String, ruleName: String, roomName: String): Boolean {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Stove appears to be on")
            .setContentText("You’re at $roomName. $ruleName")
            .setStyle(NotificationCompat.BigTextStyle().bigText("Camera Tracker reports that the stove appears on while Milo recognizes $roomName."))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return postNotification(ruleId.hashCode(), notification)
    }

    private fun postTestAlert(ruleName: String, roomName: String): Boolean {
        val notification = NotificationCompat.Builder(this, ALERT_CHANNEL)
            .setSmallIcon(R.drawable.ic_launcher_monochrome)
            .setContentTitle("Milo test alert")
            .setContentText("Notifications are ready for $ruleName.")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "A real alert will appear when Camera Tracker reports the stove on while Milo recognizes $roomName."
            ))
            .setContentIntent(openAppIntent())
            .setAutoCancel(true)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .build()
        return postNotification(TEST_NOTIFICATION_ID, notification)
    }

    private fun postNotification(notificationId: Int, notification: Notification): Boolean {
        if (!notificationsAllowed()) return false
        try {
            NotificationManagerCompat.from(this).notify(notificationId, notification)
            return true
        } catch (_: SecurityException) {
            publishSnapshot()
            return false
        }
    }

    private fun publishSnapshot() {
        _snapshot.value = RuleMonitorSnapshot(
            running = true,
            trackerConnection = if (stoveStatus.connected) TrackerConnection.CONNECTED else TrackerConnection.OFFLINE,
            stoveState = stoveStatus.state,
            activeZoneId = activeZoneId,
            notificationsAllowed = notificationsAllowed(),
            lastError = if (stoveStatus.connected) null else stoveStatus.reason,
        )
    }

    private fun notificationsAllowed(): Boolean =
        Build.VERSION.SDK_INT < 33 || ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun updateMonitorNotification() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val message = when {
            !stoveStatus.connected -> "Camera Tracker unavailable"
            stoveStatus.state == "on" -> "Monitoring · stove appears on"
            else -> "Monitoring your enabled rules"
        }
        try {
            NotificationManagerCompat.from(this).notify(MONITOR_NOTIFICATION_ID, monitorNotification(message))
        } catch (_: SecurityException) {
            publishSnapshot()
        }
    }

    private fun monitorNotification(message: String) = NotificationCompat.Builder(this, MONITOR_CHANNEL)
        .setSmallIcon(R.drawable.ic_launcher_monochrome)
        .setContentTitle("Milo is active")
        .setContentText(message)
        .setContentIntent(openAppIntent())
        .setOngoing(true)
        .setOnlyAlertOnce(true)
        .setPriority(NotificationCompat.PRIORITY_LOW)
        .build()

    private fun openAppIntent(): PendingIntent = PendingIntent.getActivity(
        this, 0, Intent(this, MainActivity::class.java), PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
    )

    private fun createChannels() {
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(NotificationChannel(MONITOR_CHANNEL, "Rule monitoring", NotificationManager.IMPORTANCE_LOW).apply {
            description = "Keeps Milo's location rules running"
        })
        manager.createNotificationChannel(NotificationChannel(ALERT_CHANNEL, "Home alerts", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "Alerts from enabled Milo rules"
        })
    }

    companion object {
        private const val MONITOR_CHANNEL = "rule-monitor"
        private const val ALERT_CHANNEL = "home-alerts"
        private const val MONITOR_NOTIFICATION_ID = 4101
        private const val TEST_NOTIFICATION_ID = 4102
        private const val ACTION_TEST_ALERT = "com.example.magneticzones.action.TEST_ALERT"
        private const val EXTRA_RULE_ID = "rule_id"
        private const val TRACKER_POLL_MILLIS = 5_000L
        private const val ALERT_COOLDOWN_MILLIS = 30 * 60 * 1_000L
        private const val PREFERENCES = "rule_monitor"
        private val _snapshot = MutableStateFlow(RuleMonitorSnapshot())
        val snapshot = _snapshot.asStateFlow()

        fun setEnabled(context: Context, enabled: Boolean) {
            val intent = Intent(context, RuleMonitorService::class.java)
            if (enabled) ContextCompat.startForegroundService(context, intent) else context.stopService(intent)
        }

        fun sendTestAlert(context: Context, ruleId: String) {
            val intent = Intent(context, RuleMonitorService::class.java)
                .setAction(ACTION_TEST_ALERT)
                .putExtra(EXTRA_RULE_ID, ruleId)
            ContextCompat.startForegroundService(context, intent)
        }
    }
}
