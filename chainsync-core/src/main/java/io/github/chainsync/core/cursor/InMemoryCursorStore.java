package io.github.chainsync.core.cursor;

import java.math.BigInteger;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Non-durable {@link CursorStore} kept in a map. Useful for tests, samples and "start from latest,
 * don't care about restarts" scenarios. Progress is lost when the JVM exits — use a persistent
 * store (e.g. the JDBC one) in production.
 */
public final class InMemoryCursorStore implements CursorStore {

    private final ConcurrentHashMap<Long, SyncCursor> cursors = new ConcurrentHashMap<>();

    @Override
    public Optional<SyncCursor> load(long chainId) {
        return Optional.ofNullable(cursors.get(chainId));
    }

    @Override
    public void save(SyncCursor cursor) {
        cursors.put(cursor.chainId(), cursor);
    }

    @Override
    public void rewind(long chainId, BigInteger blockNumber) {
        cursors.computeIfPresent(chainId,
                (id, cur) -> new SyncCursor(id, blockNumber, null));
    }
}
