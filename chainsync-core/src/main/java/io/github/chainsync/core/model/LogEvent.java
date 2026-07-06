package io.github.chainsync.core.model;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * A single EVM log entry emitted by a transaction (e.g. an ERC-20 {@code Transfer}).
 *
 * @param address     contract address that produced the log
 * @param topics      indexed topics; {@code topics[0]} is the event signature hash
 * @param data        non-indexed event payload (hex)
 * @param blockNumber block that contains the emitting transaction
 * @param txHash      hash of the emitting transaction
 * @param logIndex    position of the log within the block
 * @param removed     {@code true} if the log was removed due to a chain reorganization
 */
public record LogEvent(
        String address,
        List<String> topics,
        String data,
        BigInteger blockNumber,
        String txHash,
        BigInteger logIndex,
        boolean removed
) {
    public LogEvent {
        Objects.requireNonNull(address, "address");
        topics = topics == null ? List.of() : List.copyOf(topics);
    }

    /** Convenience accessor for the event signature topic, or {@code null} for anonymous events. */
    public String signatureTopic() {
        return topics.isEmpty() ? null : topics.get(0);
    }
}
