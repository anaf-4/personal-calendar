package com.personalcalendar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalcalendar.app.data.EventOccurrence
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

private val LABEL_FMT = DateTimeFormatter.ofPattern("M월 d일 (E)", Locale.KOREAN)

@Composable
fun EventListSection(
    selectedDate: LocalDate,
    occurrences: List<EventOccurrence>,
    categoryColor: (String) -> Color,
    onEventClick: (EventOccurrence) -> Unit,
    modifier: Modifier = Modifier
) {
    val sorted = occurrences.sortedBy { it.event.start.ifBlank { "99:99" } }
    Column(modifier.fillMaxSize()) {
        Text(
            selectedDate.format(LABEL_FMT),
            style = MaterialTheme.typography.titleSmall,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
        )
        if (sorted.isEmpty()) {
            Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                Text("이 날의 일정이 없습니다.", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 13.sp)
            }
        } else {
            LazyColumn(
                Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp)
            ) {
                items(sorted, key = { it.event.id + it.occurrenceDate }) { occ ->
                    EventRow(occ, categoryColor(occ.event.categoryId), onClick = { onEventClick(occ) })
                }
            }
        }
    }
}

@Composable
private fun EventRow(occ: EventOccurrence, color: Color, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(0.6.dp, MaterialTheme.colorScheme.outline, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
    ) {
        Box(
            Modifier
                .width(4.dp)
                .fillMaxHeight()
                .background(color)
        )
        Column(Modifier.padding(12.dp)) {
            Row {
                Text(occ.event.title, fontSize = 14.sp, color = MaterialTheme.colorScheme.onSurface)
                if (occ.event.recurrence.freq != "none") {
                    Text("  🔁", fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (occ.event.start.isNotBlank() || occ.event.end.isNotBlank()) {
                Text(
                    listOfNotNull(occ.event.start.ifBlank { null }, occ.event.end.ifBlank { null }).joinToString(" - "),
                    fontSize = 12.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (occ.event.memo.isNotBlank()) {
                Text(occ.event.memo, fontSize = 12.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
