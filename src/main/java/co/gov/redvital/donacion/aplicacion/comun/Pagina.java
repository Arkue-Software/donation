package co.gov.redvital.donacion.aplicacion.comun;

import java.util.List;
import java.util.function.Function;

public record Pagina<T>(List<T> elementos, long total, int pagina, int tamano) {

    public <R> Pagina<R> map(Function<T, R> transformar) {
        return new Pagina<>(elementos.stream().map(transformar).toList(), total, pagina, tamano);
    }
}
