package co.gov.redvital.donacion.infraestructura.seguridad;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.web.error.EscritorProblemas;
import co.gov.redvital.donacion.web.soporte.FiltroCorrelacion;
import co.gov.redvital.donacion.web.soporte.ResolutorActor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

/**
 * 401 y 403 con el mismo formato de problema que el resto del API. El 401
 * dice lo mismo para toda causa (token ausente, vencido o alterado). El 403
 * queda en la bitácora con el actor y la operación intentada, nunca con el
 * dato al que se intentó acceder.
 */
@Component
public class ManejadorDenegaciones implements AuthenticationEntryPoint, AccessDeniedHandler {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorDenegaciones.class);

    private final EscritorProblemas escritor;
    private final Auditor auditor;

    ManejadorDenegaciones(EscritorProblemas escritor, Auditor auditor) {
        this.escritor = escritor;
        this.auditor = auditor;
    }

    @Override
    public void commence(HttpServletRequest peticion, HttpServletResponse respuesta, AuthenticationException e)
            throws IOException {
        escritor.escribir(peticion, respuesta, 401, "sesion-invalida", "Sesión inválida",
                "El token es inválido o expiró.");
    }

    @Override
    public void handle(HttpServletRequest peticion, HttpServletResponse respuesta, AccessDeniedException e)
            throws IOException {
        auditar(peticion);
        escritor.escribir(peticion, respuesta, 403, "operacion-no-permitida", "Operación no permitida",
                "El rol no autoriza esta operación.");
    }

    /** Registra la denegación sin dejar que un fallo de la bitácora cambie la respuesta. */
    public void auditar(HttpServletRequest peticion) {
        try {
            Actor actor = ResolutorActor.actual();
            if (actor != null) {
                String operacion = peticion.getMethod() + " " + plantilla(peticion.getRequestURI());
                auditor.denegado(actor, operacion, recurso(peticion.getRequestURI()), null, null,
                        FiltroCorrelacion.contexto(peticion));
            }
        } catch (RuntimeException ex) {
            LOG.warn("No fue posible registrar la denegación en la bitácora", ex);
        }
    }

    /** La ruta sin identificadores, para no guardar en la bitácora el recurso al que se intentó acceder. */
    private static String plantilla(String ruta) {
        return ruta.replaceAll("[0-9a-fA-F]{8}-[0-9a-fA-F-]{27}", "{id}")
                .replaceAll("/v1/intenciones/[^/]+", "/v1/intenciones/{codigo}");
    }

    private static String recurso(String ruta) {
        String[] partes = ruta.split("/");
        return partes.length > 2 ? partes[2] : "ruta";
    }
}
