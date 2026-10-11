package rikka.shizuku.server;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Pure signing-lineage rules, independent of hidden Android reflection. */
public final class UidTestLineagePolicy {
    public record Certificate(String value, int flags) { }

    public static List<Certificate> enroll(Certificate current, List<Certificate> before, String test) {
        if (test.equals(current.value())) throw new IllegalStateException("Test must not use the platform certificate");
        if (before != null && (before.isEmpty() || before.size() > 32 ||
                !before.get(before.size() - 1).value().equals(current.value())))
            throw new IllegalStateException("Unsupported platform signing lineage");
        if (before != null && before.stream().anyMatch(c -> c.value().equals(test)))
            throw new IllegalStateException("Test certificate already present in platform lineage");
        List<Certificate> result = new ArrayList<>();
        result.add(new Certificate(test, 2));
        if (before == null) result.add(current);
        else result.addAll(before);
        return result;
    }

    /** Returns false if already restored; otherwise true only for the exact recorded state. */
    public static boolean restore(String currentPlatform, String recordedPlatform,
            List<Certificate> before, List<Certificate> after, List<Certificate> now,
            List<String> certificateConsumers) {
        if (!certificateConsumers.isEmpty()) throw new IllegalStateException("Test certificate is still used by " + certificateConsumers);
        if (!currentPlatform.equals(recordedPlatform)) throw new IllegalStateException("Platform certificate changed; recovery refused");
        if (Objects.equals(now, before)) return false;
        if (!Objects.equals(now, after)) throw new IllegalStateException("Signing lineage changed independently; recovery record retained");
        return true;
    }
}
