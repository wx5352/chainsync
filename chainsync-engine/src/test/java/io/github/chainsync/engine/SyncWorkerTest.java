package io.github.chainsync.engine;

import io.github.chainsync.core.config.ChainConfig;
import io.github.chainsync.core.config.StartFrom;
import io.github.chainsync.core.cursor.InMemoryCursorStore;
import io.github.chainsync.core.cursor.SyncCursor;
import io.github.chainsync.core.model.SyncedBlock;
import org.junit.jupiter.api.Test;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SyncWorkerTest {

    private ChainConfig config(StartFrom startFrom, int confirmations) {
        return ChainConfig.builder("test")
                .chainId(1)
                .rpcUrls(List.of("http://localhost"))
                .startFrom(startFrom)
                .confirmations(confirmations)
                .batchSize(5)
                .concurrency(2)
                .pollIntervalMs(1)
                .build();
    }

    @Test
    void syncsConfirmedBlocksInOrder() {
        FakeChain chain = new FakeChain();
        chain.append(10, "a"); // blocks 0..9, head = 9

        List<BigInteger> delivered = new ArrayList<>();
        InMemoryCursorStore cursor = new InMemoryCursorStore();
        SyncWorker worker = new SyncWorker(config(StartFrom.earliest(), 2), chain, cursor,
                b -> delivered.add(b.block().number()));

        worker.initStartPosition();
        for (int i = 0; i < 5; i++) {
            worker.syncOnce();
        }

        // confirmedHead = head(9) - confirmations(2) = 7 -> blocks 0..7 delivered in order
        assertEquals(rangeInclusive(0, 7), delivered);
        assertEquals(BigInteger.valueOf(7), cursor.load(1).orElseThrow().blockNumber());
    }

    @Test
    void resumesFromPersistedCursor() {
        FakeChain chain = new FakeChain();
        chain.append(10, "a");

        List<BigInteger> delivered = new ArrayList<>();
        InMemoryCursorStore cursor = new InMemoryCursorStore();
        cursor.save(new SyncCursor(1, BigInteger.valueOf(4), "0xa-4"));

        SyncWorker worker = new SyncWorker(config(StartFrom.earliest(), 0), chain, cursor,
                b -> delivered.add(b.block().number()));
        worker.initStartPosition();
        worker.syncOnce();
        worker.syncOnce();

        // resumes after block 4 -> next delivered block is 5
        assertEquals(BigInteger.valueOf(5), delivered.get(0));
        assertTrue(delivered.stream().noneMatch(n -> n.intValue() <= 4));
    }

    @Test
    void recoversFromReorg() {
        FakeChain chain = new FakeChain();
        chain.append(20, "a"); // 0..19

        List<SyncedBlock> delivered = new ArrayList<>();
        List<long[]> reorgs = new ArrayList<>();
        InMemoryCursorStore cursor = new InMemoryCursorStore();

        SyncWorker worker = new SyncWorker(config(StartFrom.earliest(), 0), chain, cursor,
                new io.github.chainsync.core.handler.BlockHandler() {
                    @Override
                    public void onBlock(SyncedBlock b) {
                        delivered.add(b);
                    }

                    @Override
                    public void onReorg(long chainId, BigInteger from, BigInteger to) {
                        reorgs.add(new long[]{from.longValue(), to.longValue()});
                    }
                });

        worker.initStartPosition();
        for (int i = 0; i < 6; i++) {
            worker.syncOnce();
        }
        BigInteger tipBefore = worker.lastProcessed();
        assertEquals(BigInteger.valueOf(19), tipBefore);

        // Rewrite blocks above 14 with a different tag -> parentHash chain diverges.
        chain.reorgFrom(14, 8, "b"); // now 0..14 tagged a, 15..22 tagged b, head = 22

        for (int i = 0; i < 8; i++) {
            worker.syncOnce();
        }

        assertTrue(reorgs.size() >= 1, "a reorg should have been reported");
        // After recovery the tip must sit on the new canonical chain (tag b) at the new head.
        assertEquals(BigInteger.valueOf(22), worker.lastProcessed());
        SyncedBlock tip = delivered.get(delivered.size() - 1);
        assertTrue(tip.block().hash().startsWith("0xb-"), "tip should be on the reorged (b) chain");
    }

    private static List<BigInteger> rangeInclusive(int from, int to) {
        List<BigInteger> out = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            out.add(BigInteger.valueOf(i));
        }
        return out;
    }
}
