package co.gov.redvital.donacion.web.error;

import co.gov.redvital.donacion.dominio.excepcion.ClaveIdempotenciaReutilizada;
import co.gov.redvital.donacion.dominio.excepcion.ConfirmacionInvalida;
import co.gov.redvital.donacion.dominio.excepcion.Conflicto;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.ExcepcionDominio;
import co.gov.redvital.donacion.dominio.excepcion.FueraDeJurisdiccion;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.excepcion.OperacionNoPermitida;
import co.gov.redvital.donacion.dominio.excepcion.ReglaNegocio;
import co.gov.redvital.donacion.dominio.excepcion.ServicioNoDisponible;
import co.gov.redvital.donacion.dominio.excepcion.TransicionNoPermitida;
import co.gov.redvital.donacion.infraestructura.seguridad.ManejadorDenegaciones;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingRequestHeaderException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

/**
 * Traduce los errores a problemas RFC 9457. El detalle nombra el problema y
 * el campo, nunca el valor recibido (DD, sección 12).
 */
@RestControllerAdvice
class ManejadorProblemas {

    private static final Logger LOG = LoggerFactory.getLogger(ManejadorProblemas.class);

    private final EscritorProblemas escritor;
    private final ManejadorDenegaciones denegaciones;

    ManejadorProblemas(EscritorProblemas escritor, ManejadorDenegaciones denegaciones) {
        this.escritor = escritor;
        this.denegaciones = denegaciones;
    }

    @ExceptionHandler(ExcepcionDominio.class)
    ResponseEntity<Problema> dominio(ExcepcionDominio e, HttpServletRequest peticion) {
        return switch (e) {
            case NoEncontrado x -> escritor.respuesta(peticion, 404, "no-encontrado", "No encontrado", x.getMessage());
            case OperacionNoPermitida x -> {
                denegaciones.auditar(peticion);
                yield escritor.respuesta(peticion, 403, "operacion-no-permitida", "Operación no permitida", x.getMessage());
            }
            case FueraDeJurisdiccion x ->
                    escritor.respuesta(peticion, 403, "fuera-de-jurisdiccion", "Fuera de jurisdicción", x.getMessage());
            case TransicionNoPermitida x ->
                    escritor.respuesta(peticion, 409, "conflicto-de-estado", "Conflicto de estado", x.getMessage());
            case Conflicto x -> escritor.respuesta(peticion, 409, "conflicto", "Conflicto", x.getMessage());
            case ConfirmacionInvalida x -> escritor.respuesta(peticion, 422, "confirmacion-invalida",
                    "Confirmación inválida", x.getMessage());
            case ClaveIdempotenciaReutilizada x -> escritor.respuesta(peticion, 422, "clave-idempotencia-reutilizada",
                    "Clave de idempotencia reutilizada", x.getMessage());
            case ReglaNegocio x -> escritor.respuesta(peticion, 422, x.tipo(),
                    "No es posible completar la operación", x.getMessage());
            case DatosInvalidos x -> escritor.respuesta(peticion, 400, "validacion", "Datos inválidos", x.getMessage());
            case ServicioNoDisponible x -> escritor.respuesta(peticion, 503, "servicio-no-disponible",
                    "Servicio no disponible", x.getMessage());
            default -> escritor.respuesta(peticion, 400, "validacion", "Datos inválidos", e.getMessage());
        };
    }

    @ExceptionHandler(AccessDeniedException.class)
    ResponseEntity<Problema> denegado(HttpServletRequest peticion) {
        denegaciones.auditar(peticion);
        return escritor.respuesta(peticion, 403, "operacion-no-permitida", "Operación no permitida",
                "El rol no autoriza esta operación.");
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<Problema> invalido(MethodArgumentNotValidException e, HttpServletRequest peticion) {
        String campo = e.getBindingResult().getFieldErrors().isEmpty()
                ? "cuerpo"
                : e.getBindingResult().getFieldErrors().getFirst().getField();
        return escritor.respuesta(peticion, 400, "validacion", "Datos inválidos",
                "El campo " + aSnake(campo) + " no es válido.");
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<Problema> ilegible(HttpServletRequest peticion) {
        return escritor.respuesta(peticion, 400, "validacion", "Datos inválidos",
                "El cuerpo de la petición no es válido o contiene campos no admitidos.");
    }

    @ExceptionHandler(MissingRequestHeaderException.class)
    ResponseEntity<Problema> sinCabecera(MissingRequestHeaderException e, HttpServletRequest peticion) {
        return escritor.respuesta(peticion, 400, "validacion", "Datos inválidos",
                "Falta la cabecera " + e.getHeaderName() + ".");
    }

    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    ResponseEntity<Problema> parametro(Exception e, HttpServletRequest peticion) {
        String nombre = e instanceof MethodArgumentTypeMismatchException m ? m.getName()
                : ((MissingServletRequestParameterException) e).getParameterName();
        return escritor.respuesta(peticion, 400, "validacion", "Datos inválidos",
                "El parámetro " + nombre + " no es válido.");
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<Problema> sinRuta(HttpServletRequest peticion) {
        return escritor.respuesta(peticion, 404, "no-encontrado", "No encontrado", "Ruta no disponible.");
    }

    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    ResponseEntity<Problema> metodo(HttpServletRequest peticion) {
        return escritor.respuesta(peticion, 405, "metodo-no-permitido", "Método no permitido",
                "La ruta no admite ese método.");
    }

    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    ResponseEntity<Problema> tipoMedio(HttpServletRequest peticion) {
        return escritor.respuesta(peticion, 415, "tipo-no-admitido", "Tipo de contenido no admitido",
                "El cuerpo debe enviarse como application/json.");
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<Problema> inesperado(Exception e, HttpServletRequest peticion) {
        LOG.error("Error no controlado", e);
        return escritor.respuesta(peticion, 500, "error-interno", "Error interno",
                "Ocurrió un error inesperado. Si persiste, informa el identificador de correlación.");
    }

    private static String aSnake(String campo) {
        return campo.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }
}
