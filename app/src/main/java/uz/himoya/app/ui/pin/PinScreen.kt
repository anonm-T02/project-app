package uz.himoya.app.ui.pin

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.himoya.app.R
import uz.himoya.app.guard.PinHasher
import uz.himoya.app.guard.PinResult
import uz.himoya.app.guard.PinStore
import uz.himoya.app.ui.theme.AppColors

enum class PinMode { CREATE, VERIFY }

/**
 * PIN o'rnatish (ikki marta kiritiladi) yoki tekshirish ekrani.
 * Tizim klaviaturasi o'rniga o'zimizning yirik raqamli tugmalar ishlatiladi.
 */
@Composable
fun PinScreen(
    mode: PinMode,
    pinStore: PinStore,
    onSuccess: () -> Unit,
    onCancel: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var entered by remember { mutableStateOf("") }
    var firstPin by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var lockedUntil by remember { mutableLongStateOf(pinStore.lockedUntil()) }
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }

    // Qulf paytida qolgan soniyalarni yangilab turadi.
    LaunchedEffect(lockedUntil) {
        while (System.currentTimeMillis() < lockedUntil) {
            now = System.currentTimeMillis()
            delay(500)
        }
        now = System.currentTimeMillis()
    }
    val secondsLeft = ((lockedUntil - now + 999) / 1000).coerceAtLeast(0)
    val locked = mode == PinMode.VERIFY && secondsLeft > 0
    val canType = !busy && !locked
    val canSubmit = canType && PinHasher.isValidPin(entered)

    fun submit() {
        val pin = entered
        when (mode) {
            PinMode.CREATE -> {
                val first = firstPin
                if (first == null) {
                    firstPin = pin
                    entered = ""
                    error = null
                } else if (first == pin) {
                    busy = true
                    scope.launch {
                        withContext(Dispatchers.Default) { pinStore.setPin(pin) }
                        busy = false
                        onSuccess()
                    }
                } else {
                    firstPin = null
                    entered = ""
                    error = context.getString(R.string.pin_mismatch)
                }
            }

            PinMode.VERIFY -> {
                busy = true
                scope.launch {
                    val result = withContext(Dispatchers.Default) { pinStore.verify(pin) }
                    busy = false
                    entered = ""
                    when (result) {
                        PinResult.Ok -> onSuccess()
                        is PinResult.Wrong ->
                            error = context.getString(R.string.pin_wrong, result.attemptsLeft)
                        is PinResult.Locked -> {
                            error = null
                            lockedUntil = result.untilMillis
                        }
                    }
                }
            }
        }
    }

    val title = when {
        mode == PinMode.VERIFY -> R.string.pin_verify_title
        firstPin == null -> R.string.pin_create_title
        else -> R.string.pin_repeat_title
    }
    val subtitle = when (mode) {
        PinMode.VERIFY -> R.string.pin_verify_subtitle
        PinMode.CREATE -> R.string.pin_create_subtitle
    }

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(24.dp))
            Text(
                text = stringResource(title),
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(subtitle),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(28.dp))
            PinDots(filled = entered.length)
            Spacer(Modifier.height(16.dp))
            val message = when {
                locked -> stringResource(R.string.pin_locked, secondsLeft)
                else -> error
            }
            Text(
                text = message ?: "",
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Danger,
                textAlign = TextAlign.Center,
                modifier = Modifier.height(48.dp),
            )

            Spacer(Modifier.weight(1f))

            Keypad(
                enabled = canType,
                onDigit = { digit ->
                    if (entered.length < PinHasher.MAX_LENGTH) {
                        entered += digit
                        error = null
                    }
                },
                onBackspace = { entered = entered.dropLast(1) },
                onCancel = onCancel,
            )
            Spacer(Modifier.height(20.dp))
            Button(
                onClick = { submit() },
                enabled = canSubmit,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.action_continue))
            }
        }
    }
}

@Composable
private fun PinDots(filled: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
        repeat(PinHasher.MAX_LENGTH) { index ->
            val isFilled = index < filled
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .clip(CircleShape)
                    .background(if (isFilled) AppColors.Accent else AppColors.Surface)
                    .border(1.5.dp, if (isFilled) AppColors.Accent else AppColors.Border, CircleShape),
            )
        }
    }
}

@Composable
private fun Keypad(
    enabled: Boolean,
    onDigit: (String) -> Unit,
    onBackspace: () -> Unit,
    onCancel: (() -> Unit)?,
) {
    val rows = listOf(
        listOf("1", "2", "3"),
        listOf("4", "5", "6"),
        listOf("7", "8", "9"),
    )
    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        rows.forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                row.forEach { digit ->
                    Key(label = digit, enabled = enabled, filled = true) { onDigit(digit) }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
            if (onCancel != null) {
                Key(
                    label = stringResource(R.string.action_cancel),
                    enabled = true,
                    filled = false,
                    small = true,
                    onClick = onCancel,
                )
            } else {
                Spacer(Modifier.size(KeySize))
            }
            Key(label = "0", enabled = enabled, filled = true) { onDigit("0") }
            Key(label = "←", enabled = enabled, filled = false, onClick = onBackspace)
        }
    }
}

private val KeySize = 78.dp

@Composable
private fun Key(
    label: String,
    enabled: Boolean,
    filled: Boolean,
    small: Boolean = false,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(KeySize)
            .clip(CircleShape)
            .background(if (filled) AppColors.Surface else AppColors.Bg)
            .then(if (filled) Modifier.border(1.dp, AppColors.Border, CircleShape) else Modifier)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            fontSize = if (small) 17.sp else 30.sp,
            color = if (enabled) AppColors.Text else AppColors.TextMuted,
        )
    }
}
