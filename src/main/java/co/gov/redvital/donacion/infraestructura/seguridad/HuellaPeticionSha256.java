package co.gov.redvital.donacion.infraestructura.seguridad;

import co.gov.redvital.donacion.aplicacion.puerto.salida.HuellaPeticion;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import org.springframework.stereotype.Component;
import tools.jackson.databind.MapperFeature;
import tools.jackson.databind.json.JsonMapper;

/** SHA-256 de la forma canónica (propiedades ordenadas) del cuerpo de la petición. */
@Component
class HuellaPeticionSha256 implements HuellaPeticion {

    private final JsonMapper canonico;

    HuellaPeticionSha256(JsonMapper json) {
        this.canonico = json.rebuild().enable(MapperFeature.SORT_PROPERTIES_ALPHABETICALLY).build();
    }

    @Override
    public String calcular(Object cuerpo) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256").digest(canonico.writeValueAsBytes(cuerpo));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
