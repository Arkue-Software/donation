package co.gov.redvital.donacion.infraestructura.mensajeria;

import org.apache.kafka.common.TopicPartition;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.listener.CommonErrorHandler;
import org.springframework.kafka.listener.DeadLetterPublishingRecoverer;
import org.springframework.kafka.listener.DefaultErrorHandler;
import org.springframework.kafka.support.ExponentialBackOffWithMaxRetries;

/**
 * Manejo de errores del consumidor (ADR-018): tres reintentos con espera
 * creciente y, si persisten, el mensaje va a {@code <tema>.donacion.dlt}. Un
 * mensaje mal formado va directo al tema de mensajes muertos. Los temas los
 * crea el trabajo de inicialización de Kafka, nunca el servicio.
 */
@Configuration
class KafkaConfig {

    static final String SUFIJO_DLT = ".donacion.dlt";

    @Bean
    CommonErrorHandler manejadorErroresConsumidor(KafkaTemplate<String, String> kafka) {
        var recuperador = new DeadLetterPublishingRecoverer(kafka,
                (registro, error) -> new TopicPartition(registro.topic() + SUFIJO_DLT, -1));
        var espera = new ExponentialBackOffWithMaxRetries(3);
        espera.setInitialInterval(2_000);
        espera.setMultiplier(5);
        espera.setMaxInterval(60_000);
        var manejador = new DefaultErrorHandler(recuperador, espera);
        manejador.addNotRetryableExceptions(EventoMalFormado.class);
        return manejador;
    }
}
