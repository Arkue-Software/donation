package co.gov.redvital.donacion.aplicacion.puerto.salida;

import java.util.function.Supplier;

/** Límite transaccional, sin que los casos de uso dependan de Spring. */
public interface UnidadDeTrabajo {

    <T> T ejecutar(Supplier<T> trabajo);

    /**
     * Transacción independiente de la que esté en curso. La usan las
     * denegaciones: deben quedar en la bitácora aunque la operación se revierta.
     */
    <T> T ejecutarEnNueva(Supplier<T> trabajo);

    default void ejecutar(Runnable trabajo) {
        ejecutar(() -> {
            trabajo.run();
            return null;
        });
    }

    default void ejecutarEnNueva(Runnable trabajo) {
        ejecutarEnNueva(() -> {
            trabajo.run();
            return null;
        });
    }
}
