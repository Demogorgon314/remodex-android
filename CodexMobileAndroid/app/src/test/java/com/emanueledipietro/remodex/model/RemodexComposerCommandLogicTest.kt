package com.emanueledipietro.remodex.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class RemodexComposerCommandLogicTest {
    @Test
    fun filteredSlashCommands_keepPsAndStopAtTheBottomByDefault() {
        assertEquals(
            listOf(
                RemodexSlashCommand.CODE_REVIEW,
                RemodexSlashCommand.FORK,
                RemodexSlashCommand.STATUS,
                RemodexSlashCommand.COMPACT,
                RemodexSlashCommand.PLAN,
                RemodexSlashCommand.SUBAGENTS,
                RemodexSlashCommand.PS,
                RemodexSlashCommand.STOP,
            ),
            RemodexSlashCommand.filtered(""),
        )
    }

    @Test
    fun trailingSlashCommandToken_acceptsBareSlash() {
        val token = RemodexComposerCommandLogic.trailingSlashCommandToken("/")

        assertNotNull(token)
        assertEquals("", token?.query)
        assertEquals(0, token?.startIndex)
    }

    @Test
    fun trailingSlashCommandToken_acceptsSlashQuery() {
        val token = RemodexComposerCommandLogic.trailingSlashCommandToken("/rev")

        assertNotNull(token)
        assertEquals("rev", token?.query)
        assertEquals(0, token?.startIndex)
    }

    @Test
    fun trailingSlashCommandToken_rejectsWhitespaceAfterSlash() {
        assertNull(RemodexComposerCommandLogic.trailingSlashCommandToken("/ "))
        assertNull(RemodexComposerCommandLogic.trailingSlashCommandToken("hello / status"))
    }

    @Test
    fun replaceTrailingSlashCommandToken_replacesTheActiveToken() {
        assertEquals(
            "/compact ",
            RemodexComposerCommandLogic.replaceTrailingSlashCommandToken(
                text = "/com",
                commandToken = "/compact",
            ),
        )
        assertEquals(
            "please run /compact ",
            RemodexComposerCommandLogic.replaceTrailingSlashCommandToken(
                text = "please run /com",
                commandToken = "/compact",
            ),
        )
    }
}
