package co.gov.redvital.donacion.infraestructura.seguridad;

import co.gov.redvital.donacion.aplicacion.puerto.salida.ResumidorDocumento;
import co.gov.redvital.donacion.infraestructura.configuracion.PropiedadesRedVital;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * HMAC-SHA256 del documento (DD 5.3.1). Determinista para detectar perfiles
 * duplicados y permitir la búsqueda del operador, e inútil sin la clave, que
 * es un secreto del servicio. El documento en claro nunca se registra.
 */
@Component
class ResumidorDocumentoHmac implements ResumidorDocumento {

    private static final String ALGORITMO = "HmacSHA256";
    private final SecretKeySpec clave;

    ResumidorDocumentoHmac(PropiedadesRedVital propiedades) {
        String valor = propiedades.documento() == null ? null : propiedades.documento().claveHmac();
        if (valor == null || valor.trim().length() < 32) {
            throw new IllegalStateException("Falta la clave HMAC del documento o tiene menos de 32 caracteres.");
        }
        this.clave = new SecretKeySpec(valor.trim().getBytes(StandardCharsets.UTF_8), ALGORITMO);
    }

    @Override
    public String resumir(String documento) {
        try {
            Mac mac = Mac.getInstance(ALGORITMO);
            mac.init(clave);
            return HexFormat.of().formatHex(mac.doFinal(documento.getBytes(StandardCharsets.UTF_8)));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("No fue posible calcular el resumen del documento.", e);
        }
    }
}
