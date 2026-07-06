package io.github.chainsync.store.jdbc;

import io.github.chainsync.core.handler.BlockHandler;
import io.github.chainsync.core.model.Block;
import io.github.chainsync.core.model.SyncedBlock;
import io.github.chainsync.core.model.Transaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;

/**
 * Default {@link BlockHandler} that persists blocks and their transactions into two JDBC tables
 * ({@code chainsync_block}, {@code chainsync_transaction}).
 *
 * <p>Writes are idempotent: each block is re-inserted within a single DB transaction after deleting
 * any prior rows for the same {@code (chain_id, block_number)}, so at-least-once redelivery and
 * reorg re-syncs never create duplicates.
 */
public final class JdbcBlockHandler implements BlockHandler {

    private static final Logger log = LoggerFactory.getLogger(JdbcBlockHandler.class);

    private final DataSource dataSource;
    private final String blockTable;
    private final String txTable;

    public JdbcBlockHandler(DataSource dataSource) {
        this(dataSource, "chainsync_block", "chainsync_transaction");
    }

    public JdbcBlockHandler(DataSource dataSource, String blockTable, String txTable) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.blockTable = Objects.requireNonNull(blockTable, "blockTable");
        this.txTable = Objects.requireNonNull(txTable, "txTable");
    }

    public void initSchema() {
        String blockDdl = """
                CREATE TABLE IF NOT EXISTS %s (
                    chain_id        BIGINT NOT NULL,
                    block_number    BIGINT NOT NULL,
                    block_hash      VARCHAR(80) NOT NULL,
                    parent_hash     VARCHAR(80),
                    block_timestamp BIGINT,
                    tx_count        INT,
                    PRIMARY KEY (chain_id, block_number)
                )
                """.formatted(blockTable);
        String txDdl = """
                CREATE TABLE IF NOT EXISTS %s (
                    chain_id     BIGINT NOT NULL,
                    block_number BIGINT NOT NULL,
                    tx_hash      VARCHAR(80) NOT NULL,
                    tx_index     INT,
                    from_addr    VARCHAR(64),
                    to_addr      VARCHAR(64),
                    value_wei    VARCHAR(80),
                    PRIMARY KEY (chain_id, tx_hash)
                )
                """.formatted(txTable);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(blockDdl);
            s.execute(txDdl);
            log.info("chainsync: block tables '{}' / '{}' ready", blockTable, txTable);
        } catch (SQLException e) {
            throw new StoreException("failed to initialize block tables", e);
        }
    }

    @Override
    public void onBlock(SyncedBlock synced) {
        Block block = synced.block();
        long chainId = synced.chainId();
        long number = block.number().longValueExact();

        try (Connection c = dataSource.getConnection()) {
            boolean prevAutoCommit = c.getAutoCommit();
            c.setAutoCommit(false);
            try {
                deleteBlock(c, chainId, number);
                insertBlock(c, chainId, block);
                insertTransactions(c, chainId, block);
                c.commit();
            } catch (SQLException e) {
                c.rollback();
                throw e;
            } finally {
                c.setAutoCommit(prevAutoCommit);
            }
        } catch (SQLException e) {
            throw new StoreException("failed to persist block " + number + " for chainId " + chainId, e);
        }
    }

    @Override
    public void onReorg(long chainId, BigInteger fromBlockExclusive, BigInteger toBlockInclusive) {
        String delBlocks = "DELETE FROM " + blockTable + " WHERE chain_id = ? AND block_number > ?";
        String delTxs = "DELETE FROM " + txTable + " WHERE chain_id = ? AND block_number > ?";
        long floor = fromBlockExclusive.longValueExact();
        try (Connection c = dataSource.getConnection()) {
            for (String sql : new String[]{delTxs, delBlocks}) {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setLong(1, chainId);
                    ps.setLong(2, floor);
                    ps.executeUpdate();
                }
            }
            log.warn("chainsync: purged reorged rows for chainId {} above block {}", chainId, floor);
        } catch (SQLException e) {
            throw new StoreException("failed to purge reorged blocks for chainId " + chainId, e);
        }
    }

    private void deleteBlock(Connection c, long chainId, long number) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM " + txTable + " WHERE chain_id = ? AND block_number = ?")) {
            ps.setLong(1, chainId);
            ps.setLong(2, number);
            ps.executeUpdate();
        }
        try (PreparedStatement ps = c.prepareStatement(
                "DELETE FROM " + blockTable + " WHERE chain_id = ? AND block_number = ?")) {
            ps.setLong(1, chainId);
            ps.setLong(2, number);
            ps.executeUpdate();
        }
    }

    private void insertBlock(Connection c, long chainId, Block block) throws SQLException {
        String sql = "INSERT INTO " + blockTable
                + " (chain_id, block_number, block_hash, parent_hash, block_timestamp, tx_count)"
                + " VALUES (?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chainId);
            ps.setLong(2, block.number().longValueExact());
            ps.setString(3, block.hash());
            ps.setString(4, block.parentHash());
            ps.setLong(5, block.timestamp() == null ? 0L : block.timestamp().longValueExact());
            ps.setInt(6, block.transactionCount());
            ps.executeUpdate();
        }
    }

    private void insertTransactions(Connection c, long chainId, Block block) throws SQLException {
        if (block.transactions().isEmpty()) {
            return;
        }
        String sql = "INSERT INTO " + txTable
                + " (chain_id, block_number, tx_hash, tx_index, from_addr, to_addr, value_wei)"
                + " VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            for (Transaction tx : block.transactions()) {
                ps.setLong(1, chainId);
                ps.setLong(2, block.number().longValueExact());
                ps.setString(3, tx.hash());
                ps.setInt(4, tx.transactionIndex() == null ? 0 : tx.transactionIndex().intValueExact());
                ps.setString(5, tx.from());
                ps.setString(6, tx.to());
                ps.setString(7, tx.value() == null ? null : tx.value().toString());
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }
}
