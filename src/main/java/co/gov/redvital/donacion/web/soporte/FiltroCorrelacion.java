package co.gov.redvital.donacion.web.soporte;

import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlación (EC-20). Respeta el X-Correlacion-Id que genera el gateway; si
 * falta o supera 36 caracteres, genera uno. Corre antes que la seguridad para
 * que también las respuestas 401 y 403 lo lleven.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class FiltroCorrelacion extends OncePerRequestFilter {

    public static final String CABECERA = "X-Correlacion-Id";
    static final String ATRIBUTO = FiltroCorrelacion.class.getName() + ".contexto";

    @Override
    protected void doFilterInternal(HttpServletRequest peticion, HttpServletResponse respuesta, FilterChain cadena)
            throws ServletException, IOException {
        String correlacion = peticion.getHeader(CABECERA);
        if (correlacion == null || correlacion.isBlank() || correlacion.length() > 36) {
            correlacion = UUID.randomUUID().toString();
        }
        peticion.setAttribute(ATRIBUTO, new ContextoPeticion(correlacion, origen(peticion)));
        respuesta.setHeader(CABECERA, correlacion);
        MDC.put("correlacion_id", correlacion);
        try {
            cadena.doFilter(peticion, respuesta);
        } finally {
            MDC.remove("correlacion_id");
        }
    }

    public static ContextoPeticion contexto(HttpServletRequest peticion) {
        Object valor = peticion.getAttribute(ATRIBUTO);
        return valor instanceof ContextoPeticion ctx ? ctx : new ContextoPeticion(UUID.randomUUID().toString(), null);
    }

    /** Origen tomado del gateway: el primer salto de X-Forwarded-For. */
    private static String origen(HttpServletRequest peticion) {
        String reenviado = peticion.getHeader("X-Forwarded-For");
        String valor = reenviado != null && !reenviado.isBlank()
                ? reenviado.split(",")[0].trim()
                : peticion.getRemoteAddr();
        return valor != null && valor.length() > 45 ? valor.substring(0, 45) : valor;
    }
}
