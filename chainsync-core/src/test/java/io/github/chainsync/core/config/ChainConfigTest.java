package io.github.chainsync.core.config;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainConfigTest {

    @Test
    void buildsWithDefaults() {
        ChainConfig c = ChainConfig.builder("ethereum")
                .chainId(1)
                .rpcUrls(List.of("https://eth.example"))
                .build();

        assertEquals("ethereum", c.name());
        assertEquals(1, c.chainId());
        assertEquals(12, c.confirmations());
        assertTrue(c.includeTransactions());
    }

    @Test
    void requiresAtLeastOneRpcUrl() {
        assertThrows(IllegalArgumentException.class, () ->
                ChainConfig.builder("bad").chainId(1).rpcUrls(List.of()).build());
    }
}
