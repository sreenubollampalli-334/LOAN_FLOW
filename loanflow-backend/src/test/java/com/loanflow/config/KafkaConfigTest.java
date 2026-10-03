package com.loanflow.config;

import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;
import org.springframework.kafka.support.serializer.ErrorHandlingDeserializer;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.kafka.support.serializer.JsonSerializer;
import org.springframework.test.context.ActiveProfiles;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(classes = KafkaConfig.class)
@ActiveProfiles("test")
public class KafkaConfigTest {

    @Autowired
    private ProducerFactory<String, Object> producerFactory;

    @Autowired
    private ConsumerFactory<String, Object> consumerFactory;

    @Autowired
    private KafkaTemplate<String, Object> kafkaTemplate;

    @Test
    void testKafkaConfigBeansLoaded() {
        assertNotNull(producerFactory, "ProducerFactory should be loaded");
        assertNotNull(consumerFactory, "ConsumerFactory should be loaded");
        assertNotNull(kafkaTemplate, "KafkaTemplate should be loaded");
    }

    @Test
    void testProducerFactoryConfiguration() {
        Map<String, Object> config = producerFactory.getConfigurationProperties();
        assertEquals(StringSerializer.class, config.get(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG));
        assertEquals(JsonSerializer.class, config.get(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG));
        assertEquals("all", config.get(ProducerConfig.ACKS_CONFIG));
        assertEquals(true, config.get(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG));
        assertEquals(Integer.MAX_VALUE, config.get(ProducerConfig.RETRIES_CONFIG));
    }

    @Test
    void testConsumerFactoryConfiguration() {
        Map<String, Object> config = consumerFactory.getConfigurationProperties();
        assertEquals("loanflow-group", config.get(ConsumerConfig.GROUP_ID_CONFIG));
        assertEquals(StringDeserializer.class, config.get(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG));
        assertEquals(ErrorHandlingDeserializer.class, config.get(ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG));
        assertEquals(JsonDeserializer.class.getName(), config.get(ErrorHandlingDeserializer.VALUE_DESERIALIZER_CLASS));
        assertEquals("com.loanflow.*", config.get(JsonDeserializer.TRUSTED_PACKAGES));
    }
}
