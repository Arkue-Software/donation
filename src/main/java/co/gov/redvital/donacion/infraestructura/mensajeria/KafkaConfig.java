package co.gov.redvital.donacion.infraestructura.mensajeria;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.kafka.retrytopic.RetryTopicConfigurationSupport;
import org.springframework.kafka.retrytopic.RetryTopicSchedulerWrapper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.transaction.CannotCreateTransactionException;
import org.springframework.util.backoff.FixedBackOff;

/**
 * Manejo de fallos de los consumidores (DD 14.1; Herramientas V4.0, entradas
 * 82 y 95). Distingue dos clases de fallo:
 * <ul>
 *   <li><b>El mensaje falla por sí mismo.</b> Se reintenta en los temas
 *   {@code <tema>.donacion.retry-0..2}, con espera creciente, sin detener la
 *   partición: los eventos que vienen detrás se siguen aplicando. Agotados los
 *   intentos, va a {@code <tema>.donacion.dlt}. Un mensaje que no cumple el
 *   contrato va directo a ese tema (ver {@link ConsumidorCampaniaPublicada}).</li>
 *   <li><b>Falla una dependencia</b> (la base no responde). El evento no tiene
 *   la culpa y ningún otro podría aplicarse: el consumidor reintenta en el
 *   sitio hasta que la base vuelva, sin llenar los temas de reintento ni el de
 *   mensajes muertos con eventos sanos.</li>
 * </ul>
 * Los temas los crea el trabajo de inicialización de Kafka, nunca el servicio.
 */
@Configuration
class KafkaConfig extends RetryTopicConfigurationSupport {

    static final String SUFIJO_REINTENTO = ".donacion.retry";
    static final String SUFIJO_DLT = ".donacion.dlt";

    private final Duration esperaDependencia;

    KafkaConfig(@Value("${redvital.kafka.espera-dependencia:5s}") Duration esperaDependencia) {
        this.esperaDependencia = esperaDependencia;
    }

    @Override
    protected void configureBlockingRetries(BlockingRetriesConfigurer reintentos) {
        reintentos
                .retryOn(DataAccessResourceFailureException.class, TransientDataAccessException.class,
                        CannotCreateTransactionException.class)
                .backOff(new FixedBackOff(esperaDependencia.toMillis(), FixedBackOff.UNLIMITED_ATTEMPTS));
    }

    /** Programador propio de las esperas de los temas de reintento: no depende de @EnableScheduling. */
    @Bean
    RetryTopicSchedulerWrapper programadorReintentos() {
        var programador = new ThreadPoolTaskScheduler();
        programador.setThreadNamePrefix("kafka-reintento-");
        return new RetryTopicSchedulerWrapper(programador); // lo inicia y lo detiene con el contexto
    }
}
