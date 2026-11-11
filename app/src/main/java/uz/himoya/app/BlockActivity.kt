package uz.himoya.app

import android.content.Context
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import uz.himoya.app.ui.block.BlockScreen
import uz.himoya.app.ui.theme.AppTheme

/**
 * Blok ekrani. PIN so'ramaydi: uni bola ko'radi va bu yerdan hech narsani o'zgartirib bo'lmaydi.
 */
class BlockActivity : ComponentActivity() {

    private var domain by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        domain = intent?.getStringExtra(EXTRA_DOMAIN).orEmpty()
        setContent {
            AppTheme {
                BlockScreen(domain = domain, onClose = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        domain = intent.getStringExtra(EXTRA_DOMAIN).orEmpty()
    }

    companion object {
        private const val EXTRA_DOMAIN = "domain"

        fun intent(context: Context, domain: String): Intent =
            Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_DOMAIN, domain)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
