package co.gov.redvital.donacion.infraestructura.mensajeria;

import co.gov.redvital.donacion.aplicacion.casodeuso.AplicarCampaniaPublicada;
import co.gov.redvital.donacion.aplicacion.eventos.CampaniaPublicadaV1;
import co.gov.redvital.donacion.aplicacion.eventos.Temas;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.UUID;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.kafka.annotation.BackOff;
import org.springframework.kafka.annotation.DltHandler;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.kafka.annotation.RetryableTopic;
import org.springframework.kafka.retrytopic.TopicSuffixingStrategy;
import org.springframework.kafka.support.KafkaHeaders;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.DeserializationFeature;
import tools.jackson.databind.json.JsonMapper;

/**
 * Consumidor de EV-03 {@code campaign.published} (grupo donacion). Tolera
 * campos nuevos en el mensaje; un mensaje que no cumple el contrato se envía
 * al tema de mensajes muertos sin reintentar. Cualquier otro fallo del mensaje
 * se reintenta en los temas de reintento sin detener la partición (esperas de
 * 2, 10 y 50 segundos); la base caída se espera en el sitio ({@link KafkaConfig}).
 */
@Component
class ConsumidorCampaniaPublicada {

    private static final Logger LOG = LoggerFactory.getLogger(ConsumidorCampaniaPublicada.class);

    private final AplicarCampaniaPublicada caso;
    private final JsonMapper tolerante;
    private final Counter mensajesMuertos;

    ConsumidorCampaniaPublicada(AplicarCampaniaPublicada caso, JsonMapper json, MeterRegistry metricas) {
        this.caso = caso;
        this.tolerante = json.rebuild().disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES).build();
        this.mensajesMuertos = Counter.builder("redvital_eventos_mensajes_muertos") // Prometheus: ..._total
                .description("Eventos que el consumidor no pudo aplicar y quedaron en el tema de mensajes muertos")
                .tag("tema", Temas.CAMPANIA_PUBLICADA)
                .tag("grupo", Temas.GRUPO_DONACION)
                .register(metricas);
    }

    @RetryableTopic(
            attempts = "4",
            backOff = @BackOff(delayString = "${redvital.kafka.espera-reintento:2000}", multiplier = 5, maxDelay = 60_000),
            retryTopicSuffix = KafkaConfig.SUFIJO_REINTENTO,
            dltTopicSuffix = KafkaConfig.SUFIJO_DLT,
            topicSuffixingStrategy = TopicSuffixingStrategy.SUFFIX_WITH_INDEX_VALUE,
            autoCreateTopics = "false",
            exclude = EventoMalFormado.class,
            autoStartDltHandler = "${redvital.procesos.habilitados:true}")
    @KafkaListener(topics = Temas.CAMPANIA_PUBLICADA, groupId = Temas.GRUPO_DONACION,
            autoStartup = "${redvital.procesos.habilitados:true}")
    void consumir(ConsumerRecord<String, String> registro) {
        UUID eventoId = Cabeceras.leer(registro.headers(), Cabeceras.EVENTO_ID)
                .map(ConsumidorCampaniaPublicada::uuid)
                .orElseThrow(() -> new EventoMalFormado("Falta la cabecera evento-id.", null));
        String correlacion = Cabeceras.leer(registro.headers(), Cabeceras.CORRELACION_ID)
                .filter(c -> c.length() <= 36)
                .orElse(UUID.randomUUID().toString());
        CampaniaPublicadaV1 evento = leer(registro.value());
        MDC.put("correlacion_id", correlacion);
        try {
            boolean aplicado = caso.ejecutar(eventoId, evento, correlacion);
            LOG.info("Evento {} de campaña {} {}", eventoId, evento.campaniaId(), aplicado ? "aplicado" : "duplicado");
        } catch (DatosInvalidos e) {
            throw new EventoMalFormado(e.getMessage(), e);
        } finally {
            MDC.remove("correlacion_id");
        }
    }

    /**
     * Un evento en mensajes muertos nunca se descarta en silencio (DD 14.1):
     * queda en el tema, se registra como error y alimenta la métrica de alerta.
     */
    @DltHandler
    void mensajeMuerto(ConsumerRecord<String, String> registro) {
        mensajesMuertos.increment();
        LOG.error("Evento {} en mensajes muertos ({}/{}): {}",
                Cabeceras.leer(registro.headers(), Cabeceras.EVENTO_ID).orElse("sin evento-id"),
                registro.topic(), registro.offset(),
                Cabeceras.leer(registro.headers(), KafkaHeaders.DLT_EXCEPTION_MESSAGE).orElse("sin motivo"));
    }

    private CampaniaPublicadaV1 leer(String carga) {
        try {
            CampaniaPublicadaV1 evento = tolerante.readValue(carga, CampaniaPublicadaV1.class);
            if (evento.campaniaId() == null || evento.institucionId() == null || evento.iniciaEn() == null
                    || evento.terminaEn() == null || evento.publicadaEn() == null || evento.territorioRuta() == null
                    || evento.nombre() == null || evento.sede() == null || evento.territorioCodigo() == null) {
                throw new EventoMalFormado("Faltan campos obligatorios del contrato.", null);
            }
            return evento;
        } catch (JacksonException | IllegalArgumentException e) {
            throw new EventoMalFormado("La carga no cumple el contrato de campaign.published.", e);
        }
    }

    private static UUID uuid(String valor) {
        try {
            return UUID.fromString(valor);
        } catch (IllegalArgumentException e) {
            throw new EventoMalFormado("La cabecera evento-id no es un identificador válido.", e);
        }
    }
}
