package dev.redfox.messages.feature.conversations

import dev.redfox.messages.core.model.Conversation
import dev.redfox.messages.core.model.MessageCategory
import kotlinx.collections.immutable.persistentMapOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SelectionTest {

    private fun conversation(id: Long, unread: Int = 0, archived: Boolean = false) = Conversation(
        threadId = id,
        participants = emptyList(),
        snippet = "",
        timestamp = 0,
        unreadCount = unread,
        messageCount = 1,
        hasAttachment = false,
        category = MessageCategory.entries.first(),
        isArchived = archived,
    )

    @Test
    fun `toggling adds a conversation, then removes it`() {
        val a = conversation(1)
        val once = persistentMapOf<Long, Conversation>().toggled(a)
        assertEquals(setOf(1L), once.keys)
        assertTrue(once.toggled(a).isEmpty())
    }

    @Test
    fun `no selection is not selecting`() {
        val state = ConversationsState()
        assertFalse(state.isSelecting)
        assertFalse(state.selectionUnarchives)
    }

    @Test
    fun `any unread conversation makes the read action mark read`() {
        val state = ConversationsState(selected = persistentMapOf(1L to conversation(1), 2L to conversation(2, unread = 3)))
        assertTrue(state.selectionMarksRead)
    }

    @Test
    fun `an all-read selection marks unread`() {
        val state = ConversationsState(selected = persistentMapOf(1L to conversation(1), 2L to conversation(2)))
        assertFalse(state.selectionMarksRead)
    }

    @Test
    fun `unarchive only when everything selected is archived`() {
        val mixed = ConversationsState(selected = persistentMapOf(1L to conversation(1, archived = true), 2L to conversation(2)))
        val archived = ConversationsState(selected = persistentMapOf(1L to conversation(1, archived = true)))
        assertFalse(mixed.selectionUnarchives)
        assertTrue(archived.selectionUnarchives)
    }
}
