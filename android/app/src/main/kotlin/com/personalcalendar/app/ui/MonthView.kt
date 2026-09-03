package com.personalcalendar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalcalendar.app.data.EventOccurrence
import java.time.LocalDate
import java.time.YearMonth

private val WEEKDAY_LABELS = listOf("일", "월", "화", "수", "목", "금", "토")

@Composable
fun WeekdayHeaderRow() {
    Row(Modifier.fillMaxWidth()) {
        WEEKDAY_LABELS.forEachIndexed { i, label ->
            Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
                Text(
                    label,
                    fontSize = 12.sp,
                    color = when (i) {
                        0 -> MaterialTheme.colorScheme.error
                        6 -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun MonthGrid(
    state: CalendarUiState,
    onDayClick: (LocalDate) -> Unit,
    onDayLongClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val cursor = state.cursorDate
    val yearMonth = YearMonth.of(cursor.year, cursor.month)
    val firstOfMonth = yearMonth.atDay(1)
    val startOffset = firstOfMonth.dayOfWeek.value % 7 // Sunday=0
    val gridStart = firstOfMonth.minusDays(startOffset.toLong())
    val gridEnd = gridStart.plusDays(41)
    val occurrences = state.occurrencesInRange(gridStart, gridEnd)
    val byDate: Map<String, List<EventOccurrence>> = occurrences.groupBy { it.occurrenceDate }
    val today = LocalDate.now()

    Column(modifier.fillMaxSize()) {
        for (row in 0 until 6) {
            Row(Modifier.weight(1f).fillMaxWidth()) {
                for (col in 0 until 7) {
                    val date = gridStart.plusDays((row * 7 + col).toLong())
                    val dayItems = byDate[date.toString()].orEmpty().sortedBy { it.event.start.ifBlank { "99:99" } }
                    DayCell(
                        date = date,
                        isCurrentMonth = date.month == cursor.month,
                        isToday = date == today,
                        isSelected = date == state.selectedDate,
                        items = dayItems,
                        categoryColor = { id -> Color(state.categoryById(id).color) },
                        onClick = { onDayClick(date) },
                        onLongClick = { onDayLongClick(date) },
                        modifier = Modifier.weight(1f).fillMaxSize().padding(2.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    date: LocalDate,
    isCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    items: List<EventOccurrence>,
    categoryColor: (String) -> Color,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val borderColor = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
    Column(
        modifier
            .clip(RoundedCornerShape(8.dp))
            .background(MaterialTheme.colorScheme.surface)
            .border(if (isSelected) 1.5.dp else 0.6.dp, borderColor, RoundedCornerShape(8.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(3.dp)
    ) {
        Box(
            Modifier
                .size(20.dp)
                .clip(CircleShape)
                .background(if (isToday) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center
        ) {
            Text(
                date.dayOfMonth.toString(),
                fontSize = 11.sp,
                color = when {
                    isToday -> Color.White
                    !isCurrentMonth -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.5f)
                    else -> MaterialTheme.colorScheme.onSurface
                }
            )
        }
        val maxShown = 3
        items.take(maxShown).forEach { occ ->
            Box(
                Modifier
                    .fillMaxWidth()
                    .padding(top = 1.dp)
                    .clip(RoundedCornerShape(3.dp))
                    .background(categoryColor(occ.event.categoryId))
            ) {
                Text(
                    if (occ.event.start.isNotBlank()) "${occ.event.start} ${occ.event.title}" else occ.event.title,
                    color = Color.White,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 3.dp)
                )
            }
        }
        if (items.size > maxShown) {
            Text(
                "+${items.size - maxShown}",
                fontSize = 9.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 1.dp, start = 2.dp)
            )
        }
    }
}
