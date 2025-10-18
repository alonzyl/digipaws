package nethical.digipaws.services

import android.annotation.SuppressLint
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast
import nethical.digipaws.Constants
import nethical.digipaws.blockers.AppBlocker
import nethical.digipaws.blockers.FocusModeBlocker
import nethical.digipaws.ui.activity.MainActivity
import nethical.digipaws.ui.activity.WarningActivity
import nethical.digipaws.utils.getCurrentKeyboardPackageName
import nethical.digipaws.utils.getDefaultLauncherPackageName

class AppBlockerService : BaseBlockingService() {

    companion object {
        /**
         * Refreshes information about warning screen, cheat hours and blocked app list
         */
        const val INTENT_ACTION_REFRESH_APP_BLOCKER = "nethical.digipaws.refresh.appblocker"

        /**
         * Add cooldown to an app.
         * This broadcast should always be sent together with the following keys:
         * selected_time: Int -> Duration of cooldown in minutes
         * result_id : String -> Package name of app to be put into cooldown
         */
        const val INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN =
            "nethical.digipaws.refresh.appblocker.cooldown"

        /**
         * Refreshes information related to focus mode.
         */

        const val INTENT_ACTION_REFRESH_FOCUS_MODE = "nethical.digipaws.refresh.focus_mode"
    }

    private var appBlockerWarning = MainActivity.WarningData()
    private val appBlocker = AppBlocker()

    private val focusModeBlocker = FocusModeBlocker()

    // responsible to trigger a recheck for what app user is currently using even when no event is received. Used in putting the usage recheck logic into
    // cooldown for an app and later when the cooldown duration is over, trigger a recheck
    private val handler = Handler(Looper.getMainLooper())


    private var updateRunnable: Runnable? = null

    private var lastPackage = ""
    private var lastBlockedPackage = ""
    private var lastBlockedTime = 0L

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        val packageName = event?.packageName.toString()
        
        // Special handling for recently blocked apps - don't skip the check
        val currentTime = SystemClock.uptimeMillis()
        val isRecentlyBlocked = packageName == lastBlockedPackage && currentTime - lastBlockedTime < 5000
        
        if (!isRecentlyBlocked && (lastPackage == packageName || packageName == getPackageName())) return

        lastPackage = packageName
        Log.d("AppBlockerService", "Switched to app $packageName")

        checkAppBlocking(packageName)
    }

    private fun checkAppBlocking(packageName: String) {
        val focusModeResult = focusModeBlocker.doesAppNeedToBeBlocked(packageName)
        Log.d("AppBlockerService", "Focus mode check for $packageName: isBlocked=${focusModeResult.isBlocked}, focusData=${focusModeBlocker.focusModeData}")
        if (focusModeResult.isBlocked) {
            val currentTime = SystemClock.uptimeMillis()
            lastBlockedPackage = packageName
            lastBlockedTime = currentTime
            handleFocusModeBlockerResult(focusModeResult)
            return
        }
        handleAppBlockerResult(appBlocker.doesAppNeedToBeBlocked(packageName), packageName)
    }

    private fun checkCurrentApp() {
        try {
            val currentPackage = rootInActiveWindow?.packageName?.toString()
            if (currentPackage != null && currentPackage != getPackageName()) {
                Log.d("AppBlockerService", "Checking current app after refresh: $currentPackage")
                lastPackage = "" // Reset to force recheck
                checkAppBlocking(currentPackage)
            }
        } catch (e: Exception) {
            Log.e("AppBlockerService", "Error checking current app: $e")
        }
    }


    private fun handleAppBlockerResult(result: AppBlocker.AppBlockerResult, packageName: String) {
        Log.d("AppBlockerService", "$packageName result : $result")

        if (result.cheatHoursEndTime != -1L) {
            setUpForcedRefreshChecker(packageName, result.cheatHoursEndTime)
        }
        if (result.cooldownEndTime != -1L) {
            setUpForcedRefreshChecker(packageName, result.cooldownEndTime)
        }

        if (!result.isBlocked) return

        lastBlockedPackage = packageName
        lastBlockedTime = SystemClock.uptimeMillis()

        if (appBlockerWarning.isWarningDialogHidden) {
            pressHome()
            return
        }

        pressHome()
        Thread.sleep(300)
        val dialogIntent = Intent(this, WarningActivity::class.java)
        dialogIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        dialogIntent.putExtra("mode", Constants.WARNING_SCREEN_MODE_APP_BLOCKER)
        dialogIntent.putExtra("result_id", packageName)
        startActivity(dialogIntent)

    }

    private fun handleFocusModeBlockerResult(result: FocusModeBlocker.FocusModeResult) {
        if (result.isRequestingToUpdateSPData) {
            savedPreferencesLoader.saveFocusModeData(focusModeBlocker.focusModeData)
        }

        if (!result.isBlocked) return

        // Use warning dialog with focus mode - no proceed option
        pressHome()
        Thread.sleep(300)
        val dialogIntent = Intent(this, WarningActivity::class.java)
        dialogIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        dialogIntent.putExtra("mode", Constants.WARNING_SCREEN_MODE_FOCUS_MODE)
        dialogIntent.putExtra("result_id", lastPackage)
        startActivity(dialogIntent)
    }

    override fun onInterrupt() {
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    override fun onServiceConnected() {
        super.onServiceConnected()
        setupAppBlocker()
        setupFocusMode()

        val filter = IntentFilter().apply {
            addAction(INTENT_ACTION_REFRESH_FOCUS_MODE)
            addAction(INTENT_ACTION_REFRESH_APP_BLOCKER)
            addAction(INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(refreshReceiver, filter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(refreshReceiver, filter)
        }
    }


    private val refreshReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent == null) return
            Log.d("AppBlockerService", "Received broadcast: ${intent.action}")
            when (intent.action) {
                INTENT_ACTION_REFRESH_FOCUS_MODE -> {
                    Log.d("AppBlockerService", "Refreshing focus mode from broadcast")
                    setupFocusMode()
                    // Immediately check the current app after focus mode changes
                    checkCurrentApp()
                }
                INTENT_ACTION_REFRESH_APP_BLOCKER -> setupAppBlocker()
                INTENT_ACTION_REFRESH_APP_BLOCKER_COOLDOWN -> {
                    val interval =
                        intent.getIntExtra("selected_time", appBlockerWarning.timeInterval)
                    val coolPackage = intent.getStringExtra("result_id") ?: ""
                    val cooldownUntil =
                        SystemClock.uptimeMillis() + interval
                    appBlocker.putCooldownTo(
                        coolPackage,
                        cooldownUntil
                    )
                    setUpForcedRefreshChecker(coolPackage, cooldownUntil)

                }
            }

        }
    }

    /**
     * Setup a runnable that executes after n millis to check if a package is still being used that was allowed to be used previously
     * as it was put into cooldown or found in cheat-minutes. Basically shows the warning dialog after cooldown is over.
     * @param coolPackage
     * @param endMillis
     */
    private fun setUpForcedRefreshChecker(coolPackage: String, endMillis: Long) {
        if (updateRunnable != null) {
            updateRunnable?.let { handler.removeCallbacks(it) }
            updateRunnable = null
        }
        updateRunnable = Runnable {

            Log.d("AppBlockerService", "Triggered Recheck for  $coolPackage")
            try {
                if (rootInActiveWindow.packageName == coolPackage) {
                    handleAppBlockerResult(
                        AppBlocker.AppBlockerResult(true),
                        coolPackage
                    )
                    lastPackage = ""
                    appBlocker.removeCooldownFrom(coolPackage)
                }
            } catch (e: Exception) {
                Log.e("AppBlockerService", e.toString())
                setUpForcedRefreshChecker(coolPackage, endMillis + 60_000) // recheck after a minute
            }
        }

        handler.postAtTime(updateRunnable!!, endMillis)
    }
    private fun setupAppBlocker() {
        appBlocker.blockedAppsList = savedPreferencesLoader.loadBlockedApps().toHashSet()
        appBlocker.refreshCheatHoursData(savedPreferencesLoader.loadAppBlockerCheatHoursList())

        appBlockerWarning = savedPreferencesLoader.loadAppBlockerWarningInfo()
    }

    fun setupFocusMode() {
        // Load regular auto-focus schedules
        val autoFocusSchedules = savedPreferencesLoader.loadAutoFocusHoursList()
        focusModeBlocker.refreshCheatHoursData(autoFocusSchedules)

        // Check if NFC auto-focus schedule should be active
        checkAndActivateNFCSchedule()

        val selectedFocusModeApps = savedPreferencesLoader.getFocusModeSelectedApps().toHashSet()
        val focusModeData = savedPreferencesLoader.getFocusModeData()

        Log.d("AppBlockerService", "Setting up focus mode: isTurnedOn=${focusModeData.isTurnedOn}, modeType=${focusModeData.modeType}, selectedApps=${selectedFocusModeApps.size} apps")

        // As all apps wil get blocked except the selected ones, add essential packages that need not be blocked
        // to the list of selected apps
        if (focusModeData.modeType == Constants.FOCUS_MODE_BLOCK_ALL_EX_SELECTED) {
            selectedFocusModeApps.add("com.android.systemui")
            getDefaultLauncherPackageName(packageManager)?.let { selectedFocusModeApps.add(it) }
            getCurrentKeyboardPackageName(this)?.let { selectedFocusModeApps.add(it) }
        }

        focusModeData.selectedApps = selectedFocusModeApps
        focusModeBlocker.focusModeData = focusModeData

        Log.d("AppBlockerService", "Focus mode setup complete: ${focusModeBlocker.focusModeData}")
    }
    
    private fun checkAndActivateNFCSchedule() {
        val nfcSchedules = savedPreferencesLoader.loadNFCAutoFocusSchedules()
        if (nfcSchedules.isEmpty()) return
        
        // Check if suppressed
        val suppressedUntil = savedPreferencesLoader.getNFCAutoFocusSuppressedUntil()
        if (suppressedUntil > System.currentTimeMillis()) {
            Log.d("AppBlockerService", "NFC schedules suppressed until $suppressedUntil")
            return
        }
        
        // Check if already active manually
        if (savedPreferencesLoader.getNFCActive()) {
            Log.d("AppBlockerService", "NFC focus already active manually")
            return
        }
        
        // Get current time in minutes from midnight
        val calendar = java.util.Calendar.getInstance()
        val currentMinutes = calendar.get(java.util.Calendar.HOUR_OF_DAY) * 60 + calendar.get(java.util.Calendar.MINUTE)
        
        // Check if any schedule should be active now
        for (schedule in nfcSchedules) {
            if (currentMinutes >= schedule.startTimeInMins) {
                Log.d("AppBlockerService", "NFC schedule active: ${schedule.title} at ${schedule.startTimeInMins}")
                // Auto-start NFC focus mode using NFCFocusToggle
                val nfcToggle = nethical.digipaws.utils.NFCFocusToggle(this)
                nfcToggle.startManualFocus()
                // Set the start method to AUTO_SCHEDULE
                savedPreferencesLoader.saveNFCFocusStartMethod(nethical.digipaws.utils.NFCFocusToggle.NFCFocusStartMethod.AUTO_SCHEDULE.toString())
                break
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        unregisterReceiver(refreshReceiver)
    }

}
