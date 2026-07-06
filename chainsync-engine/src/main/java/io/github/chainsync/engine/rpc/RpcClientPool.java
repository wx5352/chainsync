package io.github.chainsync.engine.rpc;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.web3j.protocol.Web3j;
import org.web3j.protocol.http.HttpService;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;

/**
 * A rotating pool of web3j clients providing RPC failover and bounded retry with exponential
 * backoff. When a call against one endpoint fails, the pool advances to the next endpoint and
 * retries, so a single flaky or rate-limited provider does not stall synchronization.
 */
public final class RpcClientPool implements AutoCloseable {

    private static final Logger log = LoggerFactory.getLogger(RpcClientPool.class);

    private final String chainName;
    private final List<String> endpoints;
    private final List<Web3j> clients;
    private final AtomicInteger cursor = new AtomicInteger(0);

    private final int maxAttempts;
    private final long baseBackoffMs;
    private final long maxBackoffMs;

    public RpcClientPool(String chainName, List<String> endpoints) {
        this(chainName, endpoints, Math.max(endpoints.size() * 2, 5), 250L, 10_000L);
    }

    public RpcClientPool(String chainName, List<String> endpoints,
                         int maxAttempts, long baseBackoffMs, long maxBackoffMs) {
        this.chainName = Objects.requireNonNull(chainName);
        if (endpoints == null || endpoints.isEmpty()) {
            throw new IllegalArgumentException("endpoints must not be empty");
        }
        this.endpoints = List.copyOf(endpoints);
        this.clients = new ArrayList<>(this.endpoints.size());
        for (String url : this.endpoints) {
            this.clients.add(Web3j.build(new HttpService(url)));
        }
        this.maxAttempts = maxAttempts;
        this.baseBackoffMs = baseBackoffMs;
        this.maxBackoffMs = maxBackoffMs;
    }

    /**
     * Execute an RPC operation with failover. The supplied function receives a live {@link Web3j}
     * client; if it throws, the pool rotates to the next endpoint and retries.
     *
     * @param opName human-readable operation name, used only for logging
     * @param call   the RPC interaction to perform
     * @return the successful result
     * @throws RpcException if every attempt across all endpoints fails
     */
    public <T> T execute(String opName, Function<Web3j, T> call) {
        RuntimeException last = null;
        for (int attempt = 0; attempt < maxAttempts; attempt++) {
            int idx = Math.floorMod(cursor.get(), clients.size());
            Web3j client = clients.get(idx);
            try {
                return call.apply(client);
            } catch (RuntimeException ex) {
                last = ex;
                log.warn("[{}] RPC '{}' failed on endpoint #{} ({}), rotating: {}",
                        chainName, opName, idx, redact(endpoints.get(idx)), ex.getMessage());
                cursor.incrementAndGet();
                sleep(backoffFor(attempt));
            }
        }
        throw new RpcException(
                "[%s] RPC '%s' failed after %d attempts across %d endpoint(s)"
                        .formatted(chainName, opName, maxAttempts, clients.size()), last);
    }

    private long backoffFor(int attempt) {
        long backoff = baseBackoffMs * (1L << Math.min(attempt, 20));
        return Math.min(backoff, maxBackoffMs);
    }

    private static void sleep(long ms) {
        if (ms <= 0) {
            return;
        }
        try {
            Thread.sleep(ms);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RpcException("interrupted while backing off", e);
        }
    }

    /** Hide query strings / api keys that commonly live in RPC URLs. */
    private static String redact(String url) {
        int q = url.indexOf('?');
        String base = q >= 0 ? url.substring(0, q) : url;
        int lastSlash = base.lastIndexOf('/');
        if (lastSlash > 8 && lastSlash < base.length() - 1) {
            return base.substring(0, lastSlash + 1) + "***";
        }
        return base;
    }

    @Override
    public void close() {
        for (Web3j client : clients) {
            try {
                client.shutdown();
            } catch (RuntimeException ignored) {
                // best-effort shutdown
            }
        }
    }
}
