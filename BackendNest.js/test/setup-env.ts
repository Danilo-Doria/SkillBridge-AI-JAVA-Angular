import { getTestKeys } from './helpers/test-keys.js';

// El publicador de outbox no debe conectarse a Kafka en la suite unitaria.
if (process.env.KAFKA_ENABLED === undefined) {
  process.env.KAFKA_ENABLED = 'false';
}

// Debe ejecutarse antes de importar los módulos Nest para que ConfigModule
// cargue la clave pública de prueba.
getTestKeys();
