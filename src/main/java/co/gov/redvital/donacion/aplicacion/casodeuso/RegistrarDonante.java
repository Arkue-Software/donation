package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.comun.EjecutorIdempotente;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CuentasIdentidadPort;
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
import co.gov.redvital.donacion.dominio.excepcion.ReglaNegocio;
import co.gov.redvital.donacion.dominio.modelo.Donante;
import co.gov.redvital.donacion.dominio.modelo.Finalidad;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * RF-01, registro con perfil completo («Crear cuenta»). Crea la cuenta en el
 * Servicio de Identidad y el perfil con sus dos consentimientos, separados.
 *
 * La llamada a Identidad ocurre fuera de la transacción local y con una clave
 * de idempotencia derivada: si el perfil no llega a guardarse, el reintento
 * con la misma clave obtiene la misma cuenta y completa el registro. No hay
 * compensación posible porque nada se borra (IN-18); una cuenta huérfana no
 * ve nada, porque toda operación del donante pasa por su perfil.
 */
public final class RegistrarDonante {

    private static final String OPERACION = "registrar_donante";
    private static final Pattern DOCUMENTO = Pattern.compile("^[A-Za-z0-9]{5,20}$");
    private static final Pattern CORREO = Pattern.compile("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$");
    private static final Pattern TELEFONO = Pattern.compile("^[0-9+ ]{7,20}$");

    public record Comando(String documento, String nombre, LocalDate fechaNacimiento, String correoAcceso,
                          String credencial, String correoContacto, String telefono, String municipioRuta,
                          boolean autorizaTratamiento, boolean autorizaAvisos, String versionAviso) {

        /** Forma que entra en la huella: nunca incluye la credencial. */
        Object sinCredencial() {
            return new Comando(documento, nombre, fechaNacimiento, correoAcceso, null, correoContacto, telefono,
                    municipioRuta, autorizaTratamiento, autorizaAvisos, versionAviso);
        }
    }

    public record Resultado(UUID donanteId, UUID usuarioId) {
    }

    private final RepositorioDonantes donantes;
    private final RepositorioConsentimientos consentimientos;
    private final CuentasIdentidadPort cuentas;
    private final ResumidorDocumento resumidor;
    private final IdempotenciaPort idempotencia;
    private final HuellaPeticion huellas;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Auditor auditor;
    private final Clock reloj;

    public RegistrarDonante(RepositorioDonantes donantes, RepositorioConsentimientos consentimientos,
                            CuentasIdentidadPort cuentas, ResumidorDocumento resumidor,
                            IdempotenciaPort idempotencia, HuellaPeticion huellas,
                            UnidadDeTrabajo unidadDeTrabajo, Auditor auditor, Clock reloj) {
        this.donantes = donantes;
        this.consentimientos = consentimientos;
        this.cuentas = cuentas;
        this.resumidor = resumidor;
        this.idempotencia = idempotencia;
        this.huellas = huellas;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    public EjecutorIdempotente.Resultado<Resultado> ejecutar(Comando c, String clave, ContextoPeticion ctx) {
        validar(c);
        String sujeto = ctx.sujetoAnonimo();
        String huella = huellas.calcular(c.sinCredencial());

        Optional<UUID> previo = buscarReenvio(sujeto, clave, huella);
        if (previo.isPresent()) {
            return new EjecutorIdempotente.Resultado<>(reconstruir(previo.get()), true);
        }

        String documentoHash = resumidor.resumir(c.documento().trim());
        if (donantes.porDocumentoHash(documentoHash).isPresent()) {
            throw noRegistrable();
        }

        UUID usuarioId = cuentas.crearCuentaDonante(c.correoAcceso().trim().toLowerCase(), c.credencial(),
                c.nombre().trim(), derivarClave(sujeto, clave));

        return unidadDeTrabajo.ejecutar(() -> {
            Instant ahora = reloj.instant();
            Optional<Donante> existente = donantes.porUsuario(usuarioId);
            if (existente.isPresent()) {
                return new EjecutorIdempotente.Resultado<>(new Resultado(existente.get().id(), usuarioId), true);
            }
            UUID donanteId = UUID.randomUUID();
            var operacion = new IdempotenciaPort.Operacion(sujeto, clave, OPERACION, huella, donanteId, 201,
                    ahora, ahora.plus(EjecutorIdempotente.VIGENCIA));
            if (!idempotencia.reservar(operacion)) {
                UUID recurso = buscarReenvio(sujeto, clave, huella).orElseThrow(ClaveIdempotenciaReutilizada::new);
                return new EjecutorIdempotente.Resultado<>(reconstruir(recurso), true);
            }
            donantes.insertar(new Donante(donanteId, usuarioId, documentoHash, c.nombre().trim(),
                    c.correoContacto(), c.telefono(), c.fechaNacimiento(), c.municipioRuta(), null,
                    null, null, true), ahora);
            consentimientos.insertar(donanteId, Finalidad.TRATAMIENTO_DATOS, true, c.versionAviso(), ahora);
            consentimientos.insertar(donanteId, Finalidad.AVISOS_CAMPANAS, c.autorizaAvisos(), c.versionAviso(), ahora);
            auditor.permitidoAnonimo(OPERACION, "donante", donanteId, ctx);
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
        if (c.fechaNacimiento() == null || !c.fechaNacimiento().isBefore(LocalDate.now(reloj))) {
            throw new DatosInvalidos("El campo fecha_nacimiento debe ser una fecha pasada.");
        }
        if (c.correoAcceso() == null || c.correoAcceso().length() > 160 || !CORREO.matcher(c.correoAcceso().trim()).matches()) {
            throw new DatosInvalidos("El campo correo_acceso no es un correo válido.");
        }
        if (c.correoContacto() != null && (c.correoContacto().length() > 160 || !CORREO.matcher(c.correoContacto()).matches())) {
            throw new DatosInvalidos("El campo correo no es un correo válido.");
        }
        if (c.credencial() == null || c.credencial().length() < 10 || c.credencial().length() > 128
                || !c.credencial().matches(".*[A-Za-z].*") || !c.credencial().matches(".*\\d.*")) {
            throw new DatosInvalidos("La contraseña debe tener al menos 10 caracteres, con letras y números.");
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
     * Mensaje genérico: no dice si el documento o el correo ya existen, para no
     * convertir el registro público en un oráculo de quién está registrado.
     */
    static Conflicto noRegistrable() {
        return new Conflicto("No es posible crear el perfil con estos datos. Si ya tienes una cuenta, inicia sesión.");
    }

    /** Clave hacia Identidad: las claves de distintos orígenes nunca colisionan allí. */
    private static String derivarClave(String sujeto, String clave) {
        try {
            byte[] resumen = MessageDigest.getInstance("SHA-256")
                    .digest((sujeto + ":" + clave).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(resumen);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }
}
