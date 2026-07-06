package io.github.chainsync.store.kafka;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.chainsync.core.model.Block;
import io.github.chainsync.core.model.SyncedBlock;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import java.math.BigInteger;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KafkaBlockHandlerTest {

    /**
     * A {@link KafkaTemplate} subclass that records the last send instead of talking to a broker.
     * Avoids mocking the (heavily generic, final-method-laden) template so the test is JVM-agnostic.
     */
    static final class RecordingKafkaTemplate extends KafkaTemplate<String, String> {
        String topic;
        String key;
        String value;

        RecordingKafkaTemplate() {
            super(new DefaultKafkaProducerFactory<>(Map.of(
                    ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, "localhost:9092",
                    ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                    ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class)));
        }

        @Override
        public CompletableFuture<SendResult<String, String>> send(String topic, String key, String data) {
            this.topic = topic;
            this.key = key;
            this.value = data;
            return CompletableFuture.completedFuture(null);
        }
    }

    @Test
    void publishesBlockAsJsonToPerChainTopic() throws Exception {
        RecordingKafkaTemplate template = new RecordingKafkaTemplate();
        KafkaBlockHandler handler = new KafkaBlockHandler(
                template, new ObjectMapper(), "chainsync", 5_000L);

        Block block = new Block(BigInteger.valueOf(42), "0xabc", "0xparent",
                BigInteger.valueOf(1234), List.of());
        handler.onBlock(new SyncedBlock(1, "ethereum", block));

        assertEquals("chainsync.ethereum.blocks", template.topic);
        assertEquals("42", template.key);
        assertTrue(template.value.contains("\"chain\":\"ethereum\""));
        assertTrue(template.value.contains("\"number\":42"));
    }

    @Test
    void buildsTopicName() {
        KafkaBlockHandler handler = new KafkaBlockHandler(
                new RecordingKafkaTemplate(), new ObjectMapper(), "cs", 1_000L);
        assertEquals("cs.polygon.blocks", handler.topicName("polygon"));
    }
}
