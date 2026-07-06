package io.github.chainsync.spring;

import io.github.chainsync.core.config.ChainConfig;
import io.github.chainsync.core.cursor.CursorStore;
import io.github.chainsync.core.cursor.InMemoryCursorStore;
import io.github.chainsync.core.handler.BlockHandler;
import io.github.chainsync.engine.ChainSynchronizer;
import io.github.chainsync.store.jdbc.JdbcBlockHandler;
import io.github.chainsync.store.jdbc.JdbcCursorStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.AutoConfigureAfter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.util.List;

/**
 * Auto-configuration that wires the entire pipeline from {@code chainsync.*} properties:
 * a {@link CursorStore}, a {@link BlockHandler}, and a {@link ChainSynchronizer} managed by the
 * Spring lifecycle. Every bean is {@code @ConditionalOnMissingBean}, so applications can override
 * any piece — most commonly by declaring their own {@link BlockHandler}.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ChainsyncProperties.class)
@ConditionalOnProperty(prefix = "chainsync", name = "enabled", havingValue = "true", matchIfMissing = true)
@AutoConfigureAfter(ChainsyncKafkaAutoConfiguration.class)
public class ChainsyncAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChainsyncAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean
    public CursorStore chainsyncCursorStore(ChainsyncProperties properties,
                                            org.springframework.beans.factory.ObjectProvider<DataSource> dataSource) {
        DataSource ds = dataSource.getIfAvailable();
        if ("jdbc".equalsIgnoreCase(properties.getCursorStore()) && ds != null) {
            JdbcCursorStore store = new JdbcCursorStore(ds);
            store.initSchema();
            log.info("chainsync: using JDBC cursor store");
            return store;
        }
        log.info("chainsync: using in-memory cursor store (progress is not persisted across restarts)");
        return new InMemoryCursorStore();
    }

    @Bean
    @ConditionalOnMissingBean
    public BlockHandler chainsyncBlockHandler(ChainsyncProperties properties,
                                              org.springframework.beans.factory.ObjectProvider<DataSource> dataSource) {
        DataSource ds = dataSource.getIfAvailable();
        if ("jdbc".equalsIgnoreCase(properties.getStore()) && ds != null) {
            JdbcBlockHandler handler = new JdbcBlockHandler(ds);
            handler.initSchema();
            log.info("chainsync: persisting blocks via JDBC store");
            return handler;
        }
        log.info("chainsync: no JDBC DataSource / custom handler found, logging blocks only");
        return new LoggingBlockHandler();
    }

    @Bean
    @ConditionalOnMissingBean
    public ChainSynchronizer chainSynchronizer(ChainsyncProperties properties,
                                               CursorStore cursorStore,
                                               BlockHandler blockHandler) {
        List<ChainConfig> chains = ChainConfigMapper.toChainConfigs(properties);
        return new ChainSynchronizer(chains, cursorStore, blockHandler);
    }

    @Bean
    @ConditionalOnMissingBean
    public ChainsyncLifecycle chainsyncLifecycle(ChainSynchronizer synchronizer) {
        return new ChainsyncLifecycle(synchronizer);
    }
}
