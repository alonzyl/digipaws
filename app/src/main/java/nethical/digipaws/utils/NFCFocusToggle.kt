package nethical.digipaws.utils

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.core.app.NotificationCompat
import nethical.digipaws.Constants
import nethical.digipaws.R
import nethical.digipaws.blockers.FocusModeBlocker
import nethical.digipaws.services.AppBlockerService

/**
 * Controller class responsible for toggling Focus Mode via NFC.
 * Integrates with existing DigiPaws blocker infrastructure.
 */
class NFCFocusToggle(private val context: Context) {
    
    private val prefsLoader = SavedPreferencesLoader(context)
    private val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    
    enum class NFCFocusStartMethod {
        TAG_TOGGLE,      // Started by NFC tag scan, can toggle with same tag
        MANUAL_BUTTON,   // Started by button press, requires NFC to unlock
        AUTO_SCHEDULE;   // Started by schedule, requires NFC to unlock, auto-ends
        
        companion object {
            fun fromString(value: String?): NFCFocusStartMethod {
                return when (value) {
                    "MANUAL_BUTTON" -> MANUAL_BUTTON
                    "AUTO_SCHEDULE" -> AUTO_SCHEDULE
                    else -> TAG_TOGGLE
                }
            }
        }
        
        override fun toString(): String {
            return name
        }
    }
    
    companion object {
        private const val TAG = "NFCFocusToggle"
        private const val NOTIFICATION_CHANNEL_ID = "nfc_focus_channel"
        private const val NOTIFICATION_ID = 1001
    }
    
    init {
        createNotificationChannel()
    }
    
    /**
     * Main entry point for handling NFC tag scans
     */
    fun handleTagScan(tagKey: String) {
        Log.d(TAG, "Processing tag scan: ...${tagKey.takeLast(4)}")
        
        val boundTagKey = prefsLoader.getNFCTagKey()
        
        // Check if tag is bound
        if (boundTagKey == null) {
            Log.d(TAG, "No tag bound, ignoring scan")
            return
        }
        
        // Check if scanned tag matches bound tag
        if (boundTagKey != tagKey) {
            Log.d(TAG, "Tag mismatch: bound=${boundTagKey.takeLast(4)}, scanned=${tagKey.takeLast(4)}")
            return
        }
        
        val isCurrentlyActive = prefsLoader.getNFCActive()
        
        if (isCurrentlyActive) {
            handleStopFocus(tagKey)
        } else {
            handleStartFocus(tagKey)
        }
    }
    
    /**
     * Start focus mode manually via button press
     */
    fun startManualFocus() {
        Log.d(TAG, "Starting NFC Focus manually")
        
        // Get apps to block
        val nfcBlockedApps = prefsLoader.loadNFCFocusSelectedApps()
        val appsToBlock = if (nfcBlockedApps.isEmpty()) {
            prefsLoader.getFocusModeSelectedApps().toSet()
        } else {
            nfcBlockedApps
        }
        
        if (appsToBlock.isEmpty()) {
            Log.w(TAG, "No apps selected for blocking")
            showToast("No apps selected for NFC Focus")
            return
        }
        
        // Create FocusMode data
        val focusData = FocusModeBlocker.FocusModeData(
            isTurnedOn = true,
            selectedApps = HashSet(appsToBlock),
            modeType = Constants.FOCUS_MODE_BLOCK_SELECTED,
            endTime = Long.MAX_VALUE
        )
        
        // Save focus mode data
        prefsLoader.saveFocusModeData(focusData)
        
        // Update NFC state
        prefsLoader.saveNFCActive(true)
        prefsLoader.saveNFCFocusStartMethod(NFCFocusStartMethod.MANUAL_BUTTON.toString())
        
        // Send broadcast to refresh focus mode in service
        val refreshIntent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
        context.sendBroadcast(refreshIntent)
        
        // Show notification and toast
        showNotification(context.getString(R.string.nfc_focus_enabled), true)
        showToast(context.getString(R.string.nfc_focus_enabled))
        
        Log.d(TAG, "NFC Focus started manually successfully")
    }
    
    private fun handleStartFocus(tagKey: String) {
        Log.d(TAG, "Starting NFC Focus")
        
        // Create focus mode data using existing blocked apps list or NFC-specific apps
        val nfcBlockedApps = prefsLoader.loadNFCFocusSelectedApps()
        val appsToBlock = if (nfcBlockedApps.isEmpty()) {
            // Fallback to main focus mode apps if no NFC-specific apps are set
            prefsLoader.getFocusModeSelectedApps().toSet()
        } else {
            nfcBlockedApps
        }
        
        if (appsToBlock.isEmpty()) {
            Log.w(TAG, "No apps selected for blocking")
            showToast("No apps selected for NFC Focus")
            return
        }
        
        // Create FocusMode data
        val focusData = FocusModeBlocker.FocusModeData(
            isTurnedOn = true,
            selectedApps = HashSet(appsToBlock),
            modeType = Constants.FOCUS_MODE_BLOCK_SELECTED,
            endTime = Long.MAX_VALUE // Unlimited duration for NFC mode
        )
        
        // Save focus mode data
        prefsLoader.saveFocusModeData(focusData)
        
        // Update NFC state
        prefsLoader.saveNFCActive(true)
        prefsLoader.saveNFCActiveTagKey(tagKey)
        prefsLoader.saveNFCFocusStartMethod(NFCFocusStartMethod.TAG_TOGGLE.toString())
        
        // Send broadcast to refresh focus mode in service
        val refreshIntent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
        context.sendBroadcast(refreshIntent)
        
        // Show notification and toast
        showNotification(context.getString(R.string.nfc_focus_enabled), true)
        showToast(context.getString(R.string.nfc_focus_enabled))
        
        Log.d(TAG, "NFC Focus started successfully")
    }
    
    private fun handleStopFocus(tagKey: String) {
        Log.d(TAG, "Attempting to stop NFC Focus")
        
        // Check start method to determine unlock requirements
        val startMethod = NFCFocusStartMethod.fromString(prefsLoader.getNFCFocusStartMethod())
        
        // Check if an NFC auto-schedule is currently active (even if no manual focus is on)
        val hasNFCSchedules = prefsLoader.loadNFCAutoFocusSchedules().isNotEmpty()
        val isNFCActive = prefsLoader.getNFCActive()
        
        when (startMethod) {
            NFCFocusStartMethod.TAG_TOGGLE -> {
                // For tag toggle mode, check if same tag is required
                val requireSameTag = prefsLoader.getNFCRequireSameTag()
                val activeTagKey = prefsLoader.getNFCActiveTagKey()
                
                if (requireSameTag && activeTagKey != tagKey) {
                    Log.d(TAG, "Wrong tag for stopping: active=${activeTagKey?.takeLast(4)}, scanned=${tagKey.takeLast(4)}")
                    showToast(context.getString(R.string.nfc_wrong_tag))
                    return
                }
            }
            NFCFocusStartMethod.MANUAL_BUTTON, NFCFocusStartMethod.AUTO_SCHEDULE -> {
                // For manual or auto-schedule mode, verify it's the bound tag
                val boundTagKey = prefsLoader.getNFCTagKey()
                if (boundTagKey != tagKey) {
                    Log.d(TAG, "Wrong tag for unlocking: bound=${boundTagKey?.takeLast(4)}, scanned=${tagKey.takeLast(4)}")
                    showToast("NFC tag required to unlock")
                    return
                }
            }
            null -> {
                // No active manual focus, but check if NFC schedules exist
                if (hasNFCSchedules) {
                    Log.d(TAG, "No manual focus active, but NFC schedules exist - suppressing them")
                    stopNFCFocus(suppressAutoSchedules = true)
                    return
                }
            }
        }
        
        // Check if we need to suppress NFC auto-focus schedules
        val wasAutoSchedule = startMethod == NFCFocusStartMethod.AUTO_SCHEDULE
        
        stopNFCFocus(suppressAutoSchedules = wasAutoSchedule || (!isNFCActive && hasNFCSchedules))
    }
    
    /**
     * Public method to stop NFC Focus (can be called from other parts of the app)
     * @param suppressAutoSchedules If true, suppresses NFC auto-focus schedules until end of day
     */
    fun stopNFCFocus(suppressAutoSchedules: Boolean = false) {
        Log.d(TAG, "Stopping NFC Focus (suppressAutoSchedules=$suppressAutoSchedules)")
        
        // Disable focus mode
        val currentFocusData = prefsLoader.getFocusModeData()
        val disabledFocusData = currentFocusData.copy(isTurnedOn = false)
        prefsLoader.saveFocusModeData(disabledFocusData)
        
        // Update NFC state
        prefsLoader.saveNFCActive(false)
        prefsLoader.saveNFCActiveTagKey(null)
        prefsLoader.saveNFCFocusStartMethod(null)
        
        // If ending an auto-schedule, suppress NFC schedules until end of day (23:59:59)
        if (suppressAutoSchedules) {
            val calendar = java.util.Calendar.getInstance()
            calendar.set(java.util.Calendar.HOUR_OF_DAY, 23)
            calendar.set(java.util.Calendar.MINUTE, 59)
            calendar.set(java.util.Calendar.SECOND, 59)
            calendar.set(java.util.Calendar.MILLISECOND, 999)
            
            val endOfDay = calendar.timeInMillis
            prefsLoader.saveNFCAutoFocusSuppressedUntil(endOfDay)
            Log.d(TAG, "NFC auto-focus schedules suppressed until end of day: $endOfDay")
        }
        
        // Send broadcast to refresh focus mode in service
        val refreshIntent = Intent(AppBlockerService.INTENT_ACTION_REFRESH_FOCUS_MODE)
        context.sendBroadcast(refreshIntent)
        
        // Show notification and toast
        showNotification(context.getString(R.string.nfc_focus_disabled), false)
        showToast(context.getString(R.string.nfc_focus_disabled))
        
        Log.d(TAG, "NFC Focus stopped successfully")
    }
    
    /**
     * Re-enable focus when screen turns off (if auto-rearm is enabled)
     */
    fun handleAutoRearm() {
        if (!prefsLoader.getNFCAutoRearm()) {
            return
        }
        
        val wasNFCActive = prefsLoader.getNFCActive()
        if (!wasNFCActive) {
            Log.d(TAG, "Auto-rearming NFC Focus after screen off")
            val boundTagKey = prefsLoader.getNFCTagKey()
            boundTagKey?.let { handleStartFocus(it) }
        }
    }
    
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                NOTIFICATION_CHANNEL_ID,
                "NFC Focus",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Notifications for NFC Focus status"
                setShowBadge(false)
            }
            notificationManager.createNotificationChannel(channel)
        }
    }
    
    private fun showNotification(message: String, isActive: Boolean) {
        val notification = NotificationCompat.Builder(context, NOTIFICATION_CHANNEL_ID)
            .setSmallIcon(if (isActive) R.drawable.baseline_nfc_24 else R.drawable.baseline_warning_24)
            .setContentTitle("NFC Focus")
            .setContentText(message)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .build()
        
        notificationManager.notify(NOTIFICATION_ID, notification)
    }
    
    private fun showToast(message: String) {
        Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
    }
}
