package com.study.config.kafka

import com.fasterxml.jackson.databind.ObjectMapper
import org.springframework.boot.autoconfigure.kafka.KafkaProperties
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.kafka.annotation.EnableKafka
import org.springframework.kafka.core.DefaultKafkaProducerFactory
import org.springframework.kafka.core.KafkaTemplate
import org.springframework.kafka.core.ProducerFactory
import org.springframework.kafka.support.converter.ByteArrayJsonMessageConverter

@EnableKafka
@Configuration
class KafkaConfig {

    @Bean
    fun producerFactory(kafkaProperties: KafkaProperties): ProducerFactory<Any, Any> =
        DefaultKafkaProducerFactory(HashMap(kafkaProperties.buildProducerProperties()))

    @Bean
    fun kafkaTemplate(producerFactory: ProducerFactory<Any, Any>): KafkaTemplate<Any, Any> =
        KafkaTemplate(producerFactory)

    @Bean
    fun jsonMessageConverter(objectMapper: ObjectMapper) = ByteArrayJsonMessageConverter(objectMapper)
}
