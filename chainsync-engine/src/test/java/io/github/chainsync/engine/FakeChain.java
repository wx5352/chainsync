package io.github.chainsync.engine;

import io.github.chainsync.core.model.Block;
import io.github.chainsync.engine.rpc.BlockSource;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.TreeMap;

/**
 * An in-memory, deterministic {@link BlockSource} used to drive {@link SyncWorker} in tests.
 * It supports rewriting a suffix of the chain to simulate reorganizations.
 */
final class FakeChain implements BlockSource {

    private final TreeMap<BigInteger, Block> blocks = new TreeMap<>();
    private BigInteger head = BigInteger.valueOf(-1);

    /** Append {@code count} blocks, each linked to the previous by parentHash, using {@code tag} in hashes. */
    void append(int count, String tag) {
        for (int i = 0; i < count; i++) {
            BigInteger number = head.add(BigInteger.ONE);
            String parentHash = number.signum() == 0 ? "0x00" : blocks.get(number.subtract(BigInteger.ONE)).hash();
            String hash = "0x" + tag + "-" + number;
            blocks.put(number, new Block(number, hash, parentHash, BigInteger.valueOf(number.longValue()), List.of()));
            head = number;
        }
    }

    /** Simulate a reorg: discard blocks above {@code forkPoint} and rebuild them with a new tag. */
    void reorgFrom(long forkPoint, int newLength, String tag) {
        blocks.tailMap(BigInteger.valueOf(forkPoint + 1), true).clear();
        head = BigInteger.valueOf(forkPoint);
        append(newLength, tag);
    }

    @Override
    public BigInteger latestBlockNumber() {
        return head;
    }

    @Override
    public Optional<String> blockHashAt(BigInteger number) {
        Block b = blocks.get(number);
        return b == null ? Optional.empty() : Optional.of(b.hash());
    }

    @Override
    public Block fetchBlock(BigInteger number, boolean includeTransactions, boolean includeLogs) {
        Block b = blocks.get(number);
        if (b == null) {
            throw new IllegalStateException("no block at " + number);
        }
        return b;
    }

    @Override
    public List<Block> fetchRange(BigInteger from, BigInteger to, int concurrency,
                                  boolean includeTransactions, boolean includeLogs) {
        List<Block> out = new ArrayList<>();
        for (BigInteger n = from; n.compareTo(to) <= 0; n = n.add(BigInteger.ONE)) {
            out.add(fetchBlock(n, includeTransactions, includeLogs));
        }
        return out;
    }
}
