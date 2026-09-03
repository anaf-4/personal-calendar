package com.personalcalendar.app.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.personalcalendar.app.data.CalendarEvent
import com.personalcalendar.app.data.EventOccurrence
import java.time.LocalDate
import java.time.LocalTime

private val HOUR_HEIGHT = 56.dp
private val WEEKDAY_LABELS = listOf("일", "월", "화", "수", "목", "금", "토")

private data class LaidOutEvent(val occ: EventOccurrence, val startMin: Int, val endMin: Int, val col: Int, val totalCols: Int)

private fun timeToMinutes(hhmm: String): Int? {
    val parts = hhmm.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    return h * 60 + m
}

private fun layoutOverlaps(items: List<EventOccurrence>): List<LaidOutEvent> {
    data class Working(val occ: EventOccurrence, val start: Int, val end: Int, var col: Int = 0)

    val working = items.mapNotNull { occ ->
        val start = timeToMinutes(occ.event.start) ?: return@mapNotNull null
        var end = occ.event.end.takeIf { it.isNotBlank() }?.let { timeToMinutes(it) } ?: (start + 30)
        if (end <= start) end = start + 30
        Working(occ, start, end)
    }.sortedBy { it.start }

    val columns = mutableListOf<MutableList<Working>>()
    working.forEach { item ->
        val col = columns.indexOfFirst { it.last().end <= item.start }
        if (col >= 0) {
            columns[col].add(item)
            item.col = col
        } else {
            item.col = columns.size
            columns.add(mutableListOf(item))
        }
    }
    val totalCols = columns.size.coerceAtLeast(1)
    return working.map { LaidOutEvent(it.occ, it.start, it.end, it.col, totalCols) }
}

@Composable
fun TimeGridView(
    state: CalendarUiState,
    days: List<LocalDate>,
    onSlotClick: (LocalDate, String) -> Unit,
    onEventClick: (CalendarEvent, String) -> Unit,
    onHeaderClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier
) {
    val today = LocalDate.now()
    val rangeStart = days.first()
    val rangeEnd = days.last()
    val occurrences = state.occurrencesInRange(rangeStart, rangeEnd)
    val byDate = occurrences.groupBy { it.occurrenceDate }
    val scrollState = rememberScrollState()
    val density = LocalDensity.current

    LaunchedEffect(days.first()) {
        val target = with(density) { (HOUR_HEIGHT * 7).toPx().toInt() }
        scrollState.scrollTo(target)
    }

    Column(modifier.fillMaxSize()) {
        // Header row
        Row(Modifier.fillMaxWidth()) {
            Box(Modifier.width(44.dp))
            days.forEach { d ->
                val isToday = d == today
                Column(
                    Modifier
                        .weight(1f)
                        .clickable { onHeaderClick(d) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(WEEKDAY_LABELS[d.dayOfWeek.value % 7], fontSize = 11.sp, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(
                        d.dayOfMonth.toString(),
                        fontSize = 15.sp,
                        color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // All-day row
        val hasAllDay = days.any { d -> byDate[d.toString()].orEmpty().any { it.event.start.isBlank() } }
        if (hasAllDay) {
            Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
                Box(Modifier.width(44.dp))
                days.forEach { d ->
                    Column(Modifier.weight(1f).padding(horizontal = 2.dp)) {
                        byDate[d.toString()].orEmpty().filter { it.event.start.isBlank() }.forEach { occ ->
                            Box(
                                Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 1.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(Color(state.categoryById(occ.event.categoryId).color))
                                    .clickable { onEventClick(occ.event, occ.occurrenceDate) }
                            ) {
                                Text(
                                    occ.event.title,
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.padding(horizontal = 3.dp, vertical = 1.dp)
                                )
                            }
                        }
                    }
                }
            }
        }

        Row(
            Modifier
                .weight(1f)
                .verticalScroll(scrollState)
        ) {
            // Hour gutter
            Column(Modifier.width(44.dp)) {
                for (h in 0 until 24) {
                    Box(Modifier.height(HOUR_HEIGHT).fillMaxWidth(), contentAlignment = Alignment.TopEnd) {
                        Text(
                            "%02d:00".format(h),
                            fontSize = 10.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(end = 4.dp)
                        )
                    }
                }
            }

            days.forEach { d ->
                val timedItems = byDate[d.toString()].orEmpty().filter { it.event.start.isNotBlank() }
                val laid = layoutOverlaps(timedItems)
                DayTimeColumn(
                    date = d,
                    isToday = d == today,
                    laidOutEvents = laid,
                    categoryColor = { id -> Color(state.categoryById(id).color) },
                    onSlotClick = { minuteOfDay ->
                        val snapped = (minuteOfDay / 15) * 15
                        val hh = snapped / 60
                        val mm = snapped % 60
                        onSlotClick(d, "%02d:%02d".format(hh, mm))
                    },
                    onEventClick = { occ -> onEventClick(occ.event, occ.occurrenceDate) },
                    modifier = Modifier.weight(1f)
                )
            }
        }
    }
}

@Composable
private fun DayTimeColumn(
    date: LocalDate,
    isToday: Boolean,
    laidOutEvents: List<LaidOutEvent>,
    categoryColor: (String) -> Color,
    onSlotClick: (Int) -> Unit,
    onEventClick: (EventOccurrence) -> Unit,
    modifier: Modifier = Modifier
) {
    val density = LocalDensity.current
    val hourHeightPx = with(density) { HOUR_HEIGHT.toPx() }
    val totalHeight = HOUR_HEIGHT * 24
    val outline = MaterialTheme.colorScheme.outline
    val nowColor = MaterialTheme.colorScheme.error

    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .height(totalHeight)
            .border(0.5.dp, outline)
            .background(if (isToday) MaterialTheme.colorScheme.primary.copy(alpha = 0.05f) else Color.Transparent)
    ) {
        val fullWidth = maxWidth

        // Tap target for empty slots
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(hourHeightPx) {
                    detectTapGestures { offset ->
                        val minuteOfDay = (offset.y / hourHeightPx * 60f).toInt().coerceIn(0, 24 * 60 - 1)
                        onSlotClick(minuteOfDay)
                    }
                }
        )

        androidx.compose.foundation.Canvas(Modifier.fillMaxSize()) {
            for (h in 1 until 24) {
                val y = h * hourHeightPx
                drawLine(outline, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
            }
            if (isToday) {
                val now = LocalTime.now()
                val nowY = (now.hour * 60 + now.minute) / 60f * hourHeightPx
                drawLine(nowColor, Offset(0f, nowY), Offset(size.width, nowY), strokeWidth = 3f)
            }
        }

        laidOutEvents.forEach { laid ->
            val topDp = with(density) { (laid.startMin / 60f * hourHeightPx).toDp() }
            val heightDp = with(density) {
                ((laid.endMin - laid.startMin) / 60f * hourHeightPx).toDp().coerceAtLeast(18.dp)
            }
            val colWidth = fullWidth / laid.totalCols
            val leftDp = colWidth * laid.col
            Box(
                Modifier
                    .offset(x = leftDp, y = topDp)
                    .width(colWidth)
                    .padding(horizontal = 1.dp)
                    .height(heightDp)
                    .clip(RoundedCornerShape(4.dp))
                    .background(categoryColor(laid.occ.event.categoryId))
                    .clickable { onEventClick(laid.occ) }
                    .padding(3.dp)
            ) {
                Column {
                    Text(
                        laid.occ.event.title,
                        color = Color.White,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        laid.occ.event.start,
                        color = Color.White.copy(alpha = 0.85f),
                        fontSize = 9.sp,
                        maxLines = 1
                    )
                }
            }
        }
    }
}
