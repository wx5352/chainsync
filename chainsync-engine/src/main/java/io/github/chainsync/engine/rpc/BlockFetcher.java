package io.github.chainsync.engine.rpc;

import io.github.chainsync.core.model.Block;
import io.github.chainsync.core.model.LogEvent;
import org.web3j.protocol.core.DefaultBlockParameter;
import org.web3j.protocol.core.methods.request.EthFilter;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.EthLog;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.stream.Collectors;

/**
 * Fetches blocks (and optionally their logs) through a {@link RpcClientPool}, mapping them into the
 * core domain model. Range fetches run concurrently on virtual threads for fast catch-up.
 */
public final class BlockFetcher implements BlockSource {

    private final RpcClientPool pool;

    public BlockFetcher(RpcClientPool pool) {
        this.pool = pool;
    }

    /** Current chain head (the highest known block number). */
    @Override
    public BigInteger latestBlockNumber() {
        return pool.execute("eth_blockNumber", web3j -> {
            try {
                return web3j.ethBlockNumber().send().getBlockNumber();
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Fetch the hash of a block header at the given height, or empty if it does not exist. */
    @Override
    public Optional<String> blockHashAt(BigInteger number) {
        return pool.execute("eth_getBlockByNumber(header)", web3j -> {
            try {
                EthBlock.Block b = web3j
                        .ethGetBlockByNumber(DefaultBlockParameter.valueOf(number), false)
                        .send()
                        .getBlock();
                return b == null ? Optional.<String>empty() : Optional.ofNullable(b.getHash());
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
    }

    /** Fetch a single fully-populated block. */
    @Override
    public Block fetchBlock(BigInteger number, boolean includeTransactions, boolean includeLogs) {
        EthBlock.Block web3Block = pool.execute("eth_getBlockByNumber", web3j -> {
            try {
                EthBlock.Block b = web3j
                        .ethGetBlockByNumber(DefaultBlockParameter.valueOf(number), includeTransactions)
                        .send()
                        .getBlock();
                if (b == null) {
                    throw new RpcException("block " + number + " not found (null response)");
                }
                return b;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });

        Map<String, List<LogEvent>> logsByTx = Map.of();
        if (includeLogs) {
            logsByTx = fetchLogsGroupedByTx(number);
        }
        return Web3jMapper.toBlock(web3Block, logsByTx);
    }

    private Map<String, List<LogEvent>> fetchLogsGroupedByTx(BigInteger number) {
        List<LogEvent> logs = pool.execute("eth_getLogs", web3j -> {
            try {
                EthFilter filter = new EthFilter(
                        DefaultBlockParameter.valueOf(number),
                        DefaultBlockParameter.valueOf(number),
                        List.<String>of());
                EthLog ethLog = web3j.ethGetLogs(filter).send();
                List<LogEvent> out = new ArrayList<>();
                for (EthLog.LogResult<?> lr : ethLog.getLogs()) {
                    if (lr.get() instanceof org.web3j.protocol.core.methods.response.Log l) {
                        out.add(Web3jMapper.toLogEvent(l));
                    }
                }
                return out;
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        });
        return logs.stream()
                .filter(l -> l.txHash() != null)
                .collect(Collectors.groupingBy(LogEvent::txHash));
    }

    /**
     * Fetch an inclusive range of blocks concurrently, returned in ascending block-number order.
     *
     * @param from        first block (inclusive)
     * @param to          last block (inclusive)
     * @param concurrency maximum simultaneous in-flight fetches
     */
    @Override
    public List<Block> fetchRange(BigInteger from, BigInteger to, int concurrency,
                                  boolean includeTransactions, boolean includeLogs) {
        if (from.compareTo(to) > 0) {
            return List.of();
        }
        int permits = Math.max(1, concurrency);
        java.util.concurrent.Semaphore limiter = new java.util.concurrent.Semaphore(permits);

        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Future<Block>> futures = new ArrayList<>();
            for (BigInteger n = from; n.compareTo(to) <= 0; n = n.add(BigInteger.ONE)) {
                BigInteger blockNumber = n;
                Callable<Block> task = () -> {
                    limiter.acquire();
                    try {
                        return fetchBlock(blockNumber, includeTransactions, includeLogs);
                    } finally {
                        limiter.release();
                    }
                };
                futures.add(executor.submit(task));
            }

            List<Block> blocks = new ArrayList<>(futures.size());
            for (Future<Block> f : futures) {
                blocks.add(join(f));
            }
            blocks.sort(Comparator.comparing(Block::number));
            return blocks;
        }
    }

    private static Block join(Future<Block> f) {
        try {
            return f.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RpcException("interrupted while fetching block", e);
        } catch (ExecutionException e) {
            Throwable cause = e.getCause();
            if (cause instanceof RpcException re) {
                throw re;
            }
            throw new RpcException("failed to fetch block", cause);
        }
    }
}
