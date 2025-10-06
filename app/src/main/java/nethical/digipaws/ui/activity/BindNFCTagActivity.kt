package nethical.digipaws.ui.activity

import android.app.PendingIntent
import android.content.Intent
import android.content.IntentFilter
import android.nfc.NdefMessage
import android.nfc.NdefRecord
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.nfc.tech.MifareClassic
import android.nfc.tech.MifareUltralight
import android.nfc.tech.Ndef
import android.os.Bundle
import android.provider.Settings
import android.view.View
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import nethical.digipaws.databinding.ActivityBindNfcTagBinding
import nethical.digipaws.utils.SavedPreferencesLoader
import java.nio.charset.Charset

class BindNFCTagActivity : AppCompatActivity() {
    
    private lateinit var binding: ActivityBindNfcTagBinding
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var intentFiltersArray: Array<IntentFilter>? = null
    private var techListsArray: Array<Array<String>>? = null
    private lateinit var prefsLoader: SavedPreferencesLoader
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityBindNfcTagBinding.inflate(layoutInflater)
        setContentView(binding.root)
        
        prefsLoader = SavedPreferencesLoader(this)
        
        setupNFC()
        setupUI()
    }
    
    private fun setupNFC() {
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        if (nfcAdapter == null) {
            showNFCUnavailable("NFC is not available on this device")
            return
        }
        
        if (!nfcAdapter!!.isEnabled) {
            showNFCUnavailable("NFC is disabled. Please enable it in settings.")
            return
        }
        
        // Create a pending intent for NFC discovery
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        
        // Setup intent filters for NFC
        val ndef = IntentFilter(NfcAdapter.ACTION_NDEF_DISCOVERED).apply {
            try {
                addDataType("*/*")
            } catch (e: IntentFilter.MalformedMimeTypeException) {
                throw RuntimeException("Failed to set up NDEF intent filter", e)
            }
        }
        intentFiltersArray = arrayOf(ndef)
        
        // Setup tech lists
        techListsArray = arrayOf(
            arrayOf<String>(MifareClassic::class.java.name),
            arrayOf<String>(MifareUltralight::class.java.name),
            arrayOf<String>(Ndef::class.java.name)
        )
    }
    
    private fun setupUI() {
        binding.btnCancelBind.setOnClickListener { 
            finish() 
        }
        
        binding.btnNfcSettings.setOnClickListener {
            startActivity(Intent(Settings.ACTION_NFC_SETTINGS))
        }
    }
    
    private fun showNFCUnavailable(message: String) {
        binding.apply {
            bindProgress.visibility = View.GONE
            bindStatus.text = message
            bindStatus.setTextColor(getColor(android.R.color.holo_red_dark))
            nfcHelpCard.visibility = View.VISIBLE
        }
    }
    
    override fun onResume() {
        super.onResume()
        // Check NFC status again in case user enabled it
        nfcAdapter?.let { adapter ->
            if (adapter.isEnabled) {
                binding.apply {
                    nfcHelpCard.visibility = View.GONE
                    bindProgress.visibility = View.VISIBLE
                    bindStatus.text = "Waiting for NFC tag..."
                    bindStatus.setTextColor(getColor(android.R.color.darker_gray))
                }
                // Enable foreground dispatch
                adapter.enableForegroundDispatch(
                    this,
                    pendingIntent,
                    intentFiltersArray,
                    techListsArray
                )
            } else {
                showNFCUnavailable("NFC is disabled. Please enable it in settings.")
            }
        }
    }
    
    override fun onPause() {
        super.onPause()
        nfcAdapter?.disableForegroundDispatch(this)
    }
    
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleNFCIntent(intent)
    }
    
    private fun handleNFCIntent(intent: Intent) {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
        
        tag?.let { nfcTag ->
            val tagId = bytesToHex(nfcTag.id)
            
            // Save the tag ID
            prefsLoader.saveNFCTagKey(tagId)
            prefsLoader.saveNFCRequireSameTag(true)
            prefsLoader.saveNFCActive(false)
            prefsLoader.saveNFCAutoRearm(true)
            
            // Show success
            binding.apply {
                bindProgress.visibility = View.GONE
                bindStatus.text = getString(nethical.digipaws.R.string.nfc_bind_success)
                bindStatus.setTextColor(getColor(android.R.color.holo_green_dark))
                btnCancelBind.text = "Done"
            }
            
            Toast.makeText(
                this,
                "NFC Tag bound: ...${tagId.takeLast(4)}",
                Toast.LENGTH_LONG
            ).show()
            
            // Auto-close after 2 seconds
            binding.root.postDelayed({
                finish()
            }, 2000)
        }
    }
    
    private fun bytesToHex(bytes: ByteArray): String {
        return bytes.joinToString("") { "%02X".format(it) }
    }
}