package com.odin.wms.config;

import com.odin.wms.messaging.event.CrmOrderConfirmedEvent;
import com.odin.wms.messaging.event.MrpProductionOrderCancelledEvent;
import com.odin.wms.messaging.event.MrpProductionOrderReleasedEvent;
import com.odin.wms.messaging.event.PackingCompletedEvent;
import com.odin.wms.messaging.event.PickingCompletedEvent;
import lombok.extern.slf4j.Slf4j;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.ConcurrentKafkaListenerContainerFactory;
import org.springframework.kafka.core.ConsumerFactory;
import org.springframework.kafka.core.DefaultKafkaConsumerFactory;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.listener.KafkaListenerErrorHandler;
import org.springframework.kafka.support.serializer.JsonDeserializer;
import org.springframework.util.backoff.FixedBackOff;

import java.util.HashMap;
import java.util.Map;

/**
 * Configuração Kafka para o consumer de eventos CRM.
 * Necessária pois o consumer padrão tem default.type fixo em PurchaseOrderConfirmedEvent.
 * O factory CRM usa CrmOrderConfirmedEvent como tipo padrão para deserialização.
 */
@Slf4j
@Configuration
public class KafkaConfig {

    @Value("${spring.kafka.bootstrap-servers:localhost:9092}")
    private String bootstrapServers;

    @Value("${spring.kafka.consumer.group-id:wms-receiving}")
    private String groupId;

    @Bean
    public ConsumerFactory<String, CrmOrderConfirmedEvent> crmOrderConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, groupId);
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        JsonDeserializer<CrmOrderConfirmedEvent> deserializer =
                new JsonDeserializer<>(CrmOrderConfirmedEvent.class, false);
        deserializer.addTrustedPackages("com.odin.wms.messaging.event");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, CrmOrderConfirmedEvent>
    crmKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, CrmOrderConfirmedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(crmOrderConsumerFactory());
        return factory;
    }

    /**
     * Error handler para o consumer de eventos CRM.
     * Loga o erro e retorna null (sem reprocessamento — Kafka avança o offset).
     */
    @Bean
    public KafkaListenerErrorHandler pickingKafkaErrorHandler() {
        return (message, exception) -> {
            log.error("Falha ao processar CRM order event: {} — payload: {}",
                    exception.getMessage(), message.getPayload(), exception);
            return null;
        };
    }

    // -------------------------------------------------------------------------
    // Packing consumer (Story 5.2) — consome wms.picking.completed
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, PickingCompletedEvent> packingOrderConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "wms-packing-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        JsonDeserializer<PickingCompletedEvent> deserializer =
                new JsonDeserializer<>(PickingCompletedEvent.class, false);
        deserializer.addTrustedPackages("com.odin.wms.messaging.event");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PickingCompletedEvent>
    packingKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PickingCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(packingOrderConsumerFactory());
        return factory;
    }

    // -------------------------------------------------------------------------
    // Shipping consumer (Story 5.3) — consome wms.packing.completed
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, PackingCompletedEvent> shippingOrderConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "wms-shipping-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        JsonDeserializer<PackingCompletedEvent> deserializer =
                new JsonDeserializer<>(PackingCompletedEvent.class, false);
        deserializer.addTrustedPackages("com.odin.wms.messaging.event");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, PackingCompletedEvent>
    shippingKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, PackingCompletedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(shippingOrderConsumerFactory());
        return factory;
    }

    /**
     * Error handler para o consumer de eventos de shipping.
     */
    @Bean
    public KafkaListenerErrorHandler shippingKafkaErrorHandler() {
        return (message, exception) -> {
            log.error("Falha ao processar PackingCompleted (shipping consumer): {} — payload: {}",
                    exception.getMessage(), message.getPayload(), exception);
            return null;
        };
    }

    // -------------------------------------------------------------------------
    // MRP consumer (Story 6.1) — consome mrp.production.order.released
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, MrpProductionOrderReleasedEvent>
    mrpReleasedConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "wms-mrp-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        JsonDeserializer<MrpProductionOrderReleasedEvent> deserializer =
                new JsonDeserializer<>(MrpProductionOrderReleasedEvent.class, false);
        deserializer.addTrustedPackages("com.odin.wms.messaging.event");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MrpProductionOrderReleasedEvent>
    mrpReleasedKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, MrpProductionOrderReleasedEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(mrpReleasedConsumerFactory());
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));
        return factory;
    }

    // -------------------------------------------------------------------------
    // MRP consumer (Story 6.1) — consome mrp.production.order.cancelled
    // -------------------------------------------------------------------------

    @Bean
    public ConsumerFactory<String, MrpProductionOrderCancelledEvent>
    mrpCancelledConsumerFactory() {
        Map<String, Object> props = new HashMap<>();
        props.put(ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(ConsumerConfig.GROUP_ID_CONFIG, "wms-mrp-consumer");
        props.put(ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest");
        props.put(ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class);

        JsonDeserializer<MrpProductionOrderCancelledEvent> deserializer =
                new JsonDeserializer<>(MrpProductionOrderCancelledEvent.class, false);
        deserializer.addTrustedPackages("com.odin.wms.messaging.event");

        return new DefaultKafkaConsumerFactory<>(props, new StringDeserializer(), deserializer);
    }

    @Bean
    public ConcurrentKafkaListenerContainerFactory<String, MrpProductionOrderCancelledEvent>
    mrpCancelledKafkaListenerContainerFactory() {
        ConcurrentKafkaListenerContainerFactory<String, MrpProductionOrderCancelledEvent> factory =
                new ConcurrentKafkaListenerContainerFactory<>();
        factory.setConsumerFactory(mrpCancelledConsumerFactory());
        factory.setCommonErrorHandler(new DefaultErrorHandler(new FixedBackOff(1000L, 3L)));
        return factory;
    }
}
