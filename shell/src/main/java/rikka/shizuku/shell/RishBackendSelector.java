package rikka.shizuku.shell;

import java.util.Arrays;

/** Parses session selectors before shell arguments; a CLI selector overrides the environment. */
public final class RishBackendSelector {
    public static final class Selection {
        public final String backend;
        public final String[] arguments;

        private Selection(String backend, String[] arguments) {
            this.backend = backend;
            this.arguments = arguments;
        }
    }

    public static Selection select(String[] arguments, String environment) {
        String backend = environment;
        int consumed = 0;
        if (arguments.length > 0) {
            String option = arguments[0].replace("—", "--").replace("–", "--");
            if (option.equals("--root") || option.equals("--system") || option.equals("--shell")) {
                backend = option.substring(2);
                consumed = 1;
            }
        }
        if (backend != null && backend.isEmpty()) backend = null;
        if ("sserver".equals(backend)) backend = "system";
        if (backend != null && !backend.equals("root") && !backend.equals("system") && !backend.equals("shell")) {
            throw new IllegalArgumentException("Unknown TOKENX_RISH_BACKEND: " + backend
                    + ". Use root, system or shell.");
        }
        return new Selection(backend, Arrays.copyOfRange(arguments, consumed, arguments.length));
    }
}
