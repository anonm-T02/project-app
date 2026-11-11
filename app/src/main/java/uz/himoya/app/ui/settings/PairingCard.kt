package uz.himoya.app.ui.settings

import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import uz.himoya.app.R
import uz.himoya.app.policy.PolicyRepo
import uz.himoya.app.sync.Api
import uz.himoya.app.sync.SyncStore
import uz.himoya.app.ui.theme.AppColors
import java.io.IOException

/** Bola telefonini ota-ona telefoniga bog'lash: ota-ona bergan 6 xonali kod kiritiladi. */
@Composable
fun PairingCard(onPaired: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val sync by SyncStore.state.collectAsStateWithLifecycle()
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    Card {
        Text(
            text = stringResource(R.string.pair_title),
            style = MaterialTheme.typography.bodyLarge.copy(fontWeight = FontWeight.SemiBold),
        )
        Spacer(Modifier.height(4.dp))

        if (sync.deviceToken != null) {
            Text(
                text = stringResource(R.string.pair_connected, sync.childName.orEmpty()),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.Safe,
            )
            TextButton(
                enabled = !busy,
                onClick = {
                    val token = sync.deviceToken
                    busy = true
                    scope.launch {
                        // Server javob bermasa ham shu telefonda bog'lanish uziladi.
                        withContext(Dispatchers.IO) {
                            try {
                                Api.call("POST", "/api/device/unpair", token, JSONObject())
                            } catch (e: IOException) {
                                // e'tiborsiz
                            }
                        }
                        SyncStore.update { it.copy(deviceToken = null, childName = null, policyVersion = -1) }
                        busy = false
                    }
                },
            ) {
                Text(stringResource(R.string.pair_disconnect), color = AppColors.Danger)
            }
        } else {
            Text(
                text = stringResource(R.string.pair_hint),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted,
            )
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(
                value = code,
                onValueChange = { value ->
                    code = value.filter { it.isDigit() }.take(6)
                    error = null
                },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = error != null,
                placeholder = { Text(stringResource(R.string.pair_code_placeholder)) },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
            if (error != null) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = error.orEmpty(),
                    style = MaterialTheme.typography.bodyMedium,
                    color = AppColors.Danger,
                )
            }
            Spacer(Modifier.height(10.dp))
            Button(
                enabled = !busy && code.length == 6,
                onClick = {
                    busy = true
                    error = null
                    scope.launch {
                        try {
                            val response = withContext(Dispatchers.IO) {
                                Api.call("POST", "/api/pair", null, JSONObject().put("code", code))
                            }
                            SyncStore.update {
                                it.copy(
                                    deviceToken = response.getString("deviceToken"),
                                    childName = response.optString("childName", ""),
                                    policyVersion = -1,
                                )
                            }
                            // Hozirgi qoidalar birinchi sinxronlashda serverga yuboriladi.
                            PolicyRepo.markDirty()
                            code = ""
                            onPaired()
                        } catch (e: IOException) {
                            error = e.message ?: context.getString(R.string.error_server)
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
                Text(stringResource(R.string.pair_action))
            }
        }

        Spacer(Modifier.height(8.dp))
        ServerUrlField()
    }
}

/** Server manzili. Emulyatorda standart qiymat yetadi; haqiqiy telefonda kompyuter IP manzili yoziladi. */
@Composable
fun ServerUrlField() {
    val sync by SyncStore.state.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf(false) }
    var url by remember(sync.serverUrl) { mutableStateOf(sync.serverUrl) }

    if (!editing) {
        TextButton(onClick = { editing = true }) {
            Text(
                text = stringResource(R.string.server_url_label, sync.serverUrl),
                style = MaterialTheme.typography.bodyMedium,
                color = AppColors.TextMuted,
            )
        }
    } else {
        OutlinedTextField(
            value = url,
            onValueChange = { url = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
        )
        TextButton(
            onClick = {
                val cleaned = url.trim().trimEnd('/')
                if (cleaned.startsWith("http://") || cleaned.startsWith("https://")) {
                    SyncStore.update { it.copy(serverUrl = cleaned) }
                }
                editing = false
            },
        ) {
            Text(stringResource(R.string.action_save), color = AppColors.Accent)
        }
    }
}
