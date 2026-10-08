package co.gov.redvital.donacion.aplicacion.eventos;

/**
 * Temas del catálogo de eventos (DD V3.0, Tabla 27). Los nombres son parte
 * del contrato AsyncAPI: redvital.&lt;contexto&gt;.&lt;evento&gt;.v&lt;mayor&gt;.
 */
public final class Temas {

    /** EV-01, productor: Donación. */
    public static final String DONACION_COMPLETADA = "redvital.donacion.donation-completed.v1";
    /** EV-02, productor: Donación. */
    public static final String AVISO_SOLICITADO = "redvital.donacion.notice-requested.v1";
    /** EV-03, productor: Campañas; Donación lo consume. */
    public static final String CAMPANIA_PUBLICADA = "redvital.campanias.campaign-published.v1";

    /** Grupo consumidor de Donación (DD, sección 14). */
    public static final String GRUPO_DONACION = "donacion";

    private Temas() {
    }
}
