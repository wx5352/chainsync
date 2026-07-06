package io.github.chainsync.core.cursor;

import java.math.BigInteger;

/**
 * The last block that was successfully processed for a chain. The stored {@code hash} lets the
 * engine detect a reorg on restart by comparing it against the chain's current block at that height.
 *
 * @param chainId     numeric chain id
 * @param blockNumber last processed block height
 * @param blockHash   hash of that block
 */
public record SyncCursor(long chainId, BigInteger blockNumber, String blockHash) {
}
