package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.EventosProcesadosPort;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class EventosProcesadosJdbc implements EventosProcesadosPort {

    private final JdbcClient jdbc;

    EventosProcesadosJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public boolean registrarSiNuevo(String grupoConsumidor, UUID eventoId, String tipo, Instant ahora, Instant expiraEn) {
        return jdbc.sql("""
                INSERT INTO evento_procesado (grupo_consumidor, evento_id, tipo, procesado_en, expira_en)
                VALUES (:grupo, :evento, :tipo, :ahora, :expira)
                ON CONFLICT (grupo_consumidor, evento_id) DO NOTHING""")
                .param("grupo", grupoConsumidor)
                .param("evento", eventoId)
                .param("tipo", tipo)
                .param("ahora", Sql.ts(ahora))
                .param("expira", Sql.ts(expiraEn))
                .update() == 1;
    }

    @Override
    public int depurar(Instant ahora) {
        return jdbc.sql("DELETE FROM evento_procesado WHERE expira_en < :ahora")
                .param("ahora", Sql.ts(ahora)).update();
    }
}
