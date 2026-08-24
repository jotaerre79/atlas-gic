# Atlas GIC

Foundation tecnica inicial del bounded context GIC - Gestion Integral de Identidad.

## Alcance

Esta foundation habilita estructura tecnica, no funcionalidad completa.

Incluye:

- Java 21 LTS como target de compilacion.
- Spring Boot 4.1.0.
- Maven como build tool inicial.
- Arquitectura hexagonal pragmatica.
- Modulos base: `identity`, `roles`, `relationships`, `deduplication`, `integration`.
- Configuracion externalizada.
- Health mediante Spring Boot Actuator.
- Logging baseline.
- Tenant context.
- PostgreSQL/Flyway foundation.
- RLS deny-by-default.
- Seguridad baseline.
- Tests unitarios, de arquitectura e integracion.
- CI basico con GitHub Actions.

## Fuera de alcance

- RegisterPerson completo.
- CRUD funcional.
- OpenAPI definitivo.
- Kafka, RabbitMQ o Redis.
- Kubernetes.
- Integraciones reales con IAM, ERP, SIFEN o SEPRELAD.
- Superclase universal `Entity`.

## Build

```bash
mvn verify
```

El entorno local puede usar un JDK superior, pero Maven compila con `release=21`.

## Tenancy

La estrategia inicial aceptada por arquitectura es PostgreSQL shared schema con `tenant_id` y Row Level Security.

RLS debe negar acceso cuando no exista tenant context valido. El acceso de plataforma debe ser explicito y auditable; no se debe desactivar RLS globalmente para administracion.

El acceso de plataforma nunca se deriva de headers controlados por el cliente. Los privilegios administrativos deben provenir de un principal autenticado y autorizado mediante IAM/Spring Security. En esta foundation, la authority explicita es `ATLAS_PLATFORM_ADMIN`.

El header temporal `X-Tenant-Id` solo selecciona el tenant solicitado mientras no exista el proveedor IAM definitivo. No constituye autoridad de autorizacion: el tenant solicitado se acepta solo si el principal autenticado tiene una authority `TENANT_<tenantId>`. Si no hay tenant context autorizado, las operaciones tenant-scoped quedan en deny-by-default y RLS actua como defensa en profundidad.

## Referencias de arquitectura

- `atlas-architecture/docs/03-adrs/ADR-015-gic-identidad-de-negocio-tenant-scoped.md`
- `atlas-architecture/docs/03-adrs/ADR-016-gic-ownership-de-identidad-pii-contactos-y-direcciones-principales.md`
- `atlas-architecture/docs/03-adrs/ADR-017-gic-separacion-persona-iam-principal.md`
- `atlas-architecture/docs/03-adrs/ADR-018-gic-merge-explicito-auditable-y-no-automatico.md`
- `atlas-architecture/docs/03-adrs/ADR-019-gic-tenancy-inicial-postgresql-shared-schema-tenant-id-rls.md`
