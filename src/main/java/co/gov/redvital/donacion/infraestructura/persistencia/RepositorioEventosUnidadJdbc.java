package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.RepositorioEventosUnidad;
import co.gov.redvital.donacion.dominio.modelo.ActorTipo;
import co.gov.redvital.donacion.dominio.modelo.CodigoEstado;
import co.gov.redvital.donacion.dominio.modelo.EventoUnidad;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class RepositorioEventosUnidadJdbc implements RepositorioEventosUnidad {

    private final JdbcClient jdbc;

    RepositorioEventosUnidadJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void insertar(EventoUnidad e) {
        jdbc.sql("""
                INSERT INTO evento_unidad (id, unidad_id, estado_anterior_id, estado_nuevo_id, actor_tipo, actor_id,
                                           institucion_id, observacion_id, correlacion_id, ocurrido_en)
                VALUES (:id, :unidad,
                        (SELECT id FROM estado_unidad WHERE codigo = :anterior),
                        (SELECT id FROM estado_unidad WHERE codigo = :nuevo),
                        :actorTipo, :actorId, :institucion,
                        (SELECT id FROM observacion_operativa WHERE codigo = :observacion),
                        :correlacion, :ocurrido)""")
                .param("id", e.id())
                .param("unidad", e.unidadId())
                .param("anterior", e.estadoAnterior() == null ? null : e.estadoAnterior().codigo())
                .param("nuevo", e.estadoNuevo().codigo())
                .param("actorTipo", e.actorTipo().codigo())
                .param("actorId", e.actorId())
                .param("institucion", e.institucionId())
                .param("observacion", e.observacion())
                .param("correlacion", e.correlacionId())
                .param("ocurrido", Sql.ts(e.ocurridoEn()))
                .update();
    }

    @Override
    public List<EventoUnidad> deUnidad(UUID unidadId) {
        return jdbc.sql("""
                SELECT ev.id, ev.unidad_id, a.codigo AS anterior, n.codigo AS nuevo, ev.actor_tipo, ev.actor_id,
                       ev.institucion_id, o.codigo AS observacion, ev.correlacion_id, ev.ocurrido_en
                FROM evento_unidad ev
                LEFT JOIN estado_unidad a ON a.id = ev.estado_anterior_id
                JOIN estado_unidad n ON n.id = ev.estado_nuevo_id
                LEFT JOIN observacion_operativa o ON o.id = ev.observacion_id
                WHERE ev.unidad_id = :unidad
                ORDER BY ev.ocurrido_en, ev.id""")
                .param("unidad", unidadId)
                .query((rs, n) -> new EventoUnidad(Sql.uuid(rs, "id"), Sql.uuid(rs, "unidad_id"),
                        rs.getString("anterior") == null ? null : CodigoEstado.desdeCodigo(rs.getString("anterior")),
                        CodigoEstado.desdeCodigo(rs.getString("nuevo")), ActorTipo.desdeCodigo(rs.getString("actor_tipo")),
                        rs.getString("actor_id"), Sql.uuid(rs, "institucion_id"), rs.getString("observacion"),
                        rs.getString("correlacion_id"), Sql.instante(rs, "ocurrido_en")))
                .list();
    }
}
