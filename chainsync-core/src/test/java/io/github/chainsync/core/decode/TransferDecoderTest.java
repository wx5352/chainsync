package io.github.chainsync.core.decode;

import io.github.chainsync.core.model.LogEvent;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TransferDecoderTest {

    private static final String FROM_TOPIC = "0x000000000000000000000000aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa";
    private static final String TO_TOPIC = "0x000000000000000000000000bbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb";

    @Test
    void decodesErc20Transfer() {
        // value = 0x0de0b6b3a7640000 = 1e18
        LogEvent log = new LogEvent(
                "0xtoken",
                List.of(TransferDecoder.TRANSFER_TOPIC, FROM_TOPIC, TO_TOPIC),
                "0x0000000000000000000000000000000000000000000000000de0b6b3a7640000",
                BigInteger.valueOf(100), "0xtx", BigInteger.ZERO, false);

        TokenTransfer t = TransferDecoder.decode(log).orElseThrow();
        assertEquals(TokenTransfer.TokenType.ERC20, t.type());
        assertEquals("0xaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa", t.from());
        assertEquals("0xbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbbb", t.to());
        assertEquals(new BigInteger("1000000000000000000"), t.value());
        assertNull(t.tokenId());
        assertTrue(t.isFungible());
    }

    @Test
    void decodesErc721Transfer() {
        // tokenId indexed as a 4th topic => ERC-721
        String tokenIdTopic = "0x0000000000000000000000000000000000000000000000000000000000000539"; // 1337
        LogEvent log = new LogEvent(
                "0xnft",
                List.of(TransferDecoder.TRANSFER_TOPIC, FROM_TOPIC, TO_TOPIC, tokenIdTopic),
                "0x",
                BigInteger.valueOf(200), "0xtx2", BigInteger.ONE, false);

        TokenTransfer t = TransferDecoder.decode(log).orElseThrow();
        assertEquals(TokenTransfer.TokenType.ERC721, t.type());
        assertEquals(BigInteger.valueOf(1337), t.tokenId());
        assertNull(t.value());
    }

    @Test
    void ignoresNonTransferLogs() {
        LogEvent log = new LogEvent(
                "0xtoken",
                List.of("0xdeadbeef"),
                "0x",
                BigInteger.ONE, "0xtx", BigInteger.ZERO, false);
        assertEquals(Optional.empty(), TransferDecoder.decode(log));
    }
}
