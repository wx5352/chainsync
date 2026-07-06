package io.github.chainsync.core.model;

import java.math.BigInteger;
import java.util.List;
import java.util.Objects;

/**
 * A normalized EVM block. The {@link #parentHash()} link is what the engine uses to detect
 * chain reorganizations.
 *
 * @param number       block height
 * @param hash         block hash
 * @param parentHash   hash of the preceding block
 * @param timestamp    block timestamp in seconds since the Unix epoch
 * @param transactions transactions contained in this block (may be empty)
 */
public record Block(
        BigInteger number,
        String hash,
        String parentHash,
        BigInteger timestamp,
        List<Transaction> transactions
) {
    public Block {
        Objects.requireNonNull(number, "number");
        Objects.requireNonNull(hash, "hash");
        transactions = transactions == null ? List.of() : List.copyOf(transactions);
    }

    public int transactionCount() {
        return transactions.size();
    }
}
