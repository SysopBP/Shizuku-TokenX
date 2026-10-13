package moe.shizuku.manager.ui

import org.junit.Assert.*
import org.junit.Test

class TokenXNavigationTest {
    @Test fun `back returns from a result to search and then its originating tab`() {
        val start = TokenXNavigationState().navigate("system")
        val search = start.navigate(Detail.SEARCH.route())
        val detail = search.navigate(Detail.WIRELESS.route())
        assertEquals(search, detail.back())
        assertEquals(start, detail.back().back())
    }

    @Test fun `nested permission pages return to their settings group`() {
        val group = TokenXNavigationState().navigate("tools").navigate(Detail.ACCESS.route())
        assertEquals(group, group.navigate(Detail.PERMISSIONS.route()).back())
        assertEquals("tools", group.back().current)
    }

    @Test fun `reselecting a page does not grow back history`() {
        val state = TokenXNavigationState().navigate("apps")
        assertEquals(state, state.navigate("apps"))
        assertEquals("home", state.back().current)
    }

    @Test fun `every search result opens a real destination`() {
        assertEquals(TokenXSearchCatalog.entries.size, TokenXSearchCatalog.entries.map { it.destination }.distinct().size)
        TokenXSearchCatalog.entries.forEach { assertTrue(validTokenXRoute(it.destination.route())) }
    }

    @Test fun `search finds technical aliases and respects all query words`() {
        assertTrue(TokenXSearchCatalog.search(" UID 1000 ").any { it.destination == Detail.CONNECTIONS })
        assertEquals(Detail.STARTUP, TokenXSearchCatalog.search("boot watchdog").single().destination)
        assertEquals(Detail.TERMINAL, TokenXSearchCatalog.search("rish export").single().destination)
        assertTrue(TokenXSearchCatalog.search("unrelated missing setting").isEmpty())
    }

    @Test fun `narrow dock retains touch targets and fits without overlapping CORE`() {
        for (available in listOf(304f, 344f, 584f)) {
            val core = if (available < 340f) 48f else 56f
            val width = dockWidth(available, .62f, core)
            assertTrue(width <= available)
            assertTrue((width - core - 8f) / 5f >= 48f)
        }
    }

    @Test fun `large font labels get enough height and content clearance`() {
        assertTrue(dockHeight(56f, 2f) >= 22f + 3f + 16f + 24f)
        assertTrue(dockReservedHeight(56f, 8f, 2f) > dockReservedHeight(56f, 8f))
    }

    @Test fun `content reservation covers maximum dock height and bottom gap`() {
        assertTrue(dockReservedHeight(78f, 28f) >= 78f + 2f * (8f + 28f))
    }
}
