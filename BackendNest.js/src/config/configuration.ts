/**
 * Configuración tipada del servicio de mentorías.
 *
 * Ninguna credencial de proveedor es obligatoria: la ausencia de una sola
 * impide únicamente ese proveedor (FR-019) y nunca el arranque del servicio.
 */

export interface ZoomConfig {
  clientId?: string;
  clientSecret?: string;
  accountId?: string;
  sdkKey?: string;
  sdkSecret?: string;
  apiBaseUrl: string;
  oauthUrl: string;
  timeoutMs: number;
}

export interface GoogleConfig {
  clientEmail?: string;
  privateKey?: string;
  calendarId?: string;
  apiBaseUrl: string;
  oauthUrl: string;
  timeoutMs: number;
}

export interface DiscordConfig {
  botToken?: string;
  guildId?: string;
  categoryId?: string;
  apiBaseUrl: string;
  timeoutMs: number;
}

export interface JwtConfig {
  publicKey?: string;
  issuer: string;
}

export interface LiveRoomConfig {
  encryptionKey?: string;
  providerTimeoutMs: number;
  transactionTimeoutMs: number;
}

export interface KafkaConfig {
  enabled: boolean;
  brokers: string[];
  topic: string;
  clientId: string;
  pollIntervalMs: number;
  batchSize: number;
  maxAttempts: number;
  backoffBaseMs: number;
  backoffMaxMs: number;
  lockMs: number;
  requestTimeoutMs: number;
  nodeEnv: string;
}

export interface AppConfiguration {
  nodeEnv: string;
  port: number;
  jwt: JwtConfig;
  liveRoom: LiveRoomConfig;
  kafka: KafkaConfig;
  zoom: ZoomConfig;
  google: GoogleConfig;
  discord: DiscordConfig;
}

function env(key: string, fallback = ''): string {
  const value = process.env[key];
  return value === undefined || value === '' ? fallback : value;
}

function numberEnv(key: string, fallback: number): number {
  const raw = process.env[key];
  if (!raw) {
    return fallback;
  }
  const parsed = Number(raw);
  return Number.isFinite(parsed) ? parsed : fallback;
}

function booleanEnv(key: string, fallback: boolean): boolean {
  const raw = process.env[key];
  if (raw === undefined || raw === '') {
    return fallback;
  }
  return raw === 'true' || raw === '1';
}

function listEnv(key: string, fallback: string[]): string[] {
  const raw = env(key);
  if (!raw) {
    return fallback;
  }
  const values = raw
    .split(',')
    .map((value) => value.trim())
    .filter(Boolean);
  return values.length > 0 ? values : fallback;
}

export function configuration(): AppConfiguration {
  const nodeEnv = env('NODE_ENV', 'development');
  return {
    nodeEnv,
    port: numberEnv('PORT', 3000),
    jwt: {
      publicKey: env('JWT_PUBLIC_KEY') || undefined,
      issuer: env('JWT_ISSUER', 'skillbridge'),
    },
    liveRoom: {
      encryptionKey: env('LIVE_ROOM_ENCRYPTION_KEY') || undefined,
      providerTimeoutMs: numberEnv('LIVE_PROVIDER_TIMEOUT_MS', 4000),
      transactionTimeoutMs: numberEnv('LIVE_ROOM_TRANSACTION_TIMEOUT_MS', 15000),
    },
    kafka: {
      enabled: booleanEnv('KAFKA_ENABLED', nodeEnv === 'production'),
      brokers: listEnv(
        'KAFKA_BOOTSTRAP_SERVERS',
        nodeEnv === 'production' ? [] : ['localhost:9092'],
      ),
      topic: env('KAFKA_TOPIC_BOOKING_EVENTS', 'booking-events'),
      clientId: env('KAFKA_CLIENT_ID', 'skillbridge-mentorship'),
      pollIntervalMs: numberEnv('KAFKA_OUTBOX_POLL_INTERVAL_MS', 1000),
      batchSize: numberEnv('KAFKA_OUTBOX_BATCH_SIZE', 50),
      maxAttempts: numberEnv('KAFKA_OUTBOX_MAX_ATTEMPTS', 10),
      backoffBaseMs: numberEnv('KAFKA_OUTBOX_BACKOFF_BASE_MS', 500),
      backoffMaxMs: numberEnv('KAFKA_OUTBOX_BACKOFF_MAX_MS', 60_000),
      lockMs: numberEnv('KAFKA_OUTBOX_LOCK_MS', 30_000),
      requestTimeoutMs: numberEnv(
        'KAFKA_PRODUCER_REQUEST_TIMEOUT_MS',
        10_000,
      ),
      nodeEnv,
    },
    zoom: {
      clientId: env('ZOOM_CLIENT_ID') || undefined,
      clientSecret: env('ZOOM_CLIENT_SECRET') || undefined,
      accountId: env('ZOOM_ACCOUNT_ID') || undefined,
      sdkKey: env('ZOOM_SDK_KEY') || undefined,
      sdkSecret: env('ZOOM_SDK_SECRET') || undefined,
      apiBaseUrl: env('ZOOM_API_BASE_URL', 'https://api.zoom.us/v2'),
      oauthUrl: env('ZOOM_OAUTH_URL', 'https://zoom.us/oauth/token'),
      timeoutMs: numberEnv('ZOOM_TIMEOUT_MS', 4000),
    },
    google: {
      clientEmail: env('GOOGLE_CLIENT_EMAIL') || undefined,
      privateKey: env('GOOGLE_PRIVATE_KEY').replace(/\\n/g, '\n') || undefined,
      calendarId: env('GOOGLE_CALENDAR_ID') || undefined,
      apiBaseUrl: env(
        'GOOGLE_CALENDAR_API_BASE_URL',
        'https://www.googleapis.com/calendar/v3',
      ),
      oauthUrl: env('GOOGLE_OAUTH_URL', 'https://oauth2.googleapis.com/token'),
      timeoutMs: numberEnv('GOOGLE_TIMEOUT_MS', 4000),
    },
    discord: {
      botToken: env('DISCORD_BOT_TOKEN') || undefined,
      guildId: env('DISCORD_GUILD_ID') || undefined,
      categoryId: env('DISCORD_CATEGORY_ID') || undefined,
      apiBaseUrl: env('DISCORD_API_BASE_URL', 'https://discord.com/api/v10'),
      timeoutMs: numberEnv('DISCORD_TIMEOUT_MS', 4000),
    },
  };
}
