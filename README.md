# donation-service

Servicio de Donación de RedVital: M1 Gestión de Donantes, M3 Trazabilidad y
Ciclo de Vida de la Unidad, y M4 Inventario y Alertas (DD V3.0, sección 15.4).
Java 25 (Temurin) y Spring Boot 4.1, conforme a Herramientas V4.0.

## Arquitectura

Hexagonal. La regla de dependencias la hace cumplir `ArquitecturaTest` (ArchUnit).

| Paquete | Contenido |
|---|---|
| `dominio` | Modelo y reglas puras: máquina de 9 estados y 13 transiciones, elegibilidad, vencimiento, campaña, confirmación. Sin Spring, sin Jackson, sin JDBC |
| `aplicacion` | Un caso de uso por clase y los puertos de salida que necesitan. Sin dependencias de marcos |
| `infraestructura` | Adaptadores: JDBC (`JdbcClient`), Kafka (bandeja de salida y consumidores), seguridad JWT, procesos programados y la composición del núcleo |
| `web` | Controladores REST en snake_case y problemas RFC 9457 `{tipo, titulo, estado, detalle, correlacion_id}` |

Decisiones relevantes:

- **El esquema no lo migra el servicio.** Vive en `BDs_RedVital/databases/donacion` y lo aplica Flyway como
  `donacion_propietario`. El servicio se conecta como `donacion_servicio`, que solo puede actualizar las columnas
  que el dominio cambia.
- **Donación no llama a ningún servicio ni recibe llamadas de ellos: todo pasa por Kafka.** Contratos en
  [`contratos/asyncapi.yaml`](contratos/asyncapi.yaml).
  - Publica, por la bandeja de salida transaccional: EV-01 `donation-completed`, EV-02 `notice-requested` (con el
    correo y el nombre del destinatario, para que Notificaciones no tenga que preguntar), EV-09 `indicators-updated`
    (la fotografía de M7 de cada institución, cada minuto si cambió) y EV-10 `audit-recorded` (cada registro de su
    serie de auditoría, para la bitácora de Institucional).
  - Consume EV-03 `campaign-published`, con el que confirma la campaña de una donación (`campania_publicada`), y
    EV-07 `institution-changed`, con el que mantiene su copia de las instituciones (`institucion_proyeccion`): el
    alcance territorial de U5 y U6 y el nombre del banco en el historial del donante.
- **El token se valida con la llave pública de Identidad**, montada como secreto (`identidad_llave_publica`); no se
  consulta el JWKS en tiempo de ejecución.
- **Perfil de donante** (`POST /v1/donantes`): lo crea la persona con la sesión de la cuenta que acaba de abrir en
  Identidad (`POST /v1/cuentas-donante`). Exige 18 años cumplidos (422 `menor-de-edad`) y una cuenta sin perfil.
- **Alertas al instante:** registrar, tamizar, distribuir o desechar una unidad, y guardar los umbrales, reevalúan
  en la misma operación las alertas de esa institución; el proceso programado de cada cinco minutos recoge los
  vencimientos.
- **Alcance por rol** (DD 15.5): el operador y el administrador de banco ven su institución; el coordinador y el
  administrador nacional leen donaciones, unidades e inventario de su territorio, y el coordinador también las
  alertas; el auditor lee la trazabilidad de una unidad. Solo el operador opera unidades. Transferencias y
  convocatorias quedan diferidas.

## Configuración

| Variable | Uso | Por defecto |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_SSLMODE` | PostgreSQL | `localhost`, `5432`, `db_donacion`, `prefer` |
| `KAFKA_SERVIDORES`, `KAFKA_PROTOCOLO` | Broker | `localhost:9092`, `SASL_PLAINTEXT` |
| `JWT_LLAVE_PUBLICA`, `JWT_EMISOR`, `JWT_AUDIENCIA` | Validación del token | `/run/secrets/identidad_llave_publica` |
| `PROCESOS_HABILITADOS`, `AVISOS_HABILITADOS` | Procesos programados y avisos de campaña | `true` |

Secretos, como archivos en `/run/secrets` (cada archivo se lee como la propiedad de su mismo nombre):
`donacion_servicio` (contraseña de la base), `kafka_donacion` (SCRAM), `identidad_llave_publica` (llave pública de
firma de Identidad) y `donacion_clave_hmac` (clave del resumen del documento; estable y respaldada).

Puertos: 8080 API, 8081 gestión (`/actuator/health`, `/actuator/prometheus`).

## Pruebas

No hace falta Maven local. Desde la raíz de RedVital:

```bash
docker run --rm --network host -v /var/run/docker.sock:/var/run/docker.sock -v redvital-m2:/root/.m2 -v "$PWD":/rv -w /rv/Servicios_RedVital/services/donation maven:3.9-eclipse-temurin-25 mvn -B verify
```

- Unitarias de dominio, incluida la matriz exhaustiva de 81 pares (EC-36).
- ArchUnit.
- Integración con Testcontainers: PostgreSQL 16 con los roles y las migraciones reales del repositorio de bases,
  y Kafka 4.3.1. Cubren HTTP, permisos de base, idempotencia, eventos de entrada y salida, mensajes muertos,
  vencimiento automático y alertas.

## Imagen

`docker build -t redvital/donacion-service .` produce un JRE reducido con `jlink` sobre distroless, sin root, con
comprobación de salud contra el puerto de gestión. El despliegue está en `Despliegue_RedVital/`.

> `guia-despliegue-redvital.md` describe un despliegue anterior en EKS que no corresponde a la infraestructura
> vigente (VMs con Docker Compose).
