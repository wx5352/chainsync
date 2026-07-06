package io.github.chainsync.core.model;

import java.util.List;
import java.util.Objects;

/**
 * A block delivered to a {@link io.github.chainsync.core.handler.BlockHandler}, tagged with the
 * chain it belongs to.
 *
 * @param chainId the numeric chain id the block was synced from (1 = Ethereum, 56 = BNB, 137 = Polygon)
 * @param chain   the human-readable chain name from configuration (e.g. {@code "ethereum"})
 * @param block   the block payload
 */
public record SyncedBlock(long chainId, String chain, Block block) {

    public SyncedBlock {
        Objects.requireNonNull(chain, "chain");
        Objects.requireNonNull(block, "block");
    }

    /** All logs across every transaction in this block, flattened for convenience. */
    public List<LogEvent> allLogs() {
        return block.transactions().stream()
                .flatMap(tx -> tx.logs().stream())
                .toList();
    }
}
