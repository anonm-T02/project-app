package uz.himoya.app.ui.block

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uz.himoya.app.R
import uz.himoya.app.ui.theme.AppColors
import uz.himoya.app.ui.theme.AppTheme
import uz.himoya.app.vpn.BlockReason
import uz.himoya.app.vpn.TimeMath
import uz.himoya.app.policy.TimeText
import uz.himoya.app.sync.ChildRequests
import uz.himoya.app.sync.TempAllow
import uz.himoya.app.sync.UnlockRequest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException

/** Tinch ohangdagi blok ekrani: qo'rqitmaydi, nima bo'lganini tushuntiradi. */
@Composable
fun BlockScreen(
    reason: BlockReason,
    domain: String,
    detail: String,
    canRequest: Boolean,
    onClose: () -> Unit,
) {
    val isBedtime = reason == BlockReason.BEDTIME
    val titleText = stringResource(if (isBedtime) R.string.bedtime_title else R.string.block_title)
    val reasonText = when (reason) {
        BlockReason.BEDTIME -> stringResource(R.string.bedtime_opens_at, detail)
        BlockReason.GAMBLING -> stringResource(R.string.block_reason_gambling)
        BlockReason.FAMILY_LIST -> stringResource(R.string.block_reason_family)
        else -> stringResource(R.string.block_reason)
    }
    val hintText = stringResource(if (isBedtime) R.string.bedtime_hint else R.string.block_hint)
    val breathing = rememberInfiniteTransition(label = "breathing")
    val scale by breathing.animateFloat(
        initialValue = 0.96f,
        targetValue = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 2200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "breathingScale",
    )

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.weight(1f))

            Box(
                modifier = Modifier
                    .size(168.dp)
                    .scale(scale)
                    .clip(CircleShape)
                    .background(AppColors.Accent.copy(alpha = 0.12f)),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxSize(0.72f)
                        .clip(CircleShape)
                        .background(AppColors.Accent),
                    contentAlignment = Alignment.Center,
                ) {
                    LockIcon(Modifier.size(60.dp))
                }
            }
            Spacer(Modifier.height(28.dp))
            Text(
                text = titleText,
                style = MaterialTheme.typography.headlineLarge,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = reasonText,
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextMuted,
                textAlign = TextAlign.Center,
            )
            if (!isBedtime && domain.isNotEmpty()) {
                Spacer(Modifier.height(20.dp))
                Text(
                    text = domain,
                    style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                    color = AppColors.Text,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .clip(RoundedCornerShape(12.dp))
                        .background(AppColors.Surface)
                        .border(1.dp, AppColors.Border, RoundedCornerShape(12.dp))
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                )
            }

            Spacer(Modifier.weight(1f))

            if (canRequest && !isBedtime && domain.isNotEmpty()) {
                UnlockRequestPanel(domain)
            } else {
                Text(
                    text = hintText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
            }
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = onClose,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.block_action_ok))
            }
        }
    }
}

private enum class RequestStep { IDLE, WRITING, SENDING, WAITING, APPROVED, DENIED, FAILED }

/**
 * "Ruxsat so'rash": bola xohlasa sababini yozadi, so'rov ota-ona telefoniga boradi va javob
 * shu yerda ko'rinadi. Ruxsat berilsa sayt belgilangan vaqtgacha ochiladi.
 */
@Composable
private fun UnlockRequestPanel(domain: String) {
    val scope = rememberCoroutineScope()
    var step by remember(domain) { mutableStateOf(RequestStep.IDLE) }
    var note by remember(domain) { mutableStateOf("") }
    var requestId by remember(domain) { mutableStateOf<String?>(null) }
    var until by remember(domain) { mutableLongStateOf(0L) }

    fun showResult(request: UnlockRequest) {
        requestId = request.id
        when (request.status) {
            UnlockRequest.APPROVED -> {
                until = request.until
                // Keyingi sinxronlashni kutmasdan sayt darhol ochilsin.
                TempAllow.put(domain, request.until)
                step = RequestStep.APPROVED
            }

            UnlockRequest.DENIED -> step = RequestStep.DENIED
            else -> step = RequestStep.WAITING
        }
    }

    // Javob kutilayotganda ota-ona qarorini tekshirib turadi.
    val waitingId = requestId
    LaunchedEffect(step, waitingId) {
        if (step != RequestStep.WAITING || waitingId == null) return@LaunchedEffect
        while (true) {
            delay(4000)
            try {
                val request = withContext(Dispatchers.IO) { ChildRequests.check(waitingId) }
                if (request.status != UnlockRequest.PENDING) {
                    showResult(request)
                    break
                }
            } catch (e: IOException) {
                // Keyingi urinishda qayta tekshiriladi.
            }
        }
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
        when (step) {
            RequestStep.IDLE, RequestStep.FAILED -> {
                if (step == RequestStep.FAILED) {
                    Text(
                        text = stringResource(R.string.error_server),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Danger,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                }
                OutlinedButton(
                    onClick = { step = RequestStep.WRITING },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.request_action), color = AppColors.Accent)
                }
            }

            RequestStep.WRITING, RequestStep.SENDING -> {
                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it.take(120) },
                    modifier = Modifier.fillMaxWidth(),
                    enabled = step == RequestStep.WRITING,
                    placeholder = { Text(stringResource(R.string.request_note_placeholder)) },
                    maxLines = 3,
                )
                Spacer(Modifier.height(10.dp))
                OutlinedButton(
                    enabled = step == RequestStep.WRITING,
                    onClick = {
                        step = RequestStep.SENDING
                        scope.launch {
                            try {
                                showResult(withContext(Dispatchers.IO) { ChildRequests.send(domain, note.trim()) })
                            } catch (e: IOException) {
                                step = RequestStep.FAILED
                            } catch (e: RuntimeException) {
                                step = RequestStep.FAILED
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(64.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.request_send), color = AppColors.Accent)
                }
            }

            RequestStep.WAITING -> Text(
                text = stringResource(R.string.request_waiting),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.Accent,
                textAlign = TextAlign.Center,
            )

            RequestStep.APPROVED -> Text(
                text = stringResource(
                    R.string.request_approved,
                    TimeText.format(TimeMath.minuteOfDay(until)),
                ),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.Safe,
                textAlign = TextAlign.Center,
            )

            RequestStep.DENIED -> Text(
                text = stringResource(R.string.request_denied),
                style = MaterialTheme.typography.bodyLarge,
                color = AppColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** Oddiy qulf belgisi: yoy va to'rtburchak. */
@Composable
private fun LockIcon(modifier: Modifier = Modifier) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val stroke = w * 0.11f
        drawArc(
            color = Color.White,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(w * 0.27f, h * 0.08f),
            size = Size(w * 0.46f, h * 0.60f),
            style = Stroke(width = stroke, cap = StrokeCap.Round),
        )
        drawRoundRect(
            color = Color.White,
            topLeft = Offset(w * 0.14f, h * 0.42f),
            size = Size(w * 0.72f, h * 0.50f),
            cornerRadius = androidx.compose.ui.geometry.CornerRadius(w * 0.10f, w * 0.10f),
        )
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun BlockScreenPreview() {
    AppTheme {
        BlockScreen(BlockReason.ADULT_OR_HARMFUL, "example.com", "", canRequest = true, onClose = {})
    }
}
