package io.github.chainsync.core.decode;

import io.github.chainsync.core.model.LogEvent;
import io.github.chainsync.core.model.SyncedBlock;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

/**
 * Decodes ERC-20 and ERC-721 {@code Transfer} events from raw {@link LogEvent}s — no ABI, no web3j,
 * pure parsing. Both standards share the signature
 * {@code Transfer(address from, address to, uint256 valueOrTokenId)}; they are told apart by topic
 * count:
 *
 * <ul>
 *   <li><b>ERC-20</b>: 3 topics {@code [sig, from, to]}, the amount lives in {@code data}.</li>
 *   <li><b>ERC-721</b>: 4 topics {@code [sig, from, to, tokenId]} (tokenId is indexed), empty data.</li>
 * </ul>
 */
public final class TransferDecoder {

    /** keccak256("Transfer(address,address,uint256)"). */
    public static final String TRANSFER_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private TransferDecoder() {
    }

    /** Decode a single log into a {@link TokenTransfer}, or empty if it is not a Transfer event. */
    public static Optional<TokenTransfer> decode(LogEvent log) {
        if (log == null || log.topics().isEmpty()) {
            return Optional.empty();
        }
        String sig = log.topics().get(0);
        if (sig == null || !sig.equalsIgnoreCase(TRANSFER_TOPIC)) {
            return Optional.empty();
        }

        List<String> topics = log.topics();
        if (topics.size() == 3) {
            String from = topicToAddress(topics.get(1));
            String to = topicToAddress(topics.get(2));
            BigInteger value = hexToBigInteger(log.data());
            return Optional.of(new TokenTransfer(
                    TokenTransfer.TokenType.ERC20, log.address(), from, to,
                    value, null, log.txHash(), log.blockNumber(), log.logIndex()));
        }
        if (topics.size() == 4) {
            String from = topicToAddress(topics.get(1));
            String to = topicToAddress(topics.get(2));
            BigInteger tokenId = hexToBigInteger(topics.get(3));
            return Optional.of(new TokenTransfer(
                    TokenTransfer.TokenType.ERC721, log.address(), from, to,
                    null, tokenId, log.txHash(), log.blockNumber(), log.logIndex()));
        }
        return Optional.empty();
    }

    /** Decode every Transfer event contained in a synced block. */
    public static List<TokenTransfer> transfersIn(SyncedBlock block) {
        return block.allLogs().stream()
                .map(TransferDecoder::decode)
                .flatMap(Optional::stream)
                .toList();
    }

    /** A 32-byte topic holds a left-padded address in its low 20 bytes. */
    static String topicToAddress(String topic) {
        if (topic == null) {
            return null;
        }
        String hex = strip0x(topic);
        if (hex.length() < 40) {
            return "0x" + hex;
        }
        return "0x" + hex.substring(hex.length() - 40).toLowerCase();
    }

    static BigInteger hexToBigInteger(String hex) {
        String s = strip0x(hex);
        if (s.isEmpty()) {
            return BigInteger.ZERO;
        }
        return new BigInteger(s, 16);
    }

    private static String strip0x(String s) {
        if (s == null) {
            return "";
        }
        String t = s.trim();
        if (t.startsWith("0x") || t.startsWith("0X")) {
            t = t.substring(2);
        }
        return t;
    }
}
