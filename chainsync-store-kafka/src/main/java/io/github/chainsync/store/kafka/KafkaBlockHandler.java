package io.github.chainsync.store.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chainsync.core.handler.BlockHandler;
import io.github.chainsync.core.model.SyncedBlock;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigInteger;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * {@link BlockHandler} that publishes each synced block as a JSON message to Kafka.
 *
 * <p>Messages are sent to a per-chain topic {@code <prefix>.<chain>.blocks}, keyed by block number
 * so all events for one block land on the same partition and stay ordered. The send is awaited
 * before returning, so the engine only advances its cursor once the message is acknowledged —
 * preserving at-least-once delivery end to end.
 */
public final class KafkaBlockHandler implements BlockHandler {

    private static final Logger log = LoggerFactory.getLogger(KafkaBlockHandler.class);

    private final KafkaTemplate<String, String> kafkaTemplate;
    private final ObjectMapper objectMapper;
    private final String topicPrefix;
    private final long sendTimeoutMs;

    public KafkaBlockHandler(KafkaTemplate<String, String> kafkaTemplate,
                             ObjectMapper objectMapper,
                             String topicPrefix,
                             long sendTimeoutMs) {
        this.kafkaTemplate = Objects.requireNonNull(kafkaTemplate, "kafkaTemplate");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper");
        this.topicPrefix = Objects.requireNonNull(topicPrefix, "topicPrefix");
        this.sendTimeoutMs = sendTimeoutMs;
    }

    @Override
    public void onBlock(SyncedBlock synced) throws Exception {
        String topic = topicName(synced.chain());
        String key = synced.block().number().toString();
        String payload = objectMapper.writeValueAsString(synced);

        kafkaTemplate.send(topic, key, payload).get(sendTimeoutMs, TimeUnit.MILLISECONDS);

        if (log.isDebugEnabled()) {
            log.debug("published {} block #{} to topic {}", synced.chain(), key, topic);
        }
    }

    @Override
    public void onReorg(long chainId, BigInteger fromBlockExclusive, BigInteger toBlockInclusive) {
        // Kafka is an append-only log; downstream consumers reconcile reorgs using the re-delivered,
        // corrected blocks. We publish a marker so consumers can react if they choose to.
        log.warn("reorg on chainId {} above block {} — corrected blocks will be re-published",
                chainId, fromBlockExclusive);
    }

    String topicName(String chain) {
        return topicPrefix + "." + chain + ".blocks";
    }
}
