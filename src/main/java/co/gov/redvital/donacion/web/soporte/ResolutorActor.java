package co.gov.redvital.donacion.web.soporte;

import co.gov.redvital.donacion.dominio.excepcion.OperacionNoPermitida;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Rol;
import java.util.List;
import java.util.UUID;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

/**
 * Traduce el token validado al actor del dominio. La jurisdicción llega como
 * texto (`institucion:<uuid>` o `territorio:<ruta>`) o, según el DD, como
 * lista; se aceptan ambas formas.
 */
public final class ResolutorActor {

    private ResolutorActor() {
    }

    /** Actor de la petición, o nulo si es anónima. */
    public static Actor actual() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (!(autenticacion instanceof JwtAuthenticationToken token)) {
            return null;
        }
        return desde(token.getToken());
    }

    public static Actor desde(Jwt jwt) {
        Rol rol = Rol.desdeCodigo(jwt.getClaimAsString("role")).orElseThrow(OperacionNoPermitida::new);
        String jurisdiccion = jurisdiccion(jwt.getClaims().get("jurisdiction"));
        UUID institucion = null;
        if (jurisdiccion != null && jurisdiccion.startsWith("institucion:")) {
            try {
                institucion = UUID.fromString(jurisdiccion.substring("institucion:".length()));
            } catch (IllegalArgumentException e) {
                institucion = null;
            }
        }
        return new Actor(jwt.getSubject(), rol, institucion, jurisdiccion);
    }

    private static String jurisdiccion(Object valor) {
        if (valor instanceof String texto) {
            return texto.isBlank() ? null : texto;
        }
        if (valor instanceof List<?> lista && lista.size() == 1 && lista.getFirst() instanceof String texto) {
            return texto;
        }
        return null;
    }
}
