package io.github.chainsync.spring;

import io.github.chainsync.core.config.ChainConfig;
import io.github.chainsync.core.config.StartFrom;

import java.util.List;

/** Translates the Spring-bound {@link ChainsyncProperties.Chain} into a core {@link ChainConfig}. */
final class ChainConfigMapper {

    private ChainConfigMapper() {
    }

    static List<ChainConfig> toChainConfigs(ChainsyncProperties properties) {
        return properties.getChains().stream()
                .map(ChainConfigMapper::toChainConfig)
                .toList();
    }

    static ChainConfig toChainConfig(ChainsyncProperties.Chain c) {
        return ChainConfig.builder(c.getName())
                .chainId(c.getChainId())
                .rpcUrls(c.getRpcUrls())
                .startFrom(StartFrom.parse(c.getStartBlock()))
                .confirmations(c.getConfirmations())
                .pollIntervalMs(c.getPollIntervalMs())
                .batchSize(c.getBatchSize())
                .concurrency(c.getConcurrency())
                .includeTransactions(c.isIncludeTransactions())
                .includeLogs(c.isIncludeLogs())
                .build();
    }
}
