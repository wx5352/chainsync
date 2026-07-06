package io.github.chainsync.spring;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chainsync.core.handler.BlockHandler;
import io.github.chainsync.store.kafka.KafkaBlockHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.kafka.core.KafkaTemplate;

/**
 * Activates the Kafka {@link BlockHandler} when {@code chainsync.store=kafka} and both spring-kafka
 * and the chainsync Kafka module are on the classpath. It reuses the application's existing
 * {@link KafkaTemplate} (auto-configured by Spring Boot from {@code spring.kafka.*}), so chainsync
 * only adds the topic prefix on top.
 *
 * <p>Configure a String value serializer so the JSON payload is written verbatim:
 * {@code spring.kafka.producer.value-serializer=org.apache.kafka.common.serialization.StringSerializer}.
 */
@AutoConfiguration
@ConditionalOnClass({KafkaTemplate.class, KafkaBlockHandler.class})
@ConditionalOnProperty(prefix = "chainsync", name = "store", havingValue = "kafka")
@EnableConfigurationProperties(ChainsyncProperties.class)
public class ChainsyncKafkaAutoConfiguration {

    private static final Logger log = LoggerFactory.getLogger(ChainsyncKafkaAutoConfiguration.class);

    @Bean
    @ConditionalOnBean(KafkaTemplate.class)
    @ConditionalOnMissingBean(BlockHandler.class)
    public BlockHandler chainsyncKafkaBlockHandler(ChainsyncProperties properties,
                                                   KafkaTemplate<String, String> kafkaTemplate,
                                                   ObjectProvider<ObjectMapper> objectMapper) {
        ObjectMapper mapper = objectMapper.getIfAvailable(ObjectMapper::new);
        ChainsyncProperties.Kafka cfg = properties.getKafka();
        log.info("chainsync: publishing blocks to Kafka (topic prefix '{}')", cfg.getTopicPrefix());
        return new KafkaBlockHandler(kafkaTemplate, mapper, cfg.getTopicPrefix(), cfg.getSendTimeoutMs());
    }
}
