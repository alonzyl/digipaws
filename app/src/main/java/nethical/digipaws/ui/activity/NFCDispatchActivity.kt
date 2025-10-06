package nethical.digipaws.ui.activity

import android.app.Activity
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.util.Log
import nethical.digipaws.utils.NFCFocusToggle

/**
 * Lightweight activity that handles NFC tag scans when the app is not in foreground.
 * This activity has no UI and immediately processes the tag then finishes.
 */
class NFCDispatchActivity : Activity() {
    
    private lateinit var focusToggle: NFCFocusToggle
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        focusToggle = NFCFocusToggle(this)
        
        // Handle the NFC intent
        handleNFCIntent(intent)
        
        // Immediately finish - we don't want any UI
        finish()
    }
    
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        intent?.let { handleNFCIntent(it) }
        finish()
    }
    
    private fun handleNFCIntent(intent: Intent) {
        Log.d("NFCDispatch", "Handling NFC intent: ${intent.action}")
        
        when (intent.action) {
            NfcAdapter.ACTION_NDEF_DISCOVERED,
            NfcAdapter.ACTION_TAG_DISCOVERED,
            NfcAdapter.ACTION_TECH_DISCOVERED -> {
                val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
                tag?.let { nfcTag ->
                    val tagId = bytesToHex(nfcTag.id)
                    Log.d("NFCDispatch", "Tag detected: ...${tagId.takeLast(4)}")
                    focusToggle.handleTagScan(tagId)
                }
            }
        }
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}