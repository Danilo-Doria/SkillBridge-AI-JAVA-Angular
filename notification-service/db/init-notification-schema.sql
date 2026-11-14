-- HU-104 WP-D: usuario y schema propios de notification-service (FR-010).
-- Lo ejecuta un superusuario UNA vez sobre la BD compartida (p. ej. como script de
-- /docker-entrypoint-initdb.d o a mano). La contraseña NO va aquí: se fija con psql -v
--   psql -v notification_password="$NOTIFICATION_DB_PASSWORD" -f init-notification-schema.sql
-- Flyway del servicio crea las tablas dentro del schema; este usuario no tiene permisos en public.

SELECT format('CREATE ROLE notification_svc LOGIN PASSWORD %L', :'notification_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'notification_svc')
\gexec

CREATE SCHEMA IF NOT EXISTS notification AUTHORIZATION notification_svc;

-- Aislamiento: no se concede nada sobre las tablas del monolito (schema public); el rol solo
-- es dueño de "notification". En PG15+ PUBLIC ya no puede crear objetos en public.
ALTER ROLE notification_svc SET search_path = notification;
