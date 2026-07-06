package io.github.chainsync.spring;

import io.github.chainsync.core.handler.BlockHandler;
import io.github.chainsync.core.model.SyncedBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Fallback {@link BlockHandler} used when neither a user-defined handler nor the JDBC store is
 * available. It simply logs each synced block, which is enough to verify the pipeline end-to-end.
 */
public class LoggingBlockHandler implements BlockHandler {

    private static final Logger log = LoggerFactory.getLogger(LoggingBlockHandler.class);

    @Override
    public void onBlock(SyncedBlock synced) {
        log.info("[{}] block #{} hash={} txs={}",
                synced.chain(),
                synced.block().number(),
                synced.block().hash(),
                synced.block().transactionCount());
    }
}
