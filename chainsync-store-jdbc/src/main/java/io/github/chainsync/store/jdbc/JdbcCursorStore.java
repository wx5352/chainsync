package io.github.chainsync.store.jdbc;

import io.github.chainsync.core.cursor.CursorStore;
import io.github.chainsync.core.cursor.SyncCursor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.math.BigInteger;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Objects;
import java.util.Optional;

/**
 * Durable {@link CursorStore} backed by any JDBC {@link DataSource}. Uses portable SQL
 * (update-then-insert instead of vendor-specific upserts) so it runs on H2, PostgreSQL, MySQL, etc.
 */
public final class JdbcCursorStore implements CursorStore {

    private static final Logger log = LoggerFactory.getLogger(JdbcCursorStore.class);

    private final DataSource dataSource;
    private final String table;

    public JdbcCursorStore(DataSource dataSource) {
        this(dataSource, "chainsync_cursor");
    }

    public JdbcCursorStore(DataSource dataSource, String table) {
        this.dataSource = Objects.requireNonNull(dataSource, "dataSource");
        this.table = Objects.requireNonNull(table, "table");
    }

    /** Create the cursor table if it does not already exist. Safe to call repeatedly. */
    public void initSchema() {
        String ddl = """
                CREATE TABLE IF NOT EXISTS %s (
                    chain_id     BIGINT PRIMARY KEY,
                    block_number BIGINT NOT NULL,
                    block_hash   VARCHAR(80),
                    updated_at   TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """.formatted(table);
        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.execute(ddl);
            log.info("chainsync: cursor table '{}' ready", table);
        } catch (SQLException e) {
            throw new StoreException("failed to initialize cursor table " + table, e);
        }
    }

    @Override
    public Optional<SyncCursor> load(long chainId) {
        String sql = "SELECT block_number, block_hash FROM " + table + " WHERE chain_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, chainId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    BigInteger number = BigInteger.valueOf(rs.getLong("block_number"));
                    String hash = rs.getString("block_hash");
                    return Optional.of(new SyncCursor(chainId, number, hash));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new StoreException("failed to load cursor for chainId " + chainId, e);
        }
    }

    @Override
    public void save(SyncCursor cursor) {
        String update = "UPDATE " + table
                + " SET block_number = ?, block_hash = ?, updated_at = CURRENT_TIMESTAMP WHERE chain_id = ?";
        String insert = "INSERT INTO " + table
                + " (chain_id, block_number, block_hash) VALUES (?, ?, ?)";
        long number = cursor.blockNumber().longValueExact();
        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement ps = c.prepareStatement(update)) {
                ps.setLong(1, number);
                ps.setString(2, cursor.blockHash());
                ps.setLong(3, cursor.chainId());
                if (ps.executeUpdate() > 0) {
                    return;
                }
            }
            try (PreparedStatement ps = c.prepareStatement(insert)) {
                ps.setLong(1, cursor.chainId());
                ps.setLong(2, number);
                ps.setString(3, cursor.blockHash());
                ps.executeUpdate();
            }
        } catch (SQLException e) {
            throw new StoreException("failed to save cursor for chainId " + cursor.chainId(), e);
        }
    }

    @Override
    public void rewind(long chainId, BigInteger blockNumber) {
        String sql = "UPDATE " + table
                + " SET block_number = ?, block_hash = NULL, updated_at = CURRENT_TIMESTAMP WHERE chain_id = ?";
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setLong(1, blockNumber.longValueExact());
            ps.setLong(2, chainId);
            ps.executeUpdate();
        } catch (SQLException e) {
            throw new StoreException("failed to rewind cursor for chainId " + chainId, e);
        }
    }
}
