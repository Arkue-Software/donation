package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.comun.EjecutorIdempotente;
import co.gov.redvital.donacion.aplicacion.eventos.DonacionCompletadaV1;
import co.gov.redvital.donacion.aplicacion.eventos.Temas;
import co.gov.redvital.donacion.aplicacion.puerto.salida.BandejaSalidaPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.ProyeccionCampaniasPort;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonaciones;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioDonantes;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioEventosUnidad;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioIntenciones;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioReconocimientos;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioUnidades;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.NoEncontrado;
import co.gov.redvital.donacion.dominio.excepcion.ReglaNegocio;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.CodigoIntencion;
import co.gov.redvital.donacion.dominio.modelo.Componente;
import co.gov.redvital.donacion.dominio.modelo.Donacion;
import co.gov.redvital.donacion.dominio.modelo.Donante;
import co.gov.redvital.donacion.dominio.modelo.Elegibilidad;
import co.gov.redvital.donacion.dominio.modelo.EstadoIntencion;
import co.gov.redvital.donacion.dominio.modelo.IntencionDonacion;
import co.gov.redvital.donacion.dominio.modelo.Reconocimiento;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import co.gov.redvital.donacion.dominio.servicio.CalculadoraVencimiento;
import co.gov.redvital.donacion.dominio.servicio.MotorTransiciones;
import co.gov.redvital.donacion.dominio.servicio.OtorgadorReconocimientos;
import co.gov.redvital.donacion.dominio.servicio.PoliticaCampania;
import co.gov.redvital.donacion.dominio.servicio.PoliticaElegibilidad;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;

/**
 * RF-03. Registra la donación y crea una unidad por componente declarado, en
 * captada, en una sola transacción con su evento de alta, su auditoría y el
 * evento donation.completed en la bandeja de salida. Registrar una donación
 * no depende de que Kafka ni Campañas estén disponibles (EC-08).
 */
public final class RegistrarDonacion {

    private static final String OPERACION = "registrar_donacion";
    private static final String TIPO_POR_DEFECTO = "sangre_total";

    public record ComponenteSolicitado(String componente, Integer volumenMl) {
    }

    public record Comando(UUID donanteId, String intencionCodigo, String tipoDonacion, String grupoSanguineo,
                          UUID campaniaId, List<ComponenteSolicitado> componentes) {
    }

    private final RepositorioDonaciones donaciones;
    private final RepositorioUnidades unidades;
    private final RepositorioEventosUnidad eventos;
    private final RepositorioDonantes donantes;
    private final RepositorioIntenciones intenciones;
    private final RepositorioReconocimientos reconocimientos;
    private final ProyeccionCampaniasPort campanias;
    private final BandejaSalidaPort bandeja;
    private final CatalogoPort catalogo;
    private final EjecutorIdempotente idempotente;
    private final Auditor auditor;
    private final PoliticaElegibilidad elegibilidad;
    private final PoliticaCampania politicaCampania;
    private final OtorgadorReconocimientos otorgador;
    private final CalculadoraVencimiento vencimientos;
    private final ConsultasDeDonaciones consultas;
    private final ZoneId zona;
    private final Clock reloj;

    public RegistrarDonacion(RepositorioDonaciones donaciones, RepositorioUnidades unidades,
                             RepositorioEventosUnidad eventos, RepositorioDonantes donantes,
                             RepositorioIntenciones intenciones, RepositorioReconocimientos reconocimientos,
                             ProyeccionCampaniasPort campanias, BandejaSalidaPort bandeja, CatalogoPort catalogo,
                             EjecutorIdempotente idempotente, Auditor auditor, PoliticaElegibilidad elegibilidad,
                             PoliticaCampania politicaCampania, OtorgadorReconocimientos otorgador,
                             CalculadoraVencimiento vencimientos, ConsultasDeDonaciones consultas,
                             ZoneId zona, Clock reloj) {
        this.donaciones = donaciones;
        this.unidades = unidades;
        this.eventos = eventos;
        this.donantes = donantes;
        this.intenciones = intenciones;
        this.reconocimientos = reconocimientos;
        this.campanias = campanias;
        this.bandeja = bandeja;
        this.catalogo = catalogo;
        this.idempotente = idempotente;
        this.auditor = auditor;
        this.elegibilidad = elegibilidad;
        this.politicaCampania = politicaCampania;
        this.otorgador = otorgador;
        this.vencimientos = vencimientos;
        this.consultas = consultas;
        this.zona = zona;
        this.reloj = reloj;
    }

    public EjecutorIdempotente.Resultado<DonacionRegistrada> ejecutar(Actor actor, Comando c, String clave,
                                                                      ContextoPeticion ctx) {
        UUID institucion = actor.institucion();
        List<Componente> componentes = validar(c);
        String tipo = c.tipoDonacion() == null ? TIPO_POR_DEFECTO : c.tipoDonacion();

        return idempotente.ejecutar(actor.sub(), clave, OPERACION, c, UUID.randomUUID(),
                donacionId -> registrar(donacionId, actor, institucion, c, tipo, componentes, ctx),
                donacionId -> consultas.reconstruir(donacionId));
    }

    private DonacionRegistrada registrar(UUID donacionId, Actor actor, UUID institucion, Comando c, String tipo,
                                         List<Componente> componentes, ContextoPeticion ctx) {
        Instant ahora = reloj.instant();
        LocalDate hoy = ahora.atZone(zona).toLocalDate();

        Donante donante = null;
        if (c.donanteId() != null) {
            donante = donantes.buscar(c.donanteId()).filter(Donante::activo)
                    .orElseThrow(() -> new NoEncontrado("No hay un donante registrado con ese identificador."));
            Elegibilidad e = elegibilidad.evaluar(donante.fechaNacimiento(), donante.fechaUltimaDonacion(), hoy);
            if (!e.elegible()) {
                throw new ReglaNegocio("donante-no-elegible", e.elegibleDesde() == null
                        ? "El donante no es elegible para donar."
                        : "El donante podrá donar a partir del " + e.elegibleDesde() + ".");
            }
        }

        UUID intencionId = null;
        if (c.intencionCodigo() != null) {
            String codigo = CodigoIntencion.normalizar(c.intencionCodigo())
                    .orElseThrow(() -> new NoEncontrado("No encontramos un registro con ese código."));
            IntencionDonacion intencion = intenciones.porCodigoParaActualizar(codigo)
                    .orElseThrow(() -> new NoEncontrado("No encontramos un registro con ese código."));
            if (intencion.estadoEn(ahora) != EstadoIntencion.PENDIENTE) {
                throw new ReglaNegocio("intencion-no-vigente", "El código ya fue usado o venció.");
            }
            intenciones.marcarAtendida(intencion.id(), ahora);
            intencionId = intencion.id();
        }

        var decision = politicaCampania.decidir(c.campaniaId(),
                c.campaniaId() == null ? java.util.Optional.empty() : campanias.buscar(c.campaniaId()),
                institucion, ahora);

        UUID operador = actor.cuentaId();
        var donacion = new Donacion(donacionId, donante == null ? null : donante.id(), intencionId, tipo,
                institucion, decision.campaniaId(), ahora, operador);
        donaciones.insertar(donacion);

        List<Unidad> creadas = new ArrayList<>();
        var motor = new MotorTransiciones(catalogo.matriz());
        for (int i = 0; i < componentes.size(); i++) {
            Componente componente = componentes.get(i);
            Unidad unidad = Unidad.nueva(donacionId, componente.codigo(), c.grupoSanguineo(), institucion,
                    vencimientos.vencimiento(ahora, componente.vidaUtilDias()), c.componentes().get(i).volumenMl(), ahora);
            var evento = motor.crear(unidad, actor.sub(), ctx.correlacionId(), ahora);
            unidades.insertar(unidad, ahora);
            eventos.insertar(evento);
            auditor.permitido(actor, "crear_unidad", "unidad", unidad.id(), ctx);
            creadas.add(unidad);
        }

        if (donante != null) {
            donantes.registrarDonacion(donante.id(), hoy, elegibilidad.elegibleDesdeTrasDonar(hoy),
                    donante.grupoSanguineo() == null ? c.grupoSanguineo() : donante.grupoSanguineo(), ahora);
            otorgarReconocimientos(donante.id(), donacionId, ahora);
        }

        bandeja.encolar(new BandejaSalidaPort.EventoSalida(UUID.randomUUID(), DonacionCompletadaV1.TIPO,
                DonacionCompletadaV1.VERSION, Temas.DONACION_COMPLETADA, donacionId.toString(),
                new DonacionCompletadaV1(donacionId, institucion, decision.campaniaId(), tipo, ahora, creadas.size()),
                null, ctx.correlacionId()), ahora);

        auditor.permitido(actor, OPERACION, "donacion", donacionId, ctx);
        return new DonacionRegistrada(donacion, decision.confirmada(), creadas);
    }

    private void otorgarReconocimientos(UUID donanteId, UUID donacionId, Instant ahora) {
        int total = donaciones.contarDeDonante(donanteId);
        var yaOtorgados = new HashSet<>(reconocimientos.otorgados(donanteId).keySet());
        for (Reconocimiento r : otorgador.nuevos(total, catalogo.reconocimientos(), yaOtorgados)) {
            reconocimientos.otorgar(donanteId, r.id(), donacionId, ahora);
        }
    }

    private List<Componente> validar(Comando c) {
        if (c.donanteId() != null && c.intencionCodigo() != null) {
            throw new DatosInvalidos("Indica el donante o el código de registro anónimo, no ambos.");
        }
        if (c.tipoDonacion() != null && !catalogo.existeTipoDonacion(c.tipoDonacion())) {
            throw new DatosInvalidos("El campo tipo_donacion no es un tipo válido.");
        }
        if (c.grupoSanguineo() == null || !catalogo.existeGrupoSanguineo(c.grupoSanguineo())) {
            throw new DatosInvalidos("El campo grupo_sanguineo es obligatorio y debe ser un grupo válido.");
        }
        if (c.componentes() == null || c.componentes().isEmpty() || c.componentes().size() > 3) {
            throw new DatosInvalidos("Declara entre uno y tres componentes.");
        }
        var vistos = new HashSet<String>();
        List<Componente> resultado = new ArrayList<>();
        for (ComponenteSolicitado s : c.componentes()) {
            Componente componente = catalogo.componente(s.componente())
                    .orElseThrow(() -> new DatosInvalidos("El campo componente no es un componente válido."));
            if (!vistos.add(componente.codigo())) {
                throw new DatosInvalidos("Cada componente se declara una sola vez.");
            }
            if (s.volumenMl() != null && (s.volumenMl() <= 0 || s.volumenMl() > 1000)) {
                throw new DatosInvalidos("El campo volumen_ml está fuera de rango.");
            }
            resultado.add(componente);
        }
        return resultado;
    }
}
