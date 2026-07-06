package io.github.chainsync.core.handler;

import io.github.chainsync.core.model.SyncedBlock;

/**
 * The single extension point most users implement: "given a synced block, do something with it".
 *
 * <p>The engine guarantees:
 * <ul>
 *   <li>Blocks for a given chain are delivered strictly in ascending order.</li>
 *   <li>{@link #onBlock} is called <em>before</em> the cursor advances, giving at-least-once
 *       delivery — if the process crashes after handling but before the cursor is saved, the block
 *       is re-delivered on restart. Handlers should therefore be idempotent.</li>
 *   <li>On a reorg, {@link #onReorg} is called for the rolled-back range before the corrected
 *       blocks are re-delivered.</li>
 * </ul>
 */
@FunctionalInterface
public interface BlockHandler {

    /** Handle a newly synced (confirmed) block. */
    void onBlock(SyncedBlock block) throws Exception;

    /**
     * Notify that blocks in {@code (fromBlockExclusive, toBlockInclusive]} on {@code chainId} were
     * invalidated by a reorganization and will be re-delivered. Default is a no-op; override to
     * purge derived state.
     */
    default void onReorg(long chainId, java.math.BigInteger fromBlockExclusive,
                         java.math.BigInteger toBlockInclusive) {
    }
}
