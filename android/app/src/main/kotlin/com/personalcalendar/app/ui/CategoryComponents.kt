package com.personalcalendar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import com.personalcalendar.app.data.EventCategory

private val PALETTE = listOf(
    0xFF5B8DEFL, 0xFF3EA36FL, 0xFFE0596BL, 0xFF9B6CE0L,
    0xFFE0A52EL, 0xFF2EC4C9L, 0xFFD46FB3L, 0xFF8C9EFFL
)

@Composable
fun CategoryFilterRow(
    categories: List<EventCategory>,
    activeIds: Set<String>,
    onToggle: (String) -> Unit,
    onManageClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 12.dp, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        categories.forEach { c ->
            FilterChip(
                selected = activeIds.contains(c.id),
                onClick = { onToggle(c.id) },
                label = { Text(c.name) },
                leadingIcon = {
                    Box(
                        Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(Color(c.color))
                    )
                }
            )
        }
        TextButton(onClick = onManageClick) { Text("관리") }
    }
}

@Composable
fun CategoryManagerDialog(
    categories: List<EventCategory>,
    onAdd: (String, Long) -> Unit,
    onUpdate: (String, String, Long) -> Unit,
    onDelete: (String) -> Unit,
    onClose: () -> Unit
) {
    var newName by remember { mutableStateOf("") }

    Dialog(onDismissRequest = onClose) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(MaterialTheme.colorScheme.surface)
                .padding(20.dp)
        ) {
            Text("카테고리 관리", style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))

            categories.forEach { c ->
                CategoryManageRow(
                    category = c,
                    canDelete = categories.size > 1,
                    onUpdate = { name, color -> onUpdate(c.id, name, color) },
                    onDelete = { onDelete(c.id) }
                )
                Spacer(Modifier.height(10.dp))
            }

            HorizontalDivider(Modifier.padding(vertical = 4.dp))
            Spacer(Modifier.height(8.dp))

            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newName,
                    onValueChange = { newName = it },
                    label = { Text("새 카테고리 이름") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Spacer(Modifier.width(8.dp))
                Button(onClick = {
                    if (newName.isNotBlank()) {
                        onAdd(newName.trim(), PALETTE[categories.size % PALETTE.size])
                        newName = ""
                    }
                }) { Text("추가") }
            }

            Spacer(Modifier.height(16.dp))
            TextButton(onClick = onClose, modifier = Modifier.fillMaxWidth()) { Text("닫기") }
        }
    }
}

@Composable
private fun CategoryManageRow(
    category: EventCategory,
    canDelete: Boolean,
    onUpdate: (String, Long) -> Unit,
    onDelete: () -> Unit
) {
    var name by remember(category.id) { mutableStateOf(category.name) }

    Column {
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    onUpdate(it, category.color)
                },
                singleLine = true,
                modifier = Modifier.weight(1f)
            )
            if (canDelete) {
                IconButton(onClick = onDelete) {
                    Icon(Icons.Filled.Close, contentDescription = "삭제")
                }
            }
        }
        Row(Modifier.padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            PALETTE.forEach { colorLong ->
                val selected = colorLong == category.color
                Box(
                    Modifier
                        .size(22.dp)
                        .clip(CircleShape)
                        .background(Color(colorLong))
                        .border(
                            width = if (selected) 2.dp else 0.dp,
                            color = MaterialTheme.colorScheme.onSurface,
                            shape = CircleShape
                        )
                        .clickable { onUpdate(name, colorLong) }
                )
            }
        }
    }
}
