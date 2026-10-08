package co.gov.redvital.donacion.dominio.servicio;

import co.gov.redvital.donacion.dominio.modelo.Reconocimiento;
import java.util.Collection;
import java.util.List;
import java.util.Set;

/** RF-11: un reconocimiento se otorga al alcanzar su número de donaciones y nunca se retira. */
public final class OtorgadorReconocimientos {

    public List<Reconocimiento> nuevos(int totalDonaciones, Collection<Reconocimiento> catalogo,
                                       Set<String> yaOtorgados) {
        return catalogo.stream()
                .filter(r -> r.criterioDonaciones() <= totalDonaciones)
                .filter(r -> !yaOtorgados.contains(r.codigo()))
                .toList();
    }
}
