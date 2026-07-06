package io.github.chainsync.samples;

import io.github.chainsync.core.decode.TokenTransfer;
import io.github.chainsync.core.decode.TransferDecoder;
import io.github.chainsync.core.handler.BlockHandler;
import io.github.chainsync.core.model.SyncedBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * The one piece of application code a user writes: "when a block arrives, do my thing".
 *
 * <p>Here we decode every ERC-20 / ERC-721 {@code Transfer} in the block using chainsync's built-in
 * {@link TransferDecoder} and log a summary. Everything else — pulling blocks, staying at the
 * confirmed head, reorg recovery, resume, RPC failover — is handled by chainsync. Declaring this
 * bean makes chainsync route blocks here instead of the default store.
 */
@Component
public class TransferWatchHandler implements BlockHandler {

    private static final Logger log = LoggerFactory.getLogger(TransferWatchHandler.class);

    @Override
    public void onBlock(SyncedBlock synced) {
        List<TokenTransfer> transfers = TransferDecoder.transfersIn(synced);
        Map<TokenTransfer.TokenType, Long> byType = transfers.stream()
                .collect(Collectors.groupingBy(TokenTransfer::type, Collectors.counting()));

        log.info("[{}] block #{} | txs={} | ERC20={} | ERC721={}",
                synced.chain(),
                synced.block().number(),
                synced.block().transactionCount(),
                byType.getOrDefault(TokenTransfer.TokenType.ERC20, 0L),
                byType.getOrDefault(TokenTransfer.TokenType.ERC721, 0L));
    }
}
