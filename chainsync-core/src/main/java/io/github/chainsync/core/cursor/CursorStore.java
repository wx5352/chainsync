package io.github.chainsync.core.cursor;

import java.math.BigInteger;
import java.util.Optional;

/**
 * Persists per-chain sync progress so the engine can resume exactly where it left off after a
 * restart. This is the backbone of the "resume / no data loss" guarantee.
 *
 * <p>Implementations must be thread-safe: one worker per chain calls {@link #save} on its own
 * thread, and different chains use different {@code chainId} keys.
 */
public interface CursorStore {

    /** Return the last processed cursor for the chain, or empty if the chain has never synced. */
    Optional<SyncCursor> load(long chainId);

    /** Persist the latest processed cursor for the chain. Must be durable before returning. */
    void save(SyncCursor cursor);

    /**
     * Roll the cursor back to {@code blockNumber} during reorg recovery. Implementations should
     * drop the stored hash (or set it from the caller on the next {@link #save}).
     */
    void rewind(long chainId, BigInteger blockNumber);
}
