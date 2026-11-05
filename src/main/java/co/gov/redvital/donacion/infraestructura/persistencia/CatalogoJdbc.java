package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.CatalogoPort;
import co.gov.redvital.donacion.dominio.modelo.ActorTipo;
import co.gov.redvital.donacion.dominio.modelo.CodigoEstado;
import co.gov.redvital.donacion.dominio.modelo.Componente;
import co.gov.redvital.donacion.dominio.modelo.MatrizTransiciones;
import co.gov.redvital.donacion.dominio.modelo.Reconocimiento;
import co.gov.redvital.donacion.dominio.modelo.Transicion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Component;

/**
 * Catálogos cerrados. Se cargan una vez y se conservan en memoria: cambian
 * solo con una migración revisada, que implica reiniciar el servicio.
 */
@Component
class CatalogoJdbc implements CatalogoPort {

    private record Instantanea(Map<String, Componente> componentes, Set<String> grupos, Set<String> tipos,
                               Set<String> observaciones, MatrizTransiciones matriz,
                               List<Reconocimiento> reconocimientos) {
    }

    private final JdbcClient jdbc;
    private volatile Instantanea instantanea;

    CatalogoJdbc(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    private Instantanea datos() {
        Instantanea actual = instantanea;
        if (actual == null) {
            synchronized (this) {
                if (instantanea == null) {
                    instantanea = cargar();
                }
                actual = instantanea;
            }
        }
        return actual;
    }

    private Instantanea cargar() {
        Map<String, Componente> componentes = jdbc.sql("SELECT id, codigo, nombre, vida_util_dias FROM componente")
                .query((rs, n) -> new Componente(Sql.uuid(rs, "id"), rs.getString("codigo"), rs.getString("nombre"),
                        rs.getInt("vida_util_dias")))
                .list().stream().collect(Collectors.toUnmodifiableMap(Componente::codigo, Function.identity()));
        Set<String> grupos = Set.copyOf(jdbc.sql("SELECT codigo FROM grupo_sanguineo").query(String.class).list());
        Set<String> tipos = Set.copyOf(jdbc.sql("SELECT codigo FROM tipo_donacion WHERE vigente").query(String.class).list());
        Set<String> observaciones = Set.copyOf(
                jdbc.sql("SELECT codigo FROM observacion_operativa WHERE vigente").query(String.class).list());
        List<Transicion> transiciones = jdbc.sql("""
                SELECT t.numero, o.codigo AS origen, d.codigo AS destino, t.actor_tipo
                FROM transicion_valida t
                JOIN estado_unidad o ON o.id = t.estado_origen_id
                JOIN estado_unidad d ON d.id = t.estado_destino_id
                ORDER BY t.numero""")
                .query((rs, n) -> new Transicion(rs.getInt("numero"), CodigoEstado.desdeCodigo(rs.getString("origen")),
                        CodigoEstado.desdeCodigo(rs.getString("destino")), ActorTipo.desdeCodigo(rs.getString("actor_tipo"))))
                .list();
        List<Reconocimiento> reconocimientos = jdbc.sql("""
                SELECT id, codigo, nombre, descripcion, criterio_donaciones, orden
                FROM reconocimiento WHERE vigente ORDER BY orden""")
                .query((rs, n) -> new Reconocimiento(Sql.uuid(rs, "id"), rs.getString("codigo"), rs.getString("nombre"),
                        rs.getString("descripcion"), rs.getInt("criterio_donaciones"), rs.getInt("orden")))
                .list();
        return new Instantanea(componentes, grupos, tipos, observaciones, new MatrizTransiciones(transiciones),
                List.copyOf(reconocimientos));
    }

    @Override
    public Optional<Componente> componente(String codigo) {
        return Optional.ofNullable(codigo).map(datos().componentes()::get);
    }

    @Override
    public boolean existeGrupoSanguineo(String codigo) {
        return codigo != null && datos().grupos().contains(codigo);
    }

    @Override
    public boolean existeTipoDonacion(String codigo) {
        return codigo != null && datos().tipos().contains(codigo);
    }

    @Override
    public boolean existeObservacion(String codigo) {
        return codigo != null && datos().observaciones().contains(codigo);
    }

    @Override
    public MatrizTransiciones matriz() {
        return datos().matriz();
    }

    @Override
    public List<Reconocimiento> reconocimientos() {
        return datos().reconocimientos();
    }
}
