package io.github.chainsync.core.config;

import org.junit.jupiter.api.Test;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StartFromTest {

    @Test
    void parsesLatest() {
        assertEquals(StartFrom.Mode.LATEST, StartFrom.parse("latest").mode());
        assertEquals(StartFrom.Mode.LATEST, StartFrom.parse(null).mode());
        assertEquals(StartFrom.Mode.LATEST, StartFrom.parse("  ").mode());
    }

    @Test
    void parsesEarliest() {
        assertEquals(StartFrom.Mode.EARLIEST, StartFrom.parse("earliest").mode());
        assertEquals(StartFrom.Mode.EARLIEST, StartFrom.parse("genesis").mode());
    }

    @Test
    void parsesExplicitBlock() {
        StartFrom sf = StartFrom.parse("18000000");
        assertEquals(StartFrom.Mode.BLOCK, sf.mode());
        assertEquals(BigInteger.valueOf(18_000_000), sf.block());
    }
}
