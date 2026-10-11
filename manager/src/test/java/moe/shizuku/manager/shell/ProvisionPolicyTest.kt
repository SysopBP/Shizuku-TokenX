package moe.shizuku.manager.shell

import org.junit.Assert.*
import org.junit.Test

class ProvisionPolicyTest {
    @Test fun directReadinessRequiresOriginalProcessAndFreshRequest() {
        assertTrue(ProvisionPolicy.acceptsDirect(100, 100, 700, 700, 17L, 17L, true, true))
        assertFalse(ProvisionPolicy.acceptsDirect(100, 200, 700, 700, 17L, 17L, true, true))
        assertFalse(ProvisionPolicy.acceptsDirect(100, 100, 700, 674, 17L, 17L, true, true))
        assertFalse(ProvisionPolicy.acceptsDirect(100, 100, 700, 700, 17L, 16L, true, true))
        assertFalse(ProvisionPolicy.acceptsDirect(100, 100, 700, 700, 0L, 0L, true, true))
        assertFalse(ProvisionPolicy.acceptsDirect(0, 100, 700, 700, 17L, 17L, true, true))
        assertFalse(ProvisionPolicy.acceptsDirect(100, 100, 700, 700, 17L, 17L, false, true))
        assertFalse(ProvisionPolicy.acceptsDirect(100, 100, 700, 700, 17L, 17L, true, false))
    }

    @Test fun readinessRequiresRestartAndMatchingPayload() {
        assertTrue(ProvisionPolicy.accepts(100, 200, 700, 700, true, true))
        assertFalse(ProvisionPolicy.accepts(100, 100, 700, 700, true, true))
        assertFalse(ProvisionPolicy.accepts(100, 200, 700, 674, true, true))
        assertFalse(ProvisionPolicy.accepts(0, 200, 700, 700, true, true))
        assertFalse(ProvisionPolicy.accepts(100, 200, 700, 700, false, true))
        assertFalse(ProvisionPolicy.accepts(100, 200, 700, 700, true, false))
    }

    @Test fun quotingKeepsApostrophesAndShellMetacharactersLiteral() {
        assertEquals("'a'\\''b'", ProvisionPolicy.quote("a'b"))
        assertEquals("'a; echo nope'", ProvisionPolicy.quote("a; echo nope"))
        assertEquals("'\$(touch /tmp/nope)'", ProvisionPolicy.quote("\$(touch /tmp/nope)"))
    }
}
