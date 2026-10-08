package co.gov.redvital.donacion.aplicacion.puerto.salida;

import co.gov.redvital.donacion.dominio.modelo.IntencionDonacion;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RepositorioIntenciones {

    void insertar(IntencionDonacion intencion);

    Optional<IntencionDonacion> buscar(UUID id);

    Optional<IntencionDonacion> porCodigo(String codigo);

    Optional<IntencionDonacion> porCodigoParaActualizar(String codigo);

    void marcarAtendida(UUID id, Instant ahora);

    void marcarCaducada(UUID id);
}
