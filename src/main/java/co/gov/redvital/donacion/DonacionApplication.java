package co.gov.redvital.donacion;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/** Servicio de Donación de RedVital: M1 Gestión de Donantes, M3 Trazabilidad y M4 Inventario. */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DonacionApplication {

    public static void main(String[] args) {
        SpringApplication.run(DonacionApplication.class, args);
    }
}
