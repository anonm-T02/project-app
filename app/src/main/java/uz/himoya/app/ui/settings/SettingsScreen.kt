package uz.himoya.app.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import uz.himoya.app.R
import uz.himoya.app.policy.DomainText
import uz.himoya.app.policy.Policy
import uz.himoya.app.policy.TimeText
import uz.himoya.app.ui.theme.AppColors
import uz.himoya.app.vpn.TrustedClock
import kotlinx.coroutines.delay

private const val TIME_STEP_MINUTES = 30

/** Ota-ona sozlamalari: uyqu vaqti, qimor filtri va oila ro'yxatlari. */
@Composable
fun SettingsScreen(
    policy: Policy,
    onChange: ((Policy) -> Policy) -> Unit,
    onBack: () -> Unit,
    title: String? = null,
    /** Bola telefonida ko'rsatiladi: qoidalar qaysi vaqt bo'yicha ishlayotgani. */
    showClock: Boolean = true,
    /** Ro'yxat oxirida qo'shimcha karta (masalan, ota-ona telefoniga bog'lash). */
    extra: (@Composable () -> Unit)? = null,
) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(onClick = onBack, contentPadding = PaddingValues(horizontal = 4.dp)) {
                    Text(stringResource(R.string.action_back), color = AppColors.Accent)
                }
                Spacer(Modifier.width(8.dp))
                Text(
                    text = title ?: stringResource(R.string.settings_title),
                    style = MaterialTheme.typography.titleLarge,
                )
            }

            // Uyqu vaqti
            Card {
                SwitchRow(
                    title = stringResource(R.string.settings_bedtime),
                    description = stringResource(R.string.settings_bedtime_desc),
                    checked = policy.bedtimeEnabled,
                    onCheckedChange = { on -> onChange { it.copy(bedtimeEnabled = on) } },
                )
                if (showClock) {
                    Spacer(Modifier.height(10.dp))
                    CurrentTime()
                }
                if (policy.bedtimeEnabled) {
                    Spacer(Modifier.height(12.dp))
                    TimeStepper(
                        label = stringResource(R.string.settings_bedtime_start),
                        minuteOfDay = policy.bedtimeStart,
                        onShift = { delta ->
                            onChange { it.copy(bedtimeStart = TimeText.shift(it.bedtimeStart, delta)) }
                        },
                    )
                    Spacer(Modifier.height(8.dp))
                    TimeStepper(
                        label = stringResource(R.string.settings_bedtime_end),
                        minuteOfDay = policy.bedtimeEnd,
                        onShift = { delta ->
                            onChange { it.copy(bedtimeEnd = TimeText.shift(it.bedtimeEnd, delta)) }
                        },
                    )
                }
            }

            // Qimor
            Card {
                SwitchRow(
                    title = stringResource(R.string.settings_gambling),
                    description = stringResource(R.string.settings_gambling_desc),
                    checked = policy.blockGambling,
                    onCheckedChange = { on -> onChange { it.copy(blockGambling = on) } },
                )
            }

            // Yopilgan saytlar
            Card {
                DomainList(
                    title = stringResource(R.string.settings_blacklist),
                    description = stringResource(R.string.settings_blacklist_desc),
                    domains = policy.blacklist,
                    onAdd = { domain ->
                        onChange { it.copy(blacklist = it.blacklist + domain, whitelist = it.whitelist - domain) }
                    },
                    onRemove = { domain -> onChange { it.copy(blacklist = it.blacklist - domain) } },
                )
            }

            // Ruxsat berilgan saytlar
            Card {
                DomainList(
                    title = stringResource(R.string.settings_whitelist),
                    description = stringResource(R.string.settings_whitelist_desc),
                    domains = policy.whitelist,
                    onAdd = { domain ->
                        onChange { it.copy(whitelist = it.whitelist + domain, blacklist = it.blacklist - domain) }
                    },
                    onRemove = { domain -> onChange { it.copy(whitelist = it.whitelist - domain) } },
                )
            }
            if (extra != null) {
                extra()
            }
            Spacer(Modifier.height(8.dp))
        }
    }
}

/** Qoidalar qaysi vaqt bo'yicha ishlayotganini ko'rsatadi (har 5 soniyada yangilanadi). */
@Composable
private fun CurrentTime() {
    var minute by remember { mutableIntStateOf(TrustedClock.minuteOfDay()) }
    var synced by remember { mutableStateOf(TrustedClock.isSynced) }
    LaunchedEffect(Unit) {
        while (true) {
            minute = TrustedClock.minuteOfDay()
            synced = TrustedClock.isSynced
            delay(5000)
        }
    }
    Text(
        text = stringResource(
            if (synced) R.string.settings_time_network else R.string.settings_time_device,
            TimeText.format(minute),
        ),
        style = MaterialTheme.typography.bodyMedium,
        color = if (synced) AppColors.Safe else AppColors.Warn,
    )
}

@Composable
fun Card(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(AppColors.Surface)
            .border(1.dp, AppColors.Border, RoundedCornerShape(18.dp))
            .padding(18.dp),
    ) {
        content()
    }
}

@Composable
private fun SwitchRow(
    title: String,
    description: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
            )
            Spacer(Modifier.height(4.dp))
            Text(
                text = description,
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted,
            )
        }
        Spacer(Modifier.width(12.dp))
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

/** Vaqtni 30 daqiqalik qadam bilan o'zgartiradigan yirik tugmalar. */
@Composable
private fun TimeStepper(label: String, minuteOfDay: Int, onShift: (Int) -> Unit) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        StepButton("−") { onShift(-TIME_STEP_MINUTES) }
        Text(
            text = TimeText.format(minuteOfDay),
            fontSize = 24.sp,
            fontFamily = FontFamily.Monospace,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 14.dp),
        )
        StepButton("+") { onShift(TIME_STEP_MINUTES) }
    }
}

@Composable
private fun StepButton(label: String, onClick: () -> Unit) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.size(52.dp),
        shape = CircleShape,
        contentPadding = PaddingValues(0.dp),
    ) {
        Text(label, fontSize = 24.sp, color = AppColors.Accent)
    }
}

@Composable
private fun DomainList(
    title: String,
    description: String,
    domains: Set<String>,
    onAdd: (String) -> Unit,
    onRemove: (String) -> Unit,
) {
    var input by remember { mutableStateOf("") }
    var invalid by remember { mutableStateOf(false) }

    Text(
        text = title,
        style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
    )
    Spacer(Modifier.height(4.dp))
    Text(
        text = description,
        style = MaterialTheme.typography.bodyMedium,
        color = AppColors.TextMuted,
    )
    Spacer(Modifier.height(12.dp))
    OutlinedTextField(
        value = input,
        onValueChange = {
            input = it
            invalid = false
        },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true,
        isError = invalid,
        placeholder = { Text(stringResource(R.string.settings_domain_placeholder)) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Done),
    )
    if (invalid) {
        Spacer(Modifier.height(4.dp))
        Text(
            text = stringResource(R.string.settings_domain_invalid),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.Danger,
        )
    }
    Spacer(Modifier.height(10.dp))
    Button(
        onClick = {
            val domain = DomainText.normalize(input)
            if (domain == null) {
                invalid = true
            } else {
                onAdd(domain)
                input = ""
            }
        },
        enabled = input.isNotBlank(),
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp),
        shape = RoundedCornerShape(14.dp),
    ) {
        Text(stringResource(R.string.action_add))
    }

    if (domains.isEmpty()) {
        Spacer(Modifier.height(12.dp))
        Text(
            text = stringResource(R.string.settings_list_empty),
            style = MaterialTheme.typography.bodyMedium,
            color = AppColors.TextMuted,
        )
    }
    for (domain in domains.sorted()) {
        Row(
            modifier = Modifier.padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = domain,
                style = MaterialTheme.typography.bodyLarge.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier.weight(1f),
            )
            TextButton(onClick = { onRemove(domain) }) {
                Text(stringResource(R.string.action_remove), color = AppColors.Danger)
            }
        }
    }
}
