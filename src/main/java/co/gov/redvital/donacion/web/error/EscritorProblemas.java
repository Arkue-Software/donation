package co.gov.redvital.donacion.web.error;

import co.gov.redvital.donacion.web.soporte.FiltroCorrelacion;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/** Construye y escribe problemas, tanto desde controladores como desde los filtros de seguridad. */
@Component
public class EscritorProblemas {

    private final JsonMapper json;

    public EscritorProblemas(JsonMapper json) {
        this.json = json;
    }

    public ResponseEntity<Problema> respuesta(HttpServletRequest peticion, int estado, String tipo, String titulo,
                                              String detalle) {
        return ResponseEntity.status(estado)
                .header("Content-Type", Problema.TIPO_CONTENIDO)
                .body(new Problema(tipo, titulo, estado, detalle, FiltroCorrelacion.contexto(peticion).correlacionId()));
    }

    public void escribir(HttpServletRequest peticion, HttpServletResponse respuesta, int estado, String tipo,
                         String titulo, String detalle) throws IOException {
        respuesta.setStatus(estado);
        respuesta.setContentType(Problema.TIPO_CONTENIDO);
        respuesta.setCharacterEncoding("UTF-8");
        json.writeValue(respuesta.getOutputStream(),
                new Problema(tipo, titulo, estado, detalle, FiltroCorrelacion.contexto(peticion).correlacionId()));
    }
}
