package io.github.chainsync.store.jdbc;

import io.github.chainsync.core.cursor.SyncCursor;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class JdbcCursorStoreTest {

    private DataSource dataSource;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:cursor-" + System.nanoTime() + ";DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        this.dataSource = ds;
    }

    @Test
    void savesAndLoads() {
        JdbcCursorStore store = new JdbcCursorStore(dataSource);
        store.initSchema();

        assertTrue(store.load(1).isEmpty());

        store.save(new SyncCursor(1, BigInteger.valueOf(100), "0xabc"));
        Optional<SyncCursor> loaded = store.load(1);
        assertTrue(loaded.isPresent());
        assertEquals(BigInteger.valueOf(100), loaded.get().blockNumber());
        assertEquals("0xabc", loaded.get().blockHash());

        // upsert path
        store.save(new SyncCursor(1, BigInteger.valueOf(200), "0xdef"));
        assertEquals(BigInteger.valueOf(200), store.load(1).orElseThrow().blockNumber());
    }

    @Test
    void rewindClearsHash() {
        JdbcCursorStore store = new JdbcCursorStore(dataSource);
        store.initSchema();
        store.save(new SyncCursor(1, BigInteger.valueOf(100), "0xabc"));

        store.rewind(1, BigInteger.valueOf(50));
        SyncCursor c = store.load(1).orElseThrow();
        assertEquals(BigInteger.valueOf(50), c.blockNumber());
        assertFalse(c.blockHash() != null && !c.blockHash().isEmpty());
    }
}
