package co.gov.redvital.donacion.aplicacion.casodeuso;

import co.gov.redvital.donacion.aplicacion.comun.Auditor;
import co.gov.redvital.donacion.aplicacion.comun.ContextoPeticion;
import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioConsentimientos;
import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import co.gov.redvital.donacion.dominio.excepcion.DatosInvalidos;
import co.gov.redvital.donacion.dominio.excepcion.ReglaNegocio;
import co.gov.redvital.donacion.dominio.modelo.Actor;
import co.gov.redvital.donacion.dominio.modelo.Consentimiento;
import co.gov.redvital.donacion.dominio.modelo.Donante;
import co.gov.redvital.donacion.dominio.modelo.Finalidad;
import java.time.Clock;
import java.util.List;

/**
 * Ley 1581. Una finalidad por petición; solo anexado. Revocar los avisos de
 * campañas detiene los avisos siguientes. La revocación del tratamiento de
 * datos implica suprimir el perfil, operación que no tiene requerimiento en
 * esta versión y por eso no se admite aquí.
 */
public final class GestionarConsentimientos {

    private final ConsultasDelDonante consultas;
    private final RepositorioConsentimientos consentimientos;
    private final UnidadDeTrabajo unidadDeTrabajo;
    private final Auditor auditor;
    private final Clock reloj;

    public GestionarConsentimientos(ConsultasDelDonante consultas, RepositorioConsentimientos consentimientos,
                                    UnidadDeTrabajo unidadDeTrabajo, Auditor auditor, Clock reloj) {
        this.consultas = consultas;
        this.consentimientos = consentimientos;
        this.unidadDeTrabajo = unidadDeTrabajo;
        this.auditor = auditor;
        this.reloj = reloj;
    }

    public List<Consentimiento> vigentes(Actor actor) {
        return consentimientos.vigentes(consultas.donante(actor).id());
    }

    public List<Consentimiento> registrar(Actor actor, String finalidadCodigo, boolean otorgado,
                                          String versionAviso, ContextoPeticion ctx) {
        Finalidad finalidad = Finalidad.desdeCodigo(finalidadCodigo)
                .orElseThrow(() -> new DatosInvalidos("El campo finalidad no es una finalidad válida."));
        String version = Validaciones.requerido(versionAviso, "version_aviso", 20);
        if (finalidad == Finalidad.TRATAMIENTO_DATOS && !otorgado) {
            throw new ReglaNegocio("supresion-no-disponible",
                    "Para revocar el tratamiento de datos y suprimir tu perfil, comunícate con el banco de sangre.");
        }
        return unidadDeTrabajo.ejecutar(() -> {
            Donante d = consultas.donante(actor);
            consentimientos.insertar(d.id(), finalidad, otorgado, version, reloj.instant());
            auditor.permitido(actor, otorgado ? "otorgar_consentimiento" : "revocar_consentimiento",
                    "consentimiento", d.id(), ctx);
            return consentimientos.vigentes(d.id());
        });
    }
}
