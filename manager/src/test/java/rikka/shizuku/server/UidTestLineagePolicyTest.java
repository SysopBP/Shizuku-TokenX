package rikka.shizuku.server;

import static org.junit.Assert.*;
import java.util.List;
import org.junit.Test;
import rikka.shizuku.server.UidTestLineagePolicy.Certificate;

public class UidTestLineagePolicyTest {
    private final Certificate platform = new Certificate("platform", 31);
    private final List<Certificate> original = List.of(new Certificate("rotated-platform", 9), platform);

    @Test public void enrollmentPreservesPlatformLineageAndFlags() {
        List<Certificate> after = UidTestLineagePolicy.enroll(platform, original, "disposable");
        assertEquals(new Certificate("disposable", 2), after.get(0));
        assertEquals(original, after.subList(1, after.size()));
        assertEquals(2, original.size());
    }
    @Test public void nullLineageRetainsTheRealCurrentPlatformTail() {
        assertEquals(List.of(new Certificate("test", 2), platform), UidTestLineagePolicy.enroll(platform, null, "test"));
    }
    @Test public void restoreAllowsExactRoundTripAndInterruptedPreMutationJournal() {
        List<Certificate> after = UidTestLineagePolicy.enroll(platform, original, "test");
        assertTrue(UidTestLineagePolicy.restore("platform", "platform", original, after, after, List.of()));
        assertFalse(UidTestLineagePolicy.restore("platform", "platform", original, after, original, List.of()));
        assertFalse(UidTestLineagePolicy.restore("platform", "platform", null, after, null, List.of()));
    }
    @Test public void restoreRejectsAnotherConsumerEvenIfLineageWasAlreadyRestored() {
        assertThrows(IllegalStateException.class, () -> UidTestLineagePolicy.restore("platform", "platform",
                original, original, original, List.of("another.system.app")));
    }
    @Test public void restoreRejectsIndependentFlagChangesAndPlatformRotation() {
        List<Certificate> after = UidTestLineagePolicy.enroll(platform, original, "test");
        assertThrows(IllegalStateException.class, () -> UidTestLineagePolicy.restore("platform", "platform",
                original, after, List.of(new Certificate("test", 31), original.get(0), platform), List.of()));
        assertThrows(IllegalStateException.class, () -> UidTestLineagePolicy.restore("new-platform", "platform",
                original, after, after, List.of()));
    }
    @Test public void enrollmentRejectsWrongTailDuplicateTestAndPlatformCertificate() {
        assertThrows(IllegalStateException.class, () -> UidTestLineagePolicy.enroll(platform,
                List.of(new Certificate("different-current", 31)), "test"));
        assertThrows(IllegalStateException.class, () -> UidTestLineagePolicy.enroll(platform,
                List.of(new Certificate("test", 0), platform), "test"));
        assertThrows(IllegalStateException.class, () -> UidTestLineagePolicy.enroll(platform, null, "platform"));
    }
    @Test public void enrollmentNeverCreatesALineageTooLargeForRecovery() {
        java.util.ArrayList<Certificate> max = new java.util.ArrayList<>();
        for (int i = 0; i < 30; i++) max.add(new Certificate("old" + i, i));
        max.add(platform);
        assertEquals(32, UidTestLineagePolicy.enroll(platform, max, "test").size());
        max.add(0, new Certificate("one-too-many", 2));
        assertThrows(IllegalStateException.class, () -> UidTestLineagePolicy.enroll(platform, max, "test"));
    }
}
