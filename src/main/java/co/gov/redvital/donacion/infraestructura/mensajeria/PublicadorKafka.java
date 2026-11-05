package co.gov.redvital.donacion.infraestructura.mensajeria;

import co.gov.redvital.donacion.aplicacion.puerto.salida.BandejaSalidaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.PublicadorMensajesPort;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

/**
 * Única clase que publica en Kafka (IN-23). Productor idempotente con
 * confirmación de todas las réplicas; espera la confirmación del broker antes
 * de que la bandeja marque el evento como publicado.
 */
@Component
class PublicadorKafka implements PublicadorMensajesPort {

    private static final long PLAZO_SEGUNDOS = 10;

    private final KafkaTemplate<String, String> kafka;

    PublicadorKafka(KafkaTemplate<String, String> kafka) {
        this.kafka = kafka;
    }

    @Override
    public void publicar(BandejaSalidaPort.EventoPendiente e) {
        var registro = new ProducerRecord<>(e.tema(), e.clave(), e.cargaJson());
        Cabeceras.agregar(registro.headers(), Cabeceras.EVENTO_ID, e.id().toString());
        Cabeceras.agregar(registro.headers(), Cabeceras.EVENTO_TIPO, e.tipo());
        Cabeceras.agregar(registro.headers(), Cabeceras.EVENTO_VERSION, String.valueOf(e.versionEsquema()));
        Cabeceras.agregar(registro.headers(), Cabeceras.CORRELACION_ID, e.correlacionId());
        Cabeceras.agregar(registro.headers(), Cabeceras.OCURRIDO_EN, e.creadoEn().toString());
        Cabeceras.agregar(registro.headers(), Cabeceras.PRODUCTOR, "donacion");
        try {
            kafka.send(registro).get(PLAZO_SEGUNDOS, TimeUnit.SECONDS);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Publicación interrumpida.", ex);
        } catch (ExecutionException | TimeoutException ex) {
            throw new IllegalStateException("El broker no confirmó la publicación.", ex);
        }
    }

}
