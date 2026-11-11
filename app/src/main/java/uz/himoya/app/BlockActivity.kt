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
import uz.himoya.app.vpn.BlockReason

/**
 * Blok ekrani. PIN so'ramaydi: uni bola ko'radi va bu yerdan hech narsani o'zgartirib bo'lmaydi.
 */
class BlockActivity : ComponentActivity() {

    private var domain by mutableStateOf("")
    private var reason by mutableStateOf(BlockReason.ADULT_OR_HARMFUL)
    private var detail by mutableStateOf("")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        read(intent)
        setContent {
            AppTheme {
                BlockScreen(reason = reason, domain = domain, detail = detail, onClose = { finish() })
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        read(intent)
    }

    private fun read(intent: Intent?) {
        domain = intent?.getStringExtra(EXTRA_DOMAIN).orEmpty()
        detail = intent?.getStringExtra(EXTRA_DETAIL).orEmpty()
        val name = intent?.getStringExtra(EXTRA_REASON)
        reason = BlockReason.entries.firstOrNull { it.name == name } ?: BlockReason.ADULT_OR_HARMFUL
    }

    companion object {
        private const val EXTRA_DOMAIN = "domain"
        private const val EXTRA_REASON = "reason"
        private const val EXTRA_DETAIL = "detail"

        /** [detail] — uyqu vaqti uchun internet ochiladigan soat ("07:00"). */
        fun intent(context: Context, domain: String, reason: BlockReason, detail: String = ""): Intent =
            Intent(context, BlockActivity::class.java)
                .putExtra(EXTRA_DOMAIN, domain)
                .putExtra(EXTRA_REASON, reason.name)
                .putExtra(EXTRA_DETAIL, detail)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP)
    }
}
