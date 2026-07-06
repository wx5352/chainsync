package io.github.chainsync.core.model;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * A normalized EVM transaction together with the logs it produced.
 *
 * <p>Values common to ETH / BNB / Polygon are captured here; chain-specific fields are
 * intentionally omitted so a single model works across every EVM chain.
 *
 * @param hash             transaction hash
 * @param blockNumber      block that includes this transaction
 * @param transactionIndex position of the transaction within the block
 * @param from             sender address
 * @param to               recipient address, or {@code null} for contract-creation transactions
 * @param value            transferred amount in wei
 * @param input            call data (hex)
 * @param status           {@code true} if the receipt reported success, {@code false} if reverted,
 *                         {@code null} if the receipt was unavailable
 * @param logs             logs emitted by this transaction
 */
public record Transaction(
        String hash,
        BigInteger blockNumber,
        BigInteger transactionIndex,
        String from,
        String to,
        BigInteger value,
        String input,
        Boolean status,
        List<LogEvent> logs
) {
    public Transaction {
        Objects.requireNonNull(hash, "hash");
        logs = logs == null ? List.of() : List.copyOf(logs);
    }

    /** {@code true} when this transaction deploys a new contract (no recipient). */
    public boolean isContractCreation() {
        return to == null;
    }
}
