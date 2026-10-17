package dev.redfox.messages.feature.thread

import androidx.compose.runtime.Immutable
import dev.redfox.messages.core.model.Message
import dev.redfox.messages.core.ui.components.BubblePosition
import dev.redfox.messages.core.ui.format.BrandLink
import dev.redfox.messages.core.ui.format.TimeFormat

/** A row of the paged thread list: a day divider or a message. */
@Immutable
sealed interface ThreadRow {
    val key: String

    data class Day(override val key: String, val label: String) : ThreadRow

    /** [brandLinks] are found while paging (off the main thread), not while drawing. */
    data class Msg(val message: Message, val brandLinks: List<BrandLink> = emptyList()) : ThreadRow {
        override val key: String get() = message.key
    }
}

/** How a bubble renders given its neighbours. */
data class BubbleLayout(
    val position: BubblePosition,
    /** Shown under the last bubble of a run only. */
    val timeLabel: String?,
    /** Group threads: name above the first incoming bubble of a run. */
    val showSender: Boolean,
)

/** Consecutive messages from the same side within this window share a bubble run. */
private const val RunWindowMillis = 3 * 60_000L

/**
 * Pure and neighbour-local, so it works on a paged list without ever scanning the thread:
 * the UI calls it with the rows directly above ([older]) and below ([newer]) a message.
 */
fun bubbleLayout(
    older: Message?,
    message: Message,
    newer: Message?,
    isGroup: Boolean,
    format: TimeFormat,
): BubbleLayout {
    fun sameRun(a: Message?, b: Message?): Boolean =
        a != null && b != null &&
            a.isOutgoing == b.isOutgoing &&
            a.sender?.address == b.sender?.address &&
            b.timestamp - a.timestamp in 0..RunWindowMillis &&
            format.localDate(a.timestamp) == format.localDate(b.timestamp)

    val joinsOlder = sameRun(older, message)
    val joinsNewer = sameRun(message, newer)
    return BubbleLayout(
        position = when {
            joinsOlder && joinsNewer -> BubblePosition.Middle
            joinsOlder -> BubblePosition.Last
            joinsNewer -> BubblePosition.First
            else -> BubblePosition.Single
        },
        timeLabel = if (joinsNewer) null else format.messageTime(message.timestamp),
        showSender = isGroup && !message.isOutgoing && !joinsOlder,
    )
}

/**
 * Paging separator for a newest-first list: between [newer] and the next-older row, insert
 * [newer]'s day when the day changes, and after the very oldest message (when [older] is
 * null at the end of the thread). In a `reverseLayout` list that puts each divider above its day.
 */
fun daySeparator(newer: Message?, older: Message?, format: TimeFormat, nowMillis: Long): ThreadRow.Day? {
    if (newer == null) return null
    val day = format.localDate(newer.timestamp)
    if (older != null && format.localDate(older.timestamp) == day) return null
    return ThreadRow.Day(key = "day-$day", label = format.dayLabel(day, nowMillis))
}
