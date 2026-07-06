package io.github.chainsync.engine;

import io.github.chainsync.core.config.ChainConfig;
import io.github.chainsync.core.config.StartFrom;
import io.github.chainsync.core.cursor.CursorStore;
import io.github.chainsync.core.cursor.SyncCursor;
import io.github.chainsync.core.handler.BlockHandler;
import io.github.chainsync.core.model.Block;
import io.github.chainsync.core.model.SyncedBlock;
import io.github.chainsync.engine.rpc.BlockSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Synchronizes a single EVM chain. One worker runs per chain on its own thread and drives the
 * adaptive loop:
 *
 * <ol>
 *   <li>Resume from the persisted cursor, or resolve the configured start position.</li>
 *   <li>Ask the node for the head; target {@code head - confirmations} to skip unstable blocks.</li>
 *   <li>If behind, fetch the next batch concurrently and deliver blocks in order.</li>
 *   <li>Verify each block's {@code parentHash} links to the previous one; on a mismatch, rewind
 *       and re-sync (reorg recovery).</li>
 *   <li>Persist the cursor <em>after</em> handling each block (at-least-once delivery).</li>
 *   <li>Once caught up, sleep for {@code pollIntervalMs} and repeat.</li>
 * </ol>
 */
public final class SyncWorker implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(SyncWorker.class);

    private final ChainConfig config;
    private final BlockSource fetcher;
    private final CursorStore cursorStore;
    private final BlockHandler handler;
    private final int maxReorgDepth;

    private volatile boolean running = true;

    private BigInteger lastProcessed;
    private String lastProcessedHash;
    private BigInteger startFloor;

    public SyncWorker(ChainConfig config, BlockSource fetcher,
                      CursorStore cursorStore, BlockHandler handler) {
        this.config = config;
        this.fetcher = fetcher;
        this.cursorStore = cursorStore;
        this.handler = handler;
        this.maxReorgDepth = Math.max(config.confirmations(), 32);
    }

    public String chainName() {
        return config.name();
    }

    BigInteger lastProcessed() {
        return lastProcessed;
    }

    public void stop() {
        this.running = false;
    }

    @Override
    public void run() {
        try {
            initStartPosition();
            log.info("[{}] starting sync from block {} (chainId={}, confirmations={})",
                    config.name(), lastProcessed.add(BigInteger.ONE), config.chainId(), config.confirmations());

            while (running && !Thread.currentThread().isInterrupted()) {
                try {
                    syncOnce();
                } catch (RuntimeException ex) {
                    log.error("[{}] sync iteration failed, backing off: {}", config.name(), ex.toString());
                    sleep(config.pollIntervalMs());
                }
            }
        } catch (RuntimeException fatal) {
            log.error("[{}] worker terminated abnormally", config.name(), fatal);
        } finally {
            log.info("[{}] worker stopped at block {}", config.name(), lastProcessed);
        }
    }

    void initStartPosition() {
        Optional<SyncCursor> saved = cursorStore.load(config.chainId());
        if (saved.isPresent()) {
            SyncCursor c = saved.get();
            lastProcessed = c.blockNumber();
            lastProcessedHash = c.blockHash();
            startFloor = c.blockNumber();
            log.info("[{}] resuming from persisted cursor at block {}", config.name(), lastProcessed);
            return;
        }

        StartFrom start = config.startFrom();
        BigInteger anchor = switch (start.mode()) {
            case LATEST -> fetcher.latestBlockNumber();
            case EARLIEST -> BigInteger.valueOf(-1);
            case BLOCK -> start.block().subtract(BigInteger.ONE);
        };
        lastProcessed = anchor;
        lastProcessedHash = null;
        startFloor = anchor;
    }

    void syncOnce() {
        BigInteger latest = fetcher.latestBlockNumber();
        BigInteger confirmedHead = latest.subtract(BigInteger.valueOf(config.confirmations()));

        if (confirmedHead.signum() < 0 || lastProcessed.compareTo(confirmedHead) >= 0) {
            sleep(config.pollIntervalMs());
            return;
        }

        BigInteger from = lastProcessed.add(BigInteger.ONE);
        BigInteger maxTo = lastProcessed.add(BigInteger.valueOf(config.batchSize()));
        BigInteger to = maxTo.min(confirmedHead);

        List<Block> blocks = fetcher.fetchRange(
                from, to, config.concurrency(),
                config.includeTransactions(), config.includeLogs());

        for (Block block : blocks) {
            if (lastProcessedHash != null && !lastProcessedHash.equals(block.parentHash())) {
                recoverFromReorg();
                return;
            }
            deliver(block);
            advanceCursor(block);
        }
    }

    private void deliver(Block block) {
        SyncedBlock synced = new SyncedBlock(config.chainId(), config.name(), block);
        try {
            handler.onBlock(synced);
        } catch (Exception e) {
            throw new io.github.chainsync.engine.rpc.RpcException(
                    "[%s] handler failed for block %s".formatted(config.name(), block.number()), e);
        }
    }

    private void advanceCursor(Block block) {
        cursorStore.save(new SyncCursor(config.chainId(), block.number(), block.hash()));
        lastProcessed = block.number();
        lastProcessedHash = block.hash();
    }

    /**
     * A reorg was detected: the next block's parent no longer matches our tip. Rewind a bounded
     * number of blocks, re-anchor to the canonical chain, and let the loop re-sync forward.
     */
    private void recoverFromReorg() {
        BigInteger before = lastProcessed;
        BigInteger target = before.subtract(BigInteger.valueOf(maxReorgDepth)).max(startFloor);

        log.warn("[{}] reorg detected at block {}: rewinding to {}", config.name(), before.add(BigInteger.ONE), target);

        handler.onReorg(config.chainId(), target, before);
        cursorStore.rewind(config.chainId(), target);

        lastProcessed = target;
        lastProcessedHash = target.signum() >= 0
                ? fetcher.blockHashAt(target).orElse(null)
                : null;
    }

    private void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            running = false;
        }
    }
}
