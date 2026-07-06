package io.github.chainsync.core.config;

import java.math.BigInteger;
import java.util.Objects;

/**
 * Where a chain should begin syncing when there is no persisted cursor yet.
 *
 * <ul>
 *   <li>{@link Mode#LATEST} — start from the current chain head (only new blocks).</li>
 *   <li>{@link Mode#EARLIEST} — start from the genesis block (full historical backfill).</li>
 *   <li>{@link Mode#BLOCK} — start from an explicit block number.</li>
 * </ul>
 */
public record StartFrom(Mode mode, BigInteger block) {

    public enum Mode { LATEST, EARLIEST, BLOCK }

    public StartFrom {
        Objects.requireNonNull(mode, "mode");
        if (mode == Mode.BLOCK) {
            Objects.requireNonNull(block, "block must be set when mode is BLOCK");
        }
    }

    public static StartFrom latest() {
        return new StartFrom(Mode.LATEST, null);
    }

    public static StartFrom earliest() {
        return new StartFrom(Mode.EARLIEST, BigInteger.ZERO);
    }

    public static StartFrom block(long number) {
        return new StartFrom(Mode.BLOCK, BigInteger.valueOf(number));
    }

    /**
     * Parse a textual value such as {@code "latest"}, {@code "earliest"}, {@code "0"} or {@code "18000000"}.
     */
    public static StartFrom parse(String value) {
        if (value == null || value.isBlank() || value.equalsIgnoreCase("latest")) {
            return latest();
        }
        if (value.equalsIgnoreCase("earliest") || value.equalsIgnoreCase("genesis")) {
            return earliest();
        }
        return new StartFrom(Mode.BLOCK, new BigInteger(value.trim()));
    }
}
