package nethical.digipaws.receivers

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import nethical.digipaws.utils.NFCFocusToggle

/**
 * BroadcastReceiver to handle screen off events for NFC Focus auto-rearm feature.
 * When screen turns off and auto-rearm is enabled, this will re-enable focus mode.
 */
class ScreenOffReceiver : BroadcastReceiver() {
    
    companion object {
        private const val TAG = "ScreenOffReceiver"
    }
    
    override fun onReceive(context: Context?, intent: Intent?) {
        if (context == null || intent == null) return
        
        when (intent.action) {
            Intent.ACTION_SCREEN_OFF -> {
                Log.d(TAG, "Screen turned off - checking for NFC auto-rearm")
                
                val focusToggle = NFCFocusToggle(context)
                focusToggle.handleAutoRearm()
            }
        }
    }
}