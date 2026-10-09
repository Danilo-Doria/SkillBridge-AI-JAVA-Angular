# HU-23 Recomendaciones multimodales

Backend acepta texto, voz e imagen mediante Gemini. Los endpoints son `POST /api/ai/recommendations`, `/voice` y `/image`; los dos últimos reciben `multipart/form-data` con `file`.

Audio permitido: WAV, MP3, MP4 y WEBM hasta 10 MB. Imagen permitida: JPEG, PNG y WEBP hasta 5 MB. 
Los archivos se procesan en memoria y no se almacenan. 
El resultado contiene `recommendationId`, `inputType`, explicación y recomendaciones. 
Se publican eventos solicitada y generada con correlation ID.

## QA

| Caso | Cobertura |
|---|---|
| Texto | `POST /api/ai/recommendations` produce resultado con `recommendationId`. |
| Voz | `POST /api/ai/recommendations/voice` valida audio y convierte a contexto Gemini. |
| Imagen | `POST /api/ai/recommendations/image` valida imagen y convierte a contexto Gemini. |
| Archivo inválido | `MediaFileValidatorTest` rechaza vacío y MIME no permitido. |
| Analytics | Se publican `RecommendationRequested` y `RecommendationGenerated` con `inputType`. |

## Verificación

- `mvn -q -DskipTests compile`: correcto.
- `mvn -q -DskipTests test-compile`: correcto.
- `npm run build`: correcto.
- La suite Maven completa depende de Docker para Testcontainers y de adjuntar el agente Mockito; el entorno local 
- actual no permite ambos.

## Variables de entorno

- `GEMINI_API_KEY`: credencial exclusiva del backend.
- `GEMINI_MODEL`: modelo Gemini multimodal.
- `AI_MEDIA_MAX_AUDIO_BYTES`: límite de audio; por defecto 10485760.
- `AI_MEDIA_MAX_IMAGE_BYTES`: límite de imagen; por defecto 5242880.
