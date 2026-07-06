package io.github.chainsync.engine.rpc;

import io.github.chainsync.core.model.Block;
import io.github.chainsync.core.model.LogEvent;
import io.github.chainsync.core.model.Transaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Log;

import java.math.BigInteger;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/** Converts web3j response types into chainsync's framework-agnostic domain model. */
public final class Web3jMapper {

    private Web3jMapper() {
    }

    /**
     * Map a web3j block into a core {@link Block}.
     *
     * @param web3Block    the raw block (expected to contain full transaction objects)
     * @param logsByTxHash logs for the block grouped by transaction hash; may be empty
     */
    public static Block toBlock(EthBlock.Block web3Block, Map<String, List<LogEvent>> logsByTxHash) {
        List<Transaction> txs = new ArrayList<>();
        for (EthBlock.TransactionResult<?> result : web3Block.getTransactions()) {
            if (result.get() instanceof EthBlock.TransactionObject txo) {
                List<LogEvent> txLogs = logsByTxHash.getOrDefault(txo.getHash(), List.of());
                txs.add(toTransaction(txo, txLogs));
            }
        }
        return new Block(
                web3Block.getNumber(),
                web3Block.getHash(),
                web3Block.getParentHash(),
                web3Block.getTimestamp(),
                txs
        );
    }

    public static Transaction toTransaction(EthBlock.TransactionObject txo, List<LogEvent> logs) {
        return new Transaction(
                txo.getHash(),
                txo.getBlockNumber(),
                txo.getTransactionIndex(),
                txo.getFrom(),
                txo.getTo(),
                txo.getValue(),
                txo.getInput(),
                null,
                logs
        );
    }

    public static LogEvent toLogEvent(Log log) {
        BigInteger logIndex = log.getLogIndex();
        return new LogEvent(
                log.getAddress(),
                log.getTopics(),
                log.getData(),
                log.getBlockNumber(),
                log.getTransactionHash(),
                logIndex,
                log.isRemoved()
        );
    }
}
