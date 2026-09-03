package com.personalcalendar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.personalcalendar.app.auth.AuthUser

private val ERROR_MESSAGES = mapOf(
    "invalid_credentials" to "이메일 또는 비밀번호가 올바르지 않습니다.",
    "email_taken" to "이미 가입된 이메일입니다.",
    "weak_password" to "비밀번호는 8자 이상이어야 합니다.",
    "invalid_email" to "올바른 이메일 형식이 아닙니다.",
    "network_error" to "서버에 연결할 수 없습니다. 서버 주소를 확인해주세요.",
    "discord_login_failed" to "디스코드 로그인에 실패했습니다."
)

@Composable
fun AccountDialog(
    authUser: AuthUser?,
    authBusy: Boolean,
    authError: String?,
    hasPinSet: Boolean,
    onDismiss: () -> Unit,
    onLogin: (String, String) -> Unit,
    onRegister: (String, String, String?) -> Unit,
    onLogout: () -> Unit,
    onDiscordLogin: () -> Unit,
    onSetPin: (String) -> Unit,
    onClearPin: () -> Unit
) {
    var tab by remember { mutableStateOf("login") }
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }
    var pinField by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onDismiss) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
                .verticalScroll(rememberScrollState())
        ) {
            Text("계정", style = MaterialTheme.typography.titleMedium)
            Spacer(10.dp)

            if (authUser != null) {
                Text(
                    "${authUser.displayName ?: authUser.email ?: authUser.discordUsername ?: "사용자"}님으로 로그인됨",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    "일정과 카테고리가 이 계정에 자동으로 동기화됩니다.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(10.dp)
                Button(onClick = onLogout, modifier = Modifier.fillMaxWidth()) { Text("로그아웃") }
            } else {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    TextButton(onClick = { tab = "login" }, modifier = Modifier.weight(1f)) { Text("로그인") }
                    TextButton(onClick = { tab = "register" }, modifier = Modifier.weight(1f)) { Text("회원가입") }
                }
                Spacer(6.dp)

                if (tab == "login") {
                    OutlinedTextField(email, { email = it }, label = { Text("이메일") }, modifier = Modifier.fillMaxWidth())
                    Spacer(8.dp)
                    OutlinedTextField(
                        password, { password = it }, label = { Text("비밀번호") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(10.dp)
                    Button(
                        onClick = { onLogin(email.trim(), password) },
                        enabled = !authBusy && email.isNotBlank() && password.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("로그인") }
                } else {
                    OutlinedTextField(name, { name = it }, label = { Text("이름 (선택)") }, modifier = Modifier.fillMaxWidth())
                    Spacer(8.dp)
                    OutlinedTextField(email, { email = it }, label = { Text("이메일") }, modifier = Modifier.fillMaxWidth())
                    Spacer(8.dp)
                    OutlinedTextField(
                        password, { password = it }, label = { Text("비밀번호 (8자 이상)") },
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth()
                    )
                    Spacer(10.dp)
                    Button(
                        onClick = { onRegister(email.trim(), password, name.trim().ifBlank { null }) },
                        enabled = !authBusy && email.isNotBlank() && password.length >= 8,
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("회원가입") }
                }

                authError?.let {
                    Spacer(6.dp)
                    Text(
                        ERROR_MESSAGES[it] ?: "오류가 발생했습니다.",
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }

                Spacer(14.dp)
                Text("또는", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Spacer(6.dp)
                Button(onClick = onDiscordLogin, modifier = Modifier.fillMaxWidth()) { Text("Discord로 로그인") }
            }

            Spacer(16.dp)
            HorizontalDivider()
            Spacer(10.dp)
            Text("화면 잠금", style = MaterialTheme.typography.titleSmall)
            Text(
                if (hasPinSet) "PIN이 설정되어 있습니다." else "설정된 PIN이 없습니다.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(8.dp)
            OutlinedTextField(
                pinField,
                { v -> pinField = v.filter { it.isDigit() }.take(6) },
                label = { Text("4~6자리 숫자") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            Spacer(8.dp)
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(
                    onClick = { onSetPin(pinField); pinField = "" },
                    enabled = pinField.length in 4..6
                ) { Text("PIN 설정") }
                if (hasPinSet) {
                    OutlinedButton(onClick = onClearPin) { Text("PIN 해제") }
                }
            }

            Spacer(16.dp)
            TextButton(onClick = onDismiss, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
            Spacer(8.dp)
            Text(
                "버전 ${com.personalcalendar.app.BuildConfig.VERSION_NAME}",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.fillMaxWidth(),
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}

@Composable
private fun Spacer(height: androidx.compose.ui.unit.Dp) {
    androidx.compose.foundation.layout.Spacer(Modifier.height(height))
}
