package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.BloqueoConsultivoPort;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/** pg_try_advisory_xact_lock: se libera solo al terminar la transacción. */
@Component
class BloqueoConsultivoPostgres implements BloqueoConsultivoPort {

    private final JdbcClient jdbc;

    BloqueoConsultivoPostgres(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean intentar(long clave) {
        return Boolean.TRUE.equals(jdbc.sql("SELECT pg_try_advisory_xact_lock(:clave)")
                .param("clave", clave).query(Boolean.class).single());
    }
}
