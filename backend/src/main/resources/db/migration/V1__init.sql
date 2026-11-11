CREATE TABLE app_users (
    id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    email VARCHAR(180) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    role VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE offerings (
    id UUID PRIMARY KEY,
    title VARCHAR(160) NOT NULL,
    description VARCHAR(1200) NOT NULL,
    category VARCHAR(80) NOT NULL,
    price NUMERIC(12,2) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE bookings (
    id UUID PRIMARY KEY,
    offering_id UUID NOT NULL REFERENCES offerings(id),
    customer_id UUID NOT NULL REFERENCES app_users(id),
    scheduled_at TIMESTAMPTZ NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_offerings_active ON offerings(active);
CREATE INDEX idx_bookings_customer ON bookings(customer_id);
CREATE INDEX idx_bookings_offering ON bookings(offering_id);

INSERT INTO offerings (id, title, description, category, price, active) VALUES
('11111111-1111-1111-1111-111111111111', 'Mentoría Java Backend', 'Sesión de arquitectura, Spring Boot y APIs REST.', 'BACKEND', 85000, true),
('22222222-2222-2222-2222-222222222222', 'Mentoría Angular', 'Sesión práctica de Angular, RxJS y arquitectura frontend.', 'FRONTEND', 75000, true),
('33333333-3333-3333-3333-333333333333', 'Diseño de Arquitectura Cloud', 'Revisión de una solución distribuida con Docker y cloud.', 'CLOUD', 120000, true),
('44444444-4444-4444-4444-444444444444', 'Mentoría React & Next.js', 'Desarrollo de aplicaciones modernas con Server Components y Next.js.', 'FRONTEND', 80000, true),
('55555555-5555-5555-5555-555555555555', 'Mentoría Node.js & NestJS', 'Microservicios escalables con TypeScript, colas y NestJS.', 'BACKEND', 78000, true),
('66666666-6666-6666-6666-666666666666', 'Mentoría Python FastAPI & Django', 'Diseño de APIs asíncronas con FastAPI y desarrollo web con Django.', 'BACKEND', 82000, true),
('77777777-7777-7777-7777-777777777777', 'Kubernetes & Contenedores en Producción', 'Orquestación de microservicios con Docker, Helm y Kubernetes en producción.', 'CLOUD', 130000, true),
('88888888-8888-8888-8888-888888888888', 'Mentoría Vue 3 & Pinia', 'Desarrollo reactivo con Composition API, TypeScript y Pinia.', 'FRONTEND', 70000, true),
('99999999-9999-9999-9999-999999999999', 'Arquitectura AWS Cloud & Terraform', 'Infraestructura como código en AWS con Terraform y buenas prácticas.', 'CLOUD', 115000, true),
('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'Mentoría Go (Golang) Microservicios', 'Construcción de servicios concurrentes y de alto rendimiento con Go.', 'BACKEND', 95000, true),
('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'Mentoría .NET Core & C#', 'APIs REST con .NET 8, Clean Architecture y Entity Framework.', 'BACKEND', 88000, true),
('cccccccc-cccc-cccc-cccc-cccccccccccc', 'Desarrollo Móvil con Flutter', 'Aplicaciones móviles multiplataforma en iOS y Android con Flutter y Dart.', 'MOBILE', 85000, true),
('dddddddd-dddd-dddd-dddd-dddddddddddd', 'Desarrollo Móvil con React Native', 'Construcción de apps móviles nativas con React Native y Expo.', 'MOBILE', 80000, true),
('eeeeeeee-eeee-eeee-eeee-eeeeeeeeeeee', 'Desarrollo Android Nativo con Kotlin', 'Jetpack Compose, coroutines, arquitectura MVVM y mejores prácticas en Android.', 'MOBILE', 90000, true),
('ffffffff-ffff-ffff-ffff-ffffffffffff', 'Desarrollo iOS con Swift & SwiftUI', 'Aplicaciones nativas para iOS con SwiftUI y arquitectura modular.', 'MOBILE', 95000, true),
('12121212-1212-1212-1212-121212121212', 'Optimización y Tuning de PostgreSQL', 'Optimización de consultas complejas, índices avanzados y planes EXPLAIN.', 'BACKEND', 92000, true),
('13131313-1313-1313-1313-131313131313', 'CI/CD y DevOps con GitHub Actions', 'Automatización de pruebas, pipelines de despliegue y flujos GitOps.', 'CLOUD', 75000, true),
('14141414-1414-1414-1414-141414141414', 'Seguridad de Aplicaciones Web (OWASP)', 'Auditoría y mitigación de vulnerabilidades OWASP Top 10 y hardening web.', 'BACKEND', 105000, true),
('15151515-1515-1515-1515-151515151515', 'Machine Learning & IA con Python', 'Modelos predictivos con Scikit-Learn, procesamiento de datos y evaluación de modelos.', 'CLOUD', 110000, true),
('16161616-1616-1616-1616-161616161616', 'Inteligencia Artificial Generativa & RAG', 'Integración de modelos LLM con embeddings vectoriales, RAG y Spring AI.', 'BACKEND', 125000, true);
