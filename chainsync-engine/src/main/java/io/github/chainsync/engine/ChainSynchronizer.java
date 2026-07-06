package io.github.chainsync.engine;

import io.github.chainsync.core.config.ChainConfig;
import io.github.chainsync.core.cursor.CursorStore;
import io.github.chainsync.core.handler.BlockHandler;
import io.github.chainsync.engine.rpc.BlockFetcher;
import io.github.chainsync.engine.rpc.RpcClientPool;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Top-level entry point of the engine. Given a set of {@link ChainConfig}s, a {@link CursorStore}
 * and a {@link BlockHandler}, it spins up one {@link SyncWorker} per chain and manages their
 * lifecycle. This is what the Spring Boot starter wires up automatically, and what a plain-Java
 * user constructs directly.
 */
public final class ChainSynchronizer implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(ChainSynchronizer.class);

    private final List<ChainConfig> chains;
    private final CursorStore cursorStore;
    private final BlockHandler handler;

    private final List<RpcClientPool> pools = new ArrayList<>();
    private final List<SyncWorker> workers = new ArrayList<>();
    private final List<Thread> threads = new ArrayList<>();

    private volatile boolean started = false;

    public ChainSynchronizer(List<ChainConfig> chains, CursorStore cursorStore, BlockHandler handler) {
        this.chains = List.copyOf(Objects.requireNonNull(chains, "chains"));
        this.cursorStore = Objects.requireNonNull(cursorStore, "cursorStore");
        this.handler = Objects.requireNonNull(handler, "handler");
    }

    /** Start a worker thread per configured chain. Idempotent. */
    public synchronized void start() {
        if (started) {
            return;
        }
        if (chains.isEmpty()) {
            log.warn("chainsync: no chains configured, nothing to synchronize");
        }
        for (ChainConfig chain : chains) {
            RpcClientPool pool = new RpcClientPool(chain.name(), chain.rpcUrls());
            BlockFetcher fetcher = new BlockFetcher(pool);
            SyncWorker worker = new SyncWorker(chain, fetcher, cursorStore, handler);

            Thread t = new Thread(worker, "chainsync-" + chain.name());
            t.setDaemon(true);

            pools.add(pool);
            workers.add(worker);
            threads.add(t);
        }
        threads.forEach(Thread::start);
        started = true;
        log.info("chainsync started with {} chain(s): {}", chains.size(),
                chains.stream().map(ChainConfig::name).toList());
    }

    /** Signal all workers to stop and release RPC resources. Idempotent. */
    public synchronized void stop() {
        if (!started) {
            return;
        }
        log.info("chainsync stopping {} worker(s)", workers.size());
        workers.forEach(SyncWorker::stop);
        threads.forEach(Thread::interrupt);
        for (Thread t : threads) {
            try {
                t.join(5_000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }
        pools.forEach(RpcClientPool::close);
        pools.clear();
        workers.clear();
        threads.clear();
        started = false;
    }

    public boolean isStarted() {
        return started;
    }

    @Override
    public void close() {
        stop();
    }
}
