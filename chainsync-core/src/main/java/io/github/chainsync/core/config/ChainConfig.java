package io.github.chainsync.core.config;

import java.util.List;
import java.util.Objects;

/**
 * Configuration for a single EVM chain to synchronize. The same shape works for ETH, BNB and
 * Polygon — only the {@code chainId} and {@code rpcUrls} differ.
 *
 * <p>Instances are immutable; use the {@link Builder} to construct them.
 */
public final class ChainConfig {

    private final String name;
    private final long chainId;
    private final List<String> rpcUrls;
    private final StartFrom startFrom;
    private final int confirmations;
    private final long pollIntervalMs;
    private final int batchSize;
    private final int concurrency;
    private final boolean includeTransactions;
    private final boolean includeLogs;

    private ChainConfig(Builder b) {
        this.name = Objects.requireNonNull(b.name, "name");
        this.chainId = b.chainId;
        if (b.rpcUrls == null || b.rpcUrls.isEmpty()) {
            throw new IllegalArgumentException("at least one rpcUrl is required for chain " + name);
        }
        this.rpcUrls = List.copyOf(b.rpcUrls);
        this.startFrom = b.startFrom == null ? StartFrom.latest() : b.startFrom;
        this.confirmations = b.confirmations;
        this.pollIntervalMs = b.pollIntervalMs;
        this.batchSize = b.batchSize;
        this.concurrency = b.concurrency;
        this.includeTransactions = b.includeTransactions;
        this.includeLogs = b.includeLogs;
    }

    public String name() { return name; }
    public long chainId() { return chainId; }
    public List<String> rpcUrls() { return rpcUrls; }
    public StartFrom startFrom() { return startFrom; }

    /** Blocks to stay behind the head to avoid ingesting soon-to-be-reorged blocks. */
    public int confirmations() { return confirmations; }

    /** Milliseconds to sleep once caught up to the confirmed head. */
    public long pollIntervalMs() { return pollIntervalMs; }

    /** Number of blocks fetched per catch-up batch. */
    public int batchSize() { return batchSize; }

    /** Parallelism used when fetching a batch of blocks. */
    public int concurrency() { return concurrency; }

    public boolean includeTransactions() { return includeTransactions; }
    public boolean includeLogs() { return includeLogs; }

    public static Builder builder(String name) {
        return new Builder(name);
    }

    public static final class Builder {
        private final String name;
        private long chainId;
        private List<String> rpcUrls;
        private StartFrom startFrom = StartFrom.latest();
        private int confirmations = 12;
        private long pollIntervalMs = 3_000L;
        private int batchSize = 20;
        private int concurrency = 8;
        private boolean includeTransactions = true;
        private boolean includeLogs = false;

        private Builder(String name) {
            this.name = name;
        }

        public Builder chainId(long chainId) { this.chainId = chainId; return this; }
        public Builder rpcUrls(List<String> rpcUrls) { this.rpcUrls = rpcUrls; return this; }
        public Builder startFrom(StartFrom startFrom) { this.startFrom = startFrom; return this; }
        public Builder confirmations(int confirmations) { this.confirmations = confirmations; return this; }
        public Builder pollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; return this; }
        public Builder batchSize(int batchSize) { this.batchSize = batchSize; return this; }
        public Builder concurrency(int concurrency) { this.concurrency = concurrency; return this; }
        public Builder includeTransactions(boolean v) { this.includeTransactions = v; return this; }
        public Builder includeLogs(boolean v) { this.includeLogs = v; return this; }

        public ChainConfig build() {
            return new ChainConfig(this);
        }
    }

    @Override
    public String toString() {
        return "ChainConfig{name=%s, chainId=%d, rpcUrls=%d, confirmations=%d, batchSize=%d}"
                .formatted(name, chainId, rpcUrls.size(), confirmations, batchSize);
    }
}
