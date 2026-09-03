package com.personalcalendar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch

private const val MAX_PIN_LEN = 6
private const val MIN_PIN_LEN = 4

@Composable
fun PinLockScreen(onCheckPin: suspend (String) -> Boolean) {
    var pin by remember { mutableStateOf("") }
    var showError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    fun attempt(candidate: String) {
        scope.launch {
            val ok = onCheckPin(candidate)
            if (!ok) {
                if (candidate.length >= MAX_PIN_LEN) {
                    showError = true
                    pin = ""
                }
                // else: not enough digits yet to conclude failure, keep waiting for more input
            }
        }
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(
            Modifier.fillMaxSize().padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text("🔒", fontSize = 40.sp)
            Spacer(Modifier.height(12.dp))
            Text("PIN을 입력하세요", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(20.dp))

            Row(horizontalArrangement = Arrangement.spacedBy(14.dp)) {
                repeat(MAX_PIN_LEN) { i ->
                    val filled = i < pin.length
                    Box(
                        Modifier
                            .size(14.dp)
                            .clip(CircleShape)
                            .background(
                                if (filled) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                            )
                    )
                }
            }

            if (showError) {
                Spacer(Modifier.height(10.dp))
                Text("PIN이 올바르지 않습니다.", color = MaterialTheme.colorScheme.error, fontSize = 13.sp)
            }

            Spacer(Modifier.height(32.dp))

            val keys = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "", "0", "⌫")
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                keys.chunked(3).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(28.dp)) {
                        row.forEach { key ->
                            KeypadButton(key) {
                                showError = false
                                when (key) {
                                    "" -> {}
                                    "⌫" -> pin = pin.dropLast(1)
                                    else -> {
                                        if (pin.length < MAX_PIN_LEN) {
                                            pin += key
                                            if (pin.length >= MIN_PIN_LEN) attempt(pin)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeypadButton(label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(64.dp)
            .clip(CircleShape)
            .then(if (label.isNotEmpty()) Modifier.clickable { onClick() } else Modifier)
            .background(if (label.isNotEmpty() && label != "⌫") MaterialTheme.colorScheme.surfaceVariant else Color.Transparent),
        contentAlignment = Alignment.Center
    ) {
        if (label == "⌫") {
            Icon(Icons.AutoMirrored.Filled.Backspace, contentDescription = "지우기")
        } else if (label.isNotEmpty()) {
            Text(label, fontSize = 22.sp)
        }
    }
}
