package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.comun.EjecutorIdempotente;
import co.gov.redvital.donacion.aplicacion.eventos.AvisoSolicitadoV1;
import co.gov.redvital.donacion.aplicacion.eventos.Temas;
import co.gov.redvital.donacion.aplicacion.puerto.salida.BandejaSalidaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.HuellaPeticion;
import co.gov.redvital.donacion.aplicacion.puerto.salida.IdempotenciaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioConsentimientos;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonantes;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ResumidorDocumento;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.excepcion.ClaveIdempotenciaReutilizada;
import co.gov.redvital.donacion.dominio.excepcion.Conflicto;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.excepcion.OperacionNoPermitida;
import co.gov.redvital.donacion.dominio.excepcion.ReglaNegocio;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Donante;
import co.gov.redvital.donacion.dominio.modelo.Finalidad;
import co.gov.redvital.donacion.dominio.modelo.Rol;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.Period;
import java.time.ZoneId;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * RF-01, perfil del donante registrado («Crear cuenta», segundo paso). La
 * cuenta ya existe: la creó la persona en el Servicio de Identidad desde la
 * web, que inició sesión con ella y llama aquí con su token. Donación no le
 * pide nada a Identidad: el perfil se enlaza con la cuenta por la
 * reivindicación {@code sub} del token. Los servicios solo se comunican por
 * Kafka.
 *
 * Crea el perfil con sus dos consentimientos, separados, y en la misma
 * transacción deja en la bandeja el aviso {@code cuenta_creada} (EV-02), con
 * el correo de contacto: el trabajador de Notificaciones envía la confirmación
 * sin consultar a nadie. El registro nunca espera al correo.
 *
 * Solo se registran mayores de edad: la donación de sangre en Colombia exige
 * 18 años cumplidos.
 */
public final class RegistrarDonante {

    public static final int EDAD_MINIMA = 18;
    private static final String OPERACION = "registrar_donante";
    private static final Pattern DOCUMENTO = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TELEFONO = Pattern.compile("^[0-9+ ]{7,20}$");

    public record Comando(String documento, String nombre, LocalDate fechaNacimiento, String correoContacto,
                          String telefono, String municipioRuta, boolean autorizaTratamiento, boolean autorizaAvisos,
                          String versionAviso) {
    }

    public record Resultado(UUID donanteId, UUID usuarioId) {
    }

    private final RepositorioDonantes donantes;
    private final RepositorioConsentimientos consentimientos;
    private final ResumidorDocumento resumidor;
    private final IdempotenciaPort idempotencia;
    private final HuellaPeticion huellas;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Auditor auditor;
    private final BandejaSalidaPort bandeja;
    private final boolean avisosHabilitados;
    private final ZoneId zona;
    private final Clock reloj;

    public RegistrarDonante(RepositorioDonantes donantes, RepositorioConsentimientos consentimientos,
                            ResumidorDocumento resumidor, IdempotenciaPort idempotencia, HuellaPeticion huellas,
                            UnidadDeTrabajo unidadDeTrabajo, Auditor auditor, BandejaSalidaPort bandeja,
                            boolean avisosHabilitados, ZoneId zona, Clock reloj) {
        this.donantes = donantes;
        this.consentimientos = consentimientos;
        this.resumidor = resumidor;
        this.idempotencia = idempotencia;
        this.huellas = huellas;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.auditor = auditor;
        this.bandeja = bandeja;
        this.avisosHabilitados = avisosHabilitados;
        this.zona = zona;
        this.reloj = reloj;
    }

    public EjecutorIdempotente.Resultado<Resultado> ejecutar(Actor actor, Comando c, String clave, ContextoPeticion ctx) {
        if (actor.rol() != Rol.DONANTE) {
            auditor.denegado(actor, OPERACION, "donante", null, null, ctx);
            throw new OperacionNoPermitida();
        }
        UUID usuarioId = actor.cuentaId();
        validar(c);
        String sujeto = actor.sub();
        String huella = huellas.calcular(c);

        Optional<UUID> previo = buscarReenvio(sujeto, clave, huella);
        if (previo.isPresent()) {
            return new EjecutorIdempotente.Resultado<>(reconstruir(previo.get()), true);
        }
        String documentoHash = resumidor.resumir(c.documento().trim());

        return unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.instant();
            // Una cuenta tiene a lo sumo un perfil, y un documento a lo sumo una cuenta.
            if (donantes.porUsuario(usuarioId).isPresent() || donantes.porDocumentoHash(documentoHash).isPresent()) {
                throw noRegistrable();
            }
            UUID donanteId = UUID.randomUUID();
            var operacion = new IdempotenciaPort.Operacion(sujeto, clave, OPERACION, huella, donanteId, 201,
                    ahora, ahora.plus(EjecutorIdempotente.VIGENCIA));
            if (!idempotencia.reservar(operacion)) {
                UUID recurso = buscarReenvio(sujeto, clave, huella).orElseThrow(ClaveIdempotenciaReutilizada::new);
                return new EjecutorIdempotente.Resultado<>(reconstruir(recurso), true);
            }
            String nombre = c.nombre().trim();
            donantes.insertar(new Donante(donanteId, usuarioId, documentoHash, nombre,
                    c.correoContacto(), c.telefono(), c.fechaNacimiento(), c.municipioRuta(), null,
                    null, null, true), ahora);
            consentimientos.insertar(donanteId, Finalidad.TRATAMIENTO_DATOS, true, c.versionAviso(), ahora);
            consentimientos.insertar(donanteId, Finalidad.AVISOS_CAMPANAS, c.autorizaAvisos(), c.versionAviso(), ahora);
            auditor.permitido(actor, OPERACION, "donante", donanteId, ctx);
            if (avisosHabilitados && c.correoContacto() != null) {
                var aviso = new AvisoSolicitadoV1(UUID.randomUUID(), AvisoSolicitadoV1.AVISO_CUENTA_CREADA, "donante",
                        donanteId, c.correoContacto(), nombre, null, Map.of(), ahora);
                bandeja.encolar(new BandejaSalidaPort.EventoSalida(aviso.avisoId(), AvisoSolicitadoV1.TIPO,
                        AvisoSolicitadoV1.VERSION, Temas.AVISO_SOLICITADO, donanteId.toString(), aviso,
                        AvisoSolicitadoV1.AVISO_CUENTA_CREADA + ":" + donanteId + ":" + donanteId,
                        ctx.correlacionId()), ahora);
            }
            return new EjecutorIdempotente.Resultado<>(new Resultado(donanteId, usuarioId), false);
        });
    }

    private Optional<UUID> buscarReenvio(String sujeto, String clave, String huella) {
        return idempotencia.buscar(sujeto, clave).map(previa -> {
            if (!previa.huella().equals(huella) || !previa.operacion().equals(OPERACION)) {
                throw new ClaveIdempotenciaReutilizada();
            }
            return previa.recursoId();
        });
    }

    private Resultado reconstruir(UUID donanteId) {
        return donantes.buscar(donanteId)
                .map(d -> new Resultado(d.id(), d.usuarioId()))
                .orElseThrow(NoEncontrado::new);
    }

    private void validar(Comando c) {
        if (c.documento() == null || !DOCUMENTO.matcher(c.documento().trim()).matches()) {
            throw new DatosInvalidos("El campo documento no tiene un formato válido.");
        }
        Validaciones.requerido(c.nombre(), "nombre", 120);
        LocalDate hoy = LocalDate.now(reloj.withZone(zona));
        if (c.fechaNacimiento() == null || !c.fechaNacimiento().isBefore(hoy)) {
            throw new DatosInvalidos("El campo fecha_nacimiento debe ser una fecha pasada.");
        }
        if (Period.between(c.fechaNacimiento(), hoy).getYears() < EDAD_MINIMA) {
            throw new ReglaNegocio("menor-de-edad", "Para crear una cuenta de donante debes tener 18 años cumplidos.");
        }
        if (c.correoContacto() != null && (c.correoContacto().length() > 160 || !CORREO.matcher(c.correoContacto()).matches())) {
            throw new DatosInvalidos("El campo correo no es un correo válido.");
        }
        if (c.telefono() != null && !TELEFONO.matcher(c.telefono()).matches()) {
            throw new DatosInvalidos("El campo telefono no tiene un formato válido.");
        }
        Validaciones.rutaMunicipio(c.municipioRuta(), "municipio_ruta");
        Validaciones.requerido(c.versionAviso(), "version_aviso", 20);
        if (!c.autorizaTratamiento()) {
            throw new ReglaNegocio("consentimiento-requerido",
                    "Sin la autorización de tratamiento de datos no es posible crear el perfil.");
        }
    }

    /**
     * Mensaje genérico: no dice si el documento ya existe, para no convertir el
     * registro en un oráculo de quién está registrado.
     */
    static Conflicto noRegistrable() {
        return new Conflicto("No es posible crear el perfil con estos datos. Si ya tienes un perfil, inicia sesión con esa cuenta.");
    }
}
