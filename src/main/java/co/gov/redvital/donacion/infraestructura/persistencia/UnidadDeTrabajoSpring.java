package co.gov.redvital.donacion.infraestructura.persistencia;

import co.gov.redvital.donacion.aplicacion.puerto.salida.UnidadDeTrabajo;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Component
class UnidadDeTrabajoSpring implements UnidadDeTrabajo {

    private final TransactionTemplate requerida;
    private final TransactionTemplate nueva;

    UnidadDeTrabajoSpring(PlatformTransactionManager transacciones) {
        this.requerida = new TransactionTemplate(transacciones);
        this.nueva = new TransactionTemplate(transacciones);
        this.nueva.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return requerida.execute(estado -> trabajo.get());
    }

    @Override
    public <T> T ejecutarEnNueva(Supplier<T> trabajo) {
        return nueva.execute(estado -> trabajo.get());
    }
}
