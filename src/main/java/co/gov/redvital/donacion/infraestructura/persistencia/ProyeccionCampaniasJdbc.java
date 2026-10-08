package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.ProyeccionCampaniasPort;
import co.gov.redvital.donacion.dominio.modelo.CampaniaPublicada;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
class ProyeccionCampaniasJdbc implements ProyeccionCampaniasPort {

    private final JdbcClient jdbc;

    ProyeccionCampaniasJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<CampaniaPublicada> buscar(UUID campaniaId) {
        return jdbc.sql("""
                SELECT campania_id, institucion_id, nombre, sede, territorio_codigo, territorio_ruta, inicia_en,
                       termina_en, publicada_en, estado
                FROM campania_publicada WHERE campania_id = :id""")
                .param("id", campaniaId)
                .query((rs, n) -> new CampaniaPublicada(Sql.uuid(rs, "campania_id"), Sql.uuid(rs, "institucion_id"),
                        rs.getString("nombre"), rs.getString("sede"), rs.getString("territorio_codigo"),
                        rs.getString("territorio_ruta"), Sql.instante(rs, "inicia_en"), Sql.instante(rs, "termina_en"),
                        Sql.instante(rs, "publicada_en"), rs.getString("estado")))
                .optional();
    }

    @Override
    public void guardar(CampaniaPublicada c, UUID eventoId, Instant ahora) {
        jdbc.sql("""
                INSERT INTO campania_publicada (campania_id, institucion_id, nombre, sede, territorio_codigo,
                                                territorio_ruta, inicia_en, termina_en, publicada_en, estado,
                                                evento_id, actualizada_en)
                VALUES (:id, :institucion, :nombre, :sede, :codigo, :ruta, :inicia, :termina, :publicada, :estado,
                        :evento, :ahora)
                ON CONFLICT (campania_id) DO UPDATE SET
                    institucion_id = EXCLUDED.institucion_id, nombre = EXCLUDED.nombre, sede = EXCLUDED.sede,
                    territorio_codigo = EXCLUDED.territorio_codigo, territorio_ruta = EXCLUDED.territorio_ruta,
                    inicia_en = EXCLUDED.inicia_en, termina_en = EXCLUDED.termina_en,
                    publicada_en = EXCLUDED.publicada_en, estado = EXCLUDED.estado,
                    evento_id = EXCLUDED.evento_id, actualizada_en = EXCLUDED.actualizada_en
                WHERE campania_publicada.publicada_en <= EXCLUDED.publicada_en""")
                .param("id", c.campaniaId())
                .param("institucion", c.institucionId())
                .param("nombre", c.nombre())
                .param("sede", c.sede())
                .param("codigo", c.territorioCodigo())
                .param("ruta", c.territorioRuta())
                .param("inicia", Sql.ts(c.iniciaEn()))
                .param("termina", Sql.ts(c.terminaEn()))
                .param("publicada", Sql.ts(c.publicadaEn()))
                .param("estado", c.estado())
                .param("evento", eventoId)
                .param("ahora", Sql.ts(ahora))
                .update();
    }
}
