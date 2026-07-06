package io.github.chainsync.engine.rpc;

import io.github.chainsync.core.model.Block;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Read side of a chain the engine syncs from. Extracted from {@link BlockFetcher} so the worker
 * loop can be driven by a test double without any network.
 */
public interface BlockSource {

    /** The highest known block number on the chain. */
    BigInteger latestBlockNumber();

    /** Canonical block hash at a height, or empty if the block does not exist. */
    Optional<String> blockHashAt(BigInteger number);

    /** Fetch a single fully-populated block. */
    Block fetchBlock(BigInteger number, boolean includeTransactions, boolean includeLogs);

    /** Fetch an inclusive range of blocks, returned in ascending block-number order. */
    List<Block> fetchRange(BigInteger from, BigInteger to, int concurrency,
                           boolean includeTransactions, boolean includeLogs);
}
