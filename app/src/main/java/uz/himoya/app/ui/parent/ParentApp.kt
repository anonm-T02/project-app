package uz.himoya.app.ui.parent

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import uz.himoya.app.R
import uz.himoya.app.policy.Policy
import uz.himoya.app.policy.TimeText
import uz.himoya.app.sync.BlockEvent
import uz.himoya.app.sync.ChildDevice
import uz.himoya.app.sync.ParentApi
import uz.himoya.app.sync.SyncStore
import uz.himoya.app.ui.settings.Card
import uz.himoya.app.ui.settings.ServerUrlField
import uz.himoya.app.ui.settings.SettingsScreen
import uz.himoya.app.ui.theme.AppColors
import uz.himoya.app.vpn.TimeMath
import java.io.IOException

/** Shu vaqtdan ko'p xabar kelmasa qurilma "ko'rinmayapti" deb hisoblanadi. */
private const val ONLINE_WINDOW_MS = 50_000L
private const val REFRESH_MS = 4_000L

private enum class ParentPage { HOME, ADD_CHILD, DETAIL, RULES }

/** Ota-ona telefoni: bolalar qurilmalari holati, qoidalar va yangi bola qo'shish. */
@Composable
fun ParentApp(onSwitchRole: () -> Unit) {
    val sync by SyncStore.state.collectAsStateWithLifecycle()
    var devices by remember { mutableStateOf<List<ChildDevice>>(emptyList()) }
    var serverTime by remember { mutableLongStateOf(0L) }
    var loaded by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    var page by remember { mutableStateOf(ParentPage.HOME) }
    var selectedId by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current

    // Ekran ochiq paytda serverdan holatni yangilab turadi.
    LaunchedEffect(sync.serverUrl) {
        while (true) {
            try {
                val result = withContext(Dispatchers.IO) { ParentApi.devices() }
                devices = result.devices
                serverTime = result.serverTime
                loaded = true
                error = null
            } catch (e: IOException) {
                error = context.getString(R.string.error_server)
            } catch (e: RuntimeException) {
                error = context.getString(R.string.error_server)
            }
            delay(REFRESH_MS)
        }
    }

    val selected = devices.firstOrNull { it.id == selectedId }

    when {
        page == ParentPage.ADD_CHILD -> {
            BackHandler { page = ParentPage.HOME }
            AddChildScreen(onBack = { page = ParentPage.HOME })
        }

        page == ParentPage.RULES && selected != null -> {
            BackHandler { page = ParentPage.DETAIL }
            RulesScreen(device = selected, onBack = { page = ParentPage.DETAIL })
        }

        page == ParentPage.DETAIL && selected != null -> {
            BackHandler { page = ParentPage.HOME }
            DeviceDetailScreen(
                device = selected,
                serverTime = serverTime,
                onBack = { page = ParentPage.HOME },
                onOpenRules = { page = ParentPage.RULES },
                onRemoved = {
                    devices = devices.filter { it.id != selected.id }
                    page = ParentPage.HOME
                },
            )
        }

        else -> ParentHomeScreen(
            devices = devices,
            serverTime = serverTime,
            loaded = loaded,
            error = error,
            onOpenDevice = { id ->
                selectedId = id
                page = ParentPage.DETAIL
            },
            onAddChild = { page = ParentPage.ADD_CHILD },
            onSwitchRole = onSwitchRole,
        )
    }
}

// ---------------------------------------------------------------- holat matni

private class DeviceStatus(val text: String, val color: Color)

@Composable
private fun deviceStatus(device: ChildDevice, serverTime: Long): DeviceStatus {
    val silentMs = serverTime - device.lastSeenAt
    return when {
        !device.protectionOn ->
            DeviceStatus(stringResource(R.string.parent_status_off), AppColors.Danger)

        silentMs > ONLINE_WINDOW_MS -> {
            val minutes = (silentMs / 60_000L).coerceAtLeast(1)
            DeviceStatus(stringResource(R.string.parent_status_silent, minutes), AppColors.Warn)
        }

        device.bedtimeActive ->
            DeviceStatus(stringResource(R.string.parent_status_bedtime), AppColors.Accent)

        else -> DeviceStatus(stringResource(R.string.status_on_title), AppColors.Safe)
    }
}

@Composable
private fun Page(content: @Composable () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            content()
        }
    }
}

@Composable
private fun Header(title: String, onBack: () -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) {
            Text(stringResource(R.string.action_back), color = AppColors.Accent)
        }
        Spacer(Modifier.width(8.dp))
        Text(text = title, style = MaterialTheme.typography.titleLarge)
    }
}

// ---------------------------------------------------------------- bosh sahifa

@Composable
private fun ParentHomeScreen(
    devices: List<ChildDevice>,
    serverTime: Long,
    loaded: Boolean,
    error: String?,
    onOpenDevice: (String) -> Unit,
    onAddChild: () -> Unit,
    onSwitchRole: () -> Unit,
) {
    Page {
        Text(text = stringResource(R.string.parent_title), style = MaterialTheme.typography.titleLarge)

        if (error != null) {
            Card {
                Text(
                    text = error,
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                    color = AppColors.Danger,
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = stringResource(R.string.error_server_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextMuted,
                )
            }
        }

        // Umumiy holat: bir qarashda hammasi joyidami.
        if (loaded) {
            val unprotected = devices.count { device ->
                !device.protectionOn || serverTime - device.lastSeenAt > ONLINE_WINDOW_MS
            }
            val summary = when {
                devices.isEmpty() -> stringResource(R.string.parent_summary_empty)
                unprotected == 0 -> stringResource(R.string.parent_summary_ok)
                else -> stringResource(R.string.parent_summary_risk, unprotected)
            }
            Text(
                text = summary,
                style = MaterialTheme.typography.bodyLarge,
                color = when {
                    devices.isEmpty() -> AppColors.TextMuted
                    unprotected == 0 -> AppColors.Safe
                    else -> AppColors.Danger
                },
            )
        }

        for (device in devices) {
            val status = deviceStatus(device, serverTime)
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(18.dp))
                    .clickable { onOpenDevice(device.id) },
            ) {
                Card {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(14.dp)
                                .clip(CircleShape)
                                .background(status.color),
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            text = device.name,
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.weight(1f),
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(text = status.text, style = MaterialTheme.typography.bodyLarge, color = status.color)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        text = stringResource(R.string.parent_blocked_count, device.blocked),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.TextMuted,
                    )
                }
            }
        }

        Button(
            onClick = onAddChild,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.parent_add_child))
        }

        ServerUrlField()
        TextButton(onClick = onSwitchRole) {
            Text(
                text = stringResource(R.string.parent_switch_role),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted,
            )
        }
    }
}

// ---------------------------------------------------------------- bola qo'shish

@Composable
private fun AddChildScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var code by remember { mutableStateOf<String?>(null) }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Page {
        Header(title = stringResource(R.string.parent_add_child), onBack = onBack)

        val shownCode = code
        if (shownCode == null) {
            Card {
                Text(
                    text = stringResource(R.string.parent_child_name),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(10.dp))
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    placeholder = { Text(stringResource(R.string.parent_child_name_placeholder)) },
                )
                if (error != null) {
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = error.orEmpty(),
                        style = MaterialTheme.typography.bodyMedium,
                        color = AppColors.Danger,
                    )
                }
                Spacer(Modifier.height(12.dp))
                Button(
                    enabled = !busy && name.isNotBlank(),
                    onClick = {
                        busy = true
                        error = null
                        scope.launch {
                            try {
                                code = withContext(Dispatchers.IO) { ParentApi.pairCode(name.trim()) }
                            } catch (e: IOException) {
                                error = context.getString(R.string.error_server)
                            } catch (e: RuntimeException) {
                                error = context.getString(R.string.error_server)
                            }
                            busy = false
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp),
                    shape = RoundedCornerShape(14.dp),
                ) {
                    Text(stringResource(R.string.parent_get_code))
                }
            }
        } else {
            Card {
                Text(
                    text = stringResource(R.string.parent_code_title, name.trim()),
                    style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = shownCode.chunked(3).joinToString(" "),
                    fontSize = 48.sp,
                    fontFamily = FontFamily.Monospace,
                    fontWeight = FontWeight.Bold,
                    color = AppColors.Accent,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth(),
                )
                Spacer(Modifier.height(16.dp))
                Text(
                    text = stringResource(R.string.parent_code_hint),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextMuted,
                )
            }
            Button(
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(64.dp),
                shape = RoundedCornerShape(14.dp),
            ) {
                Text(stringResource(R.string.action_done))
            }
        }
    }
}

// ---------------------------------------------------------------- qurilma sahifasi

@Composable
private fun DeviceDetailScreen(
    device: ChildDevice,
    serverTime: Long,
    onBack: () -> Unit,
    onOpenRules: () -> Unit,
    onRemoved: () -> Unit,
) {
    val scope = rememberCoroutineScope()
    var confirmRemove by remember { mutableStateOf(false) }
    val status = deviceStatus(device, serverTime)

    Page {
        Header(title = device.name, onBack = onBack)

        Card {
            Text(
                text = status.text,
                style = MaterialTheme.typography.titleLarge,
                color = status.color,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = stringResource(R.string.request_counters, device.checked, device.blocked),
                style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                color = AppColors.TextMuted,
            )
            if (device.protectionOn && device.alwaysOn == false) {
                Spacer(Modifier.height(8.dp))
                Text(
                    text = stringResource(R.string.parent_always_on_missing),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Warn,
                )
            }
        }

        Button(
            onClick = onOpenRules,
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(stringResource(R.string.parent_rules))
        }

        Card {
            Text(
                text = stringResource(R.string.parent_recent_blocks),
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            if (device.events.isEmpty()) {
                Spacer(Modifier.height(6.dp))
                Text(
                    text = stringResource(R.string.parent_no_blocks),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.TextMuted,
                )
            }
            for (event in device.events) {
                Spacer(Modifier.height(10.dp))
                BlockRow(event)
            }
        }

        OutlinedButton(
            onClick = {
                if (!confirmRemove) {
                    confirmRemove = true
                } else {
                    scope.launch {
                        try {
                            withContext(Dispatchers.IO) { ParentApi.remove(device.id) }
                            onRemoved()
                        } catch (e: IOException) {
                            confirmRemove = false
                        }
                    }
                }
            },
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp),
            shape = RoundedCornerShape(14.dp),
        ) {
            Text(
                text = stringResource(
                    if (confirmRemove) R.string.parent_remove_confirm else R.string.parent_remove
                ),
                color = AppColors.Danger,
            )
        }
    }
}

@Composable
private fun BlockRow(event: BlockEvent) {
    val reason = when (event.reason) {
        "GAMBLING" -> R.string.block_reason_gambling_short
        "FAMILY_LIST" -> R.string.block_reason_family_short
        else -> R.string.block_reason_adult_short
    }
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = event.domain,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
            )
            Text(
                text = stringResource(reason),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted,
            )
        }
        Text(
            text = TimeText.format(TimeMath.minuteOfDay(event.atUtcMs)),
            style = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
            color = AppColors.TextMuted,
        )
    }
}

// ---------------------------------------------------------------- qoidalar

/** Bola telefonidagi sozlamalar ekranining o'zi, lekin o'zgarishlar serverga yuboriladi. */
@Composable
private fun RulesScreen(device: ChildDevice, onBack: () -> Unit) {
    val scope = rememberCoroutineScope()
    // Tahrir paytida serverdan kelayotgan yangilanishlar yozilayotgan narsani buzmasligi uchun
    // alohida nusxa bilan ishlaymiz.
    var policy by remember(device.id) { mutableStateOf(device.policy) }

    SettingsScreen(
        policy = policy,
        onChange = { transform ->
            val next = transform(policy)
            policy = next
            scope.launch {
                try {
                    withContext(Dispatchers.IO) { ParentApi.setPolicy(device.id, next) }
                } catch (e: IOException) {
                    // Keyingi o'zgarishda yana yuboriladi.
                }
            }
        },
        onBack = onBack,
        title = stringResource(R.string.parent_rules_title, device.name),
        showClock = false,
    )
}
