package io.github.chainsync.core.decode;

import java.math.BigInteger;

/**
 * A decoded token transfer, produced from a raw {@link io.github.chainsync.core.model.LogEvent} by
 * {@link TransferDecoder}. Covers the two most common cases: fungible ERC-20 transfers and
 * non-fungible ERC-721 transfers (both emit the same {@code Transfer} signature, distinguished by
 * how the arguments are indexed).
 *
 * @param type        {@link TokenType#ERC20} or {@link TokenType#ERC721}
 * @param token       the token contract address that emitted the event
 * @param from        sender address
 * @param to          recipient address
 * @param value       transferred amount for ERC-20; {@code null} for ERC-721
 * @param tokenId     transferred token id for ERC-721; {@code null} for ERC-20
 * @param txHash      hash of the transaction that emitted the event
 * @param blockNumber block containing the event
 * @param logIndex    position of the source log within the block
 */
public record TokenTransfer(
        TokenType type,
        String token,
        String from,
        String to,
        BigInteger value,
        BigInteger tokenId,
        String txHash,
        BigInteger blockNumber,
        BigInteger logIndex
) {
    public enum TokenType { ERC20, ERC721 }

    public boolean isFungible() {
        return type == TokenType.ERC20;
    }
}
