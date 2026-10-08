package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.aplicacion.comun.Pagina;
import co.gov.redvital.donacion.dominio.modelo.CodigoEstado;
import co.gov.redvital.donacion.dominio.modelo.Unidad;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioUnidades {

    void insertar(Unidad unidad, Instant ahora);

    /** Escribe solo las columnas que el rol de servicio puede actualizar. */
    void actualizarEstado(Unidad unidad, Instant ahora);

    Optional<Unidad> buscar(UUID id);

    /** Bloquea la fila: dos transiciones simultáneas sobre la misma unidad se serializan. */
    Optional<Unidad> buscarParaActualizar(UUID id);

    List<Unidad> deDonacion(UUID donacionId);

    List<Unidad> deDonacionParaActualizar(UUID donacionId);

    Pagina<Unidad> listar(UUID institucionId, CodigoEstado estado, String componente, int pagina, int tamano);

    /** Unidades que el proceso de vencimiento debe retirar, saltando las bloqueadas. */
    List<Unidad> vencidasParaActualizar(LocalDate hoy, int limite);

    List<Unidad> disponiblesDe(UUID institucionId, LocalDate hoy);
}
