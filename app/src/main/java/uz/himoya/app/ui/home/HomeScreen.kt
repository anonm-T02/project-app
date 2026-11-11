package uz.himoya.app.ui.home

import androidx.compose.animation.Crossfade
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import uz.himoya.app.R
import uz.himoya.app.ui.theme.AppColors
import uz.himoya.app.ui.theme.AppTheme
import uz.himoya.app.vpn.ProtectionStatus
import uz.himoya.app.vpn.SystemVpnSettings

/** Yagona ekran: markazda himoya holati, pastda bitta asosiy tugma. */
@Composable
fun HomeScreen(
    status: ProtectionStatus,
    checkedRequests: Long,
    blockedRequests: Long,
    systemSettings: SystemVpnSettings,
    privateDnsProvider: String?,
    bedtimeActive: Boolean,
    bedtimeEnd: String,
    onTurnOn: () -> Unit,
    onTurnOff: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenVpnSettings: () -> Unit,
    onOpenNetworkSettings: () -> Unit,
) {
    // Himoya yoqiq va hozir uyqu vaqti: internet butunlay yopiq.
    val bedtimeNow = status == ProtectionStatus.ON && bedtimeActive
    val orbColor by animateColorAsState(
        targetValue = when (status) {
            ProtectionStatus.ON -> if (bedtimeNow) AppColors.Accent else AppColors.Safe
            ProtectionStatus.STARTING -> AppColors.Accent
            ProtectionStatus.OFF -> AppColors.Warn
        },
        animationSpec = tween(durationMillis = 250),
        label = "orbColor",
    )
    val orbScale by animateFloatAsState(
        targetValue = if (status == ProtectionStatus.ON) 1f else 0.92f,
        animationSpec = spring(dampingRatio = 0.6f),
        label = "orbScale",
    )
    val title = when (status) {
        ProtectionStatus.ON -> R.string.status_on_title
        ProtectionStatus.STARTING -> R.string.status_starting_title
        ProtectionStatus.OFF -> R.string.status_off_title
    }
    val subtitle = when (status) {
        ProtectionStatus.ON -> R.string.status_on_subtitle
        ProtectionStatus.STARTING -> R.string.status_starting_subtitle
        ProtectionStatus.OFF -> R.string.status_off_subtitle
    }
    val buttonShape = RoundedCornerShape(14.dp)
    val buttonModifier = Modifier
        .fillMaxWidth()
        .height(64.dp)

    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.app_name),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onOpenSettings) {
                    Text(stringResource(R.string.settings_title), color = AppColors.Accent)
                }
            }

            // O'rta qism kichik ekranlarda aylantiriladi, tugma doim pastda ko'rinib turadi.
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(vertical = 16.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                StatusOrb(
                    isOn = status == ProtectionStatus.ON,
                    color = orbColor,
                    modifier = Modifier
                        .size(180.dp)
                        .scale(orbScale),
                )
                Spacer(Modifier.height(28.dp))
                Text(
                    text = stringResource(if (bedtimeNow) R.string.bedtime_title else title),
                    style = MaterialTheme.typography.headlineLarge,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.height(8.dp))
                Text(
                    text = if (bedtimeNow) {
                        stringResource(R.string.bedtime_closed_until, bedtimeEnd)
                    } else {
                        stringResource(subtitle)
                    },
                    style = MaterialTheme.typography.bodyLarge,
                    color = AppColors.TextMuted,
                    textAlign = TextAlign.Center,
                )
                if (status == ProtectionStatus.ON) {
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = stringResource(R.string.request_counters, checkedRequests, blockedRequests),
                        style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                        color = AppColors.TextMuted,
                        textAlign = TextAlign.Center,
                    )
                }
                if (privateDnsProvider != null) {
                    Spacer(Modifier.height(20.dp))
                    InfoCard(
                        title = stringResource(R.string.private_dns_title),
                        text = stringResource(R.string.private_dns_text, privateDnsProvider),
                        warning = true,
                        onAction = onOpenNetworkSettings,
                    )
                }
                if (status == ProtectionStatus.ON) {
                    Spacer(Modifier.height(20.dp))
                    AlwaysOnCard(systemSettings, onOpenVpnSettings)
                }
            }

            when (status) {
                ProtectionStatus.ON -> OutlinedButton(
                    onClick = onTurnOff,
                    modifier = buttonModifier,
                    shape = buttonShape,
                    border = BorderStroke(1.dp, AppColors.Border),
                ) {
                    Text(stringResource(R.string.action_turn_off), color = AppColors.Text)
                }

                else -> Button(
                    onClick = onTurnOn,
                    enabled = status == ProtectionStatus.OFF,
                    modifier = buttonModifier,
                    shape = buttonShape,
                ) {
                    Text(stringResource(R.string.action_turn_on))
                }
            }
            Spacer(Modifier.height(16.dp))
            Text(
                text = stringResource(R.string.disclaimer),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted,
                textAlign = TextAlign.Center,
            )
        }
    }
}

/** "Doim yoqiq VPN" bo'yicha maslahat yoki ogohlantirish. */
@Composable
private fun AlwaysOnCard(settings: SystemVpnSettings, onOpenVpnSettings: () -> Unit) {
    val lockdown = settings.lockdown == true
    if (settings.alwaysOn == true && !lockdown) {
        Text(
            text = stringResource(R.string.always_on_active),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Safe,
            textAlign = TextAlign.Center,
        )
        return
    }
    InfoCard(
        title = stringResource(if (lockdown) R.string.lockdown_title else R.string.always_on_title),
        text = stringResource(if (lockdown) R.string.lockdown_text else R.string.always_on_text),
        warning = lockdown,
        onAction = onOpenVpnSettings,
    )
}

@Composable
private fun InfoCard(title: String, text: String, warning: Boolean, onAction: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.Surface)
            .border(1.dp, if (warning) AppColors.Warn else AppColors.Border, RoundedCornerShape(18.dp))
            .padding(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 6.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            color = AppColors.Text,
        )
        Spacer(Modifier.height(6.dp))
        Text(
            text = text,
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextMuted,
        )
        TextButton(onClick = onAction) {
            Text(
                text = stringResource(R.string.action_open_settings),
                color = if (warning) AppColors.Warn else AppColors.Accent,
            )
        }
    }
}

@Composable
private fun StatusOrb(isOn: Boolean, color: Color, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(color.copy(alpha = 0.14f)),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize(0.72f)
                .clip(CircleShape)
                .background(color),
            contentAlignment = Alignment.Center,
        ) {
            Crossfade(
                targetState = isOn,
                animationSpec = tween(durationMillis = 250),
                label = "orbIcon",
            ) { on ->
                Canvas(modifier = Modifier.size(72.dp)) {
                    val w = size.width
                    val h = size.height
                    val stroke = w * 0.14f
                    if (on) {
                        val check = Path().apply {
                            moveTo(w * 0.10f, h * 0.55f)
                            lineTo(w * 0.40f, h * 0.82f)
                            lineTo(w * 0.92f, h * 0.22f)
                        }
                        drawPath(
                            path = check,
                            color = Color.White,
                            style = Stroke(width = stroke, cap = StrokeCap.Round, join = StrokeJoin.Round),
                        )
                    } else {
                        drawLine(
                            color = Color.White,
                            start = Offset(w / 2f, h * 0.12f),
                            end = Offset(w / 2f, h * 0.60f),
                            strokeWidth = stroke,
                            cap = StrokeCap.Round,
                        )
                        drawCircle(
                            color = Color.White,
                            radius = stroke * 0.6f,
                            center = Offset(w / 2f, h * 0.86f),
                        )
                    }
                }
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun HomeScreenOnPreview() {
    AppTheme {
        HomeScreen(ProtectionStatus.ON, 128, 4, SystemVpnSettings(alwaysOn = false), null, false, "07:00", {}, {}, {}, {}, {})
    }
}

@Preview(showBackground = true, widthDp = 380, heightDp = 780)
@Composable
private fun HomeScreenOffPreview() {
    AppTheme {
        HomeScreen(ProtectionStatus.OFF, 0, 0, SystemVpnSettings(), "dns.example.com", false, "07:00", {}, {}, {}, {}, {})
    }
}
