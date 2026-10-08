package co.gov.redvital.donacion.infraestructura.mensajeria;

import java.nio.charset.StandardCharsets;
import java.util.Optional;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.Headers;

/** Cabeceras obligatorias de todo evento, según los contratos AsyncAPI. */
final class Cabeceras {

    static final String EVENTO_ID = "evento-id";
    static final String EVENTO_TIPO = "evento-tipo";
    static final String EVENTO_VERSION = "evento-version";
    static final String CORRELACION_ID = "correlacion-id";
    static final String OCURRIDO_EN = "ocurrido-en";
    static final String PRODUCTOR = "productor";

    private Cabeceras() {
    }

    static void agregar(Headers cabeceras, String nombre, String valor) {
        if (valor != null) {
            cabeceras.add(nombre, valor.getBytes(StandardCharsets.UTF_8));
        }
    }

    static Optional<String> leer(Headers cabeceras, String nombre) {
        Header ultima = cabeceras.lastHeader(nombre);
        return ultima == null || ultima.value() == null
                ? Optional.empty()
                : Optional.of(new String(ultima.value(), StandardCharsets.UTF_8));
    }
}
