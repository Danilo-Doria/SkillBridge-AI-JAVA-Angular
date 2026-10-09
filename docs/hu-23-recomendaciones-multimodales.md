# HU-23 Recomendaciones multimodales

Backend acepta texto, voz e imagen mediante Gemini. Los endpoints son `POST /api/ai/recommendations`, `/voice` y `/image`; los dos últimos reciben `multipart/form-data` con `file`.

Audio permitido: WAV, MP3, MP4 y WEBM hasta 10 MB. Imagen permitida: JPEG, PNG y WEBP hasta 5 MB. Los archivos se procesan en memoria y no se almacenan. El resultado contiene `recommendationId`, `inputType`, explicación y recomendaciones. Se publican eventos solicitada y generada con correlation ID.
