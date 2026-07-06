package io.github.chainsync.spring;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.ArrayList;
import java.util.List;

/**
 * Configuration bound from the {@code chainsync.*} namespace.
 *
 * <pre>
 * chainsync:
 *   enabled: true
 *   store: jdbc            # jdbc | memory
 *   chains:
 *     - name: ethereum
 *       chain-id: 1
 *       rpc-urls: [https://eth.llamarpc.com]
 *       start-block: latest
 *       confirmations: 12
 * </pre>
 */
@ConfigurationProperties(prefix = "chainsync")
public class ChainsyncProperties {

    /** Master switch; set to false to disable all synchronization. */
    private boolean enabled = true;

    /** Where synced data goes when no custom BlockHandler bean is provided: {@code jdbc} or {@code memory}. */
    private String store = "jdbc";

    /** Persist cursors durably (jdbc) or keep them in memory. Defaults to the value of {@link #store}. */
    private String cursorStore;

    private List<Chain> chains = new ArrayList<>();

    private final Kafka kafka = new Kafka();

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getStore() {
        return store;
    }

    public void setStore(String store) {
        this.store = store;
    }

    public String getCursorStore() {
        return cursorStore != null ? cursorStore : store;
    }

    public void setCursorStore(String cursorStore) {
        this.cursorStore = cursorStore;
    }

    public List<Chain> getChains() {
        return chains;
    }

    public void setChains(List<Chain> chains) {
        this.chains = chains;
    }

    public Kafka getKafka() {
        return kafka;
    }

    /** Settings used when {@code chainsync.store=kafka}. */
    public static class Kafka {
        /** Topic name prefix; the effective topic is {@code <prefix>.<chain>.blocks}. */
        private String topicPrefix = "chainsync";
        /** How long to wait for a send acknowledgement before failing the block. */
        private long sendTimeoutMs = 10_000L;

        public String getTopicPrefix() { return topicPrefix; }
        public void setTopicPrefix(String topicPrefix) { this.topicPrefix = topicPrefix; }

        public long getSendTimeoutMs() { return sendTimeoutMs; }
        public void setSendTimeoutMs(long sendTimeoutMs) { this.sendTimeoutMs = sendTimeoutMs; }
    }

    /** Per-chain configuration. Field names map to {@code chainsync.chains[].*}. */
    public static class Chain {
        private String name;
        private long chainId;
        private List<String> rpcUrls = new ArrayList<>();
        private String startBlock = "latest";
        private int confirmations = 12;
        private long pollIntervalMs = 3_000L;
        private int batchSize = 20;
        private int concurrency = 8;
        private boolean includeTransactions = true;
        private boolean includeLogs = false;

        public String getName() { return name; }
        public void setName(String name) { this.name = name; }

        public long getChainId() { return chainId; }
        public void setChainId(long chainId) { this.chainId = chainId; }

        public List<String> getRpcUrls() { return rpcUrls; }
        public void setRpcUrls(List<String> rpcUrls) { this.rpcUrls = rpcUrls; }

        public String getStartBlock() { return startBlock; }
        public void setStartBlock(String startBlock) { this.startBlock = startBlock; }

        public int getConfirmations() { return confirmations; }
        public void setConfirmations(int confirmations) { this.confirmations = confirmations; }

        public long getPollIntervalMs() { return pollIntervalMs; }
        public void setPollIntervalMs(long pollIntervalMs) { this.pollIntervalMs = pollIntervalMs; }

        public int getBatchSize() { return batchSize; }
        public void setBatchSize(int batchSize) { this.batchSize = batchSize; }

        public int getConcurrency() { return concurrency; }
        public void setConcurrency(int concurrency) { this.concurrency = concurrency; }

        public boolean isIncludeTransactions() { return includeTransactions; }
        public void setIncludeTransactions(boolean includeTransactions) { this.includeTransactions = includeTransactions; }

        public boolean isIncludeLogs() { return includeLogs; }
        public void setIncludeLogs(boolean includeLogs) { this.includeLogs = includeLogs; }
    }
}
