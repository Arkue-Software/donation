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
| `infraestructura` | Adaptadores: JDBC (`JdbcClient`), Kafka (bandeja de salida y consumidor), cliente de Identidad, seguridad JWT, procesos programados y la composición del núcleo |
| `web` | Controladores REST en snake_case y problemas RFC 9457 `{tipo, titulo, estado, detalle, correlacion_id}` |

Decisiones relevantes:

- **El esquema no lo migra el servicio.** Vive en `BDs_RedVital/databases/donacion` y lo aplica Flyway como
  `donacion_propietario`. El servicio se conecta como `donacion_servicio`, que solo puede actualizar las columnas
  que el dominio cambia.
- **Donación nunca llama a Campañas.** La campaña de una donación se confirma con la proyección local
  `campania_publicada`, que alimenta el evento `redvital.campanias.campaign-published.v1`. El registro de la
  donación publica `redvital.donacion.donation-completed.v1` por la bandeja de salida transaccional. Contratos en
  [`contratos/asyncapi.yaml`](contratos/asyncapi.yaml).
- **La única consulta síncrona es a Identidad**, para crear la cuenta del donante (`POST /v1/donantes`).
- **Alcance de esta versión:** donante (U2), operador (U3) y administrador de banco (U4). Coordinador territorial,
  administrador nacional y auditor reciben 403 hasta que exista el Servicio Institucional, que resuelve qué
  instituciones hay en cada territorio. Transferencias y convocatorias quedan diferidas por la misma razón.

## Configuración

| Variable | Uso | Por defecto |
|---|---|---|
| `DB_HOST`, `DB_PORT`, `DB_NAME`, `DB_SSLMODE` | PostgreSQL | `localhost`, `5432`, `db_donacion`, `prefer` |
| `KAFKA_SERVIDORES`, `KAFKA_PROTOCOLO` | Broker | `localhost:9092`, `SASL_PLAINTEXT` |
| `JWT_JWKS_URI`, `JWT_EMISOR`, `JWT_AUDIENCIA` | Validación del token | JWKS de `identity-service` |
| `IDENTIDAD_URL` | Operaciones internas de Identidad | `http://identity-service:8080` |
| `PROCESOS_HABILITADOS`, `AVISOS_HABILITADOS` | Procesos programados y avisos de campaña | `true` |

Secretos, como archivos en `/run/secrets` (cada archivo se lee como la propiedad de su mismo nombre):
`donacion_servicio` (contraseña de la base), `kafka_donacion` (SCRAM), `donacion_secreto_identidad` (credencial de
servicio ante Identidad) y `donacion_clave_hmac` (clave del resumen del documento; estable y respaldada).

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
