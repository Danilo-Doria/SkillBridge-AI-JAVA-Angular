-- HU-104 WP-E: usuario y schema propios de payment-service (FR-010).
-- Lo ejecuta un superusuario UNA vez sobre la BD compartida. La contraseña NO va aquí:
--   psql -v payment_password="$PAYMENT_DATABASE_PASSWORD" -f init-payment-schema.sql
-- Flyway del servicio crea las tablas dentro del schema; este usuario no tiene permisos en public.

SELECT format('CREATE ROLE payment_svc LOGIN PASSWORD %L', :'payment_password')
WHERE NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'payment_svc')
\gexec

CREATE SCHEMA IF NOT EXISTS payment AUTHORIZATION payment_svc;

-- Aislamiento: nada sobre las tablas del monolito (schema public); el rol solo es dueño de "payment".
ALTER ROLE payment_svc SET search_path = payment;
