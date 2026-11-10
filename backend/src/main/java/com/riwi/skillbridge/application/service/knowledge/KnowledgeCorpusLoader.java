package com.riwi.skillbridge.application.service.knowledge;

import com.riwi.skillbridge.domain.knowledge.KnowledgeChunk;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.core.io.support.ResourcePatternResolver;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * Cargador del corpus de conocimiento de la plataforma (HU-26, FR-004, FR-014).
 * <p>
 * Lee exclusivamente archivos Markdown bajo {@code knowledge/*.md}, parsea el encabezado
 * YAML frontmatter (sourceId, type, title, visibility) e indexa solo los archivos con
 * {@code visibility: public}, dividiendo el contenido por sección de idioma (ES y EN).
 */
@Component
public class KnowledgeCorpusLoader {

    public static final String DEFAULT_RESOURCE_PATTERN = "classpath*:knowledge/*.md";
    private static final String REQUIRED_PATH_KEYWORD = "knowledge";
    private static final String FORBIDDEN_PATH_KEYWORD = "docs";

    private final ResourcePatternResolver resourcePatternResolver;
    private final String resourcePattern;

    public KnowledgeCorpusLoader() {
        this(new PathMatchingResourcePatternResolver(), DEFAULT_RESOURCE_PATTERN);
    }

    public KnowledgeCorpusLoader(ResourcePatternResolver resourcePatternResolver) {
        this(resourcePatternResolver, DEFAULT_RESOURCE_PATTERN);
    }

    public KnowledgeCorpusLoader(ResourcePatternResolver resourcePatternResolver, String resourcePattern) {
        validateResourcePattern(resourcePattern);
        this.resourcePatternResolver = Objects.requireNonNull(resourcePatternResolver, "resourcePatternResolver no puede ser nulo");
        this.resourcePattern = resourcePattern;
    }

    /**
     * Carga e indexa todos los fragmentos públicos del corpus configurado.
     *
     * @return lista inmutable de fragmentos de conocimiento cargados
     */
    public List<KnowledgeChunk> load() {
        return loadFromPattern(this.resourcePattern);
    }

    /**
     * Carga fragmentos a partir de un patrón de recursos específico.
     *
     * @param pattern patrón de recursos (debe contener 'knowledge' y no contener 'docs')
     * @return lista inmutable de fragmentos de conocimiento
     */
    public List<KnowledgeChunk> loadFromPattern(String pattern) {
        validateResourcePattern(pattern);
        try {
            Resource[] resources = resourcePatternResolver.getResources(pattern);
            return loadFromResources(resources);
        } catch (IOException e) {
            throw new IllegalStateException("Error al resolver los recursos de conocimiento con el patrón: " + pattern, e);
        }
    }

    /**
     * Carga fragmentos a partir de un arreglo de recursos {@link Resource}.
     *
     * @param resources recursos a procesar
     * @return lista inmutable de fragmentos de conocimiento
     */
    public List<KnowledgeChunk> loadFromResources(Resource... resources) {
        if (resources == null || resources.length == 0) {
            return List.of();
        }

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (Resource resource : resources) {
            if (resource == null || !resource.exists()) {
                continue;
            }

            String filename = resource.getFilename();
            if (filename == null || !filename.endsWith(".md")) {
                continue;
            }

            try {
                String uriStr = resource.getURI().toString().toLowerCase(Locale.ROOT);
                if (hasPathSegment(uriStr, FORBIDDEN_PATH_KEYWORD) || !hasPathSegment(uriStr, REQUIRED_PATH_KEYWORD)) {
                    continue;
                }
            } catch (IOException ignored) {
                // Si el URI no puede obtenerse (ej. en ciertos entornos virtuales), se continúa con la lectura
            }

            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8))) {
                StringBuilder builder = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) {
                    builder.append(line).append('\n');
                }
                chunks.addAll(parse(builder.toString()));
            } catch (IOException e) {
                throw new IllegalStateException("Error al leer el recurso de conocimiento: " + filename, e);
            }
        }

        return Collections.unmodifiableList(chunks);
    }

    /**
     * Parsea el contenido Markdown de un archivo del corpus y produce los {@link KnowledgeChunk} correspondientes.
     * Si el archivo no tiene {@code visibility: public}, se descarta y retorna una lista vacía.
     *
     * @param markdownContent contenido completo del archivo markdown
     * @return lista de fragmentos indexados por sección de idioma
     */
    public List<KnowledgeChunk> parse(String markdownContent) {
        if (markdownContent == null || markdownContent.isBlank()) {
            return List.of();
        }

        String normalized = markdownContent.replace("\r\n", "\n").replace('\r', '\n');
        if (normalized.startsWith("\uFEFF")) {
            normalized = normalized.substring(1);
        }

        String[] lines = normalized.split("\n", -1);
        int openingIndex = -1;
        for (int i = 0; i < lines.length; i++) {
            if (lines[i].trim().equals("---")) {
                openingIndex = i;
                break;
            } else if (!lines[i].isBlank()) {
                // Hay contenido antes del primer --- -> no contiene frontmatter válido
                return List.of();
            }
        }

        if (openingIndex == -1) {
            return List.of();
        }

        int closingIndex = -1;
        Map<String, String> header = new LinkedHashMap<>();
        for (int i = openingIndex + 1; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.equals("---")) {
                closingIndex = i;
                break;
            }
            int colonIndex = line.indexOf(':');
            if (colonIndex > 0) {
                String key = line.substring(0, colonIndex).trim();
                String value = line.substring(colonIndex + 1).trim();
                if ((value.startsWith("\"") && value.endsWith("\"")) || (value.startsWith("'") && value.endsWith("'"))) {
                    value = value.substring(1, value.length() - 1).trim();
                }
                header.put(key, value);
            }
        }

        if (closingIndex == -1) {
            return List.of();
        }

        // FR-004: Solo indexar archivos con visibility: public
        String visibility = header.get("visibility");
        if (visibility == null || !"public".equalsIgnoreCase(visibility.trim())) {
            return List.of();
        }

        String sourceId = header.get("sourceId");
        if (sourceId == null || sourceId.isBlank()) {
            return List.of();
        }

        KnowledgeChunk.SourceType sourceType = parseSourceType(header.get("type"));
        String fullTitle = header.getOrDefault("title", sourceId).trim();
        if (fullTitle.isBlank()) {
            fullTitle = sourceId;
        }

        List<Section> sections = extractLanguageSections(lines, closingIndex + 1);
        if (sections.isEmpty()) {
            return List.of();
        }

        List<KnowledgeChunk> chunks = new ArrayList<>();
        for (Section section : sections) {
            String title = resolveTitleForLanguage(fullTitle, section.language());
            String content = section.content();
            String hash = computeContentHash(content);

            chunks.add(new KnowledgeChunk(
                    sourceId,
                    sourceType,
                    title,
                    content,
                    section.language(),
                    hash,
                    null,
                    null
            ));
        }

        return Collections.unmodifiableList(chunks);
    }

    private KnowledgeChunk.SourceType parseSourceType(String typeStr) {
        if (typeStr == null || typeStr.isBlank()) {
            return KnowledgeChunk.SourceType.POLICY_DOC;
        }
        try {
            return KnowledgeChunk.SourceType.valueOf(typeStr.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return KnowledgeChunk.SourceType.POLICY_DOC;
        }
    }

    private List<Section> extractLanguageSections(String[] lines, int startIndex) {
        KnowledgeChunk.Language currentLang = null;
        StringBuilder currentContent = new StringBuilder();
        List<Section> sections = new ArrayList<>();

        for (int i = startIndex; i < lines.length; i++) {
            String rawLine = lines[i];
            String trimmedLine = rawLine.trim();

            if (trimmedLine.startsWith("## ")) {
                String heading = trimmedLine.substring(3).trim().toLowerCase(Locale.ROOT);
                KnowledgeChunk.Language detectedLang = detectLanguage(heading);

                if (detectedLang != null) {
                    if (currentLang != null && !currentContent.toString().isBlank()) {
                        sections.add(new Section(currentLang, currentContent.toString().trim()));
                    }
                    currentLang = detectedLang;
                    currentContent.setLength(0);
                    continue;
                }
            }

            if (currentLang != null) {
                currentContent.append(rawLine).append('\n');
            } else {
                currentContent.append(rawLine).append('\n');
            }
        }

        if (currentLang != null && !currentContent.toString().isBlank()) {
            sections.add(new Section(currentLang, currentContent.toString().trim()));
        } else if (sections.isEmpty() && !currentContent.toString().isBlank()) {
            // Documento sin encabezados ## explícitos de idioma: por defecto español (FR-014)
            sections.add(new Section(KnowledgeChunk.Language.ES, currentContent.toString().trim()));
        }

        return sections;
    }

    private KnowledgeChunk.Language detectLanguage(String heading) {
        if (heading.contains("español") || heading.contains("espanol") || heading.contains("spanish") || heading.equals("es")) {
            return KnowledgeChunk.Language.ES;
        }
        if (heading.contains("english") || heading.contains("inglés") || heading.contains("ingles") || heading.equals("en")) {
            return KnowledgeChunk.Language.EN;
        }
        return null;
    }

    private String resolveTitleForLanguage(String fullTitle, KnowledgeChunk.Language language) {
        if (fullTitle.contains(" / ")) {
            String[] parts = fullTitle.split("\\s*/\\s*", 2);
            if (parts.length == 2) {
                if (language == KnowledgeChunk.Language.ES) {
                    return parts[0].trim();
                } else if (language == KnowledgeChunk.Language.EN) {
                    return parts[1].trim();
                }
            }
        }
        return fullTitle;
    }

    private String computeContentHash(String content) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(content.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder(64);
            for (byte b : hash) {
                hexString.append(String.format("%02x", b & 0xff));
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("Algoritmo de hash SHA-256 no disponible", e);
        }
    }

    private void validateResourcePattern(String pattern) {
        if (pattern == null || pattern.isBlank()) {
            throw new IllegalArgumentException("El patrón de recursos no puede ser nulo ni estar vacío");
        }
        String lower = pattern.toLowerCase(Locale.ROOT);
        if (hasPathSegment(lower, FORBIDDEN_PATH_KEYWORD)) {
            throw new IllegalArgumentException("Acceso prohibido: no se permite la lectura de la ruta 'docs/' (FR-004)");
        }
        if (!hasPathSegment(lower, REQUIRED_PATH_KEYWORD)) {
            throw new IllegalArgumentException("Acceso restringido: solo se permite la lectura dentro del directorio 'knowledge/' (FR-004)");
        }
    }

    /**
     * Comprueba que {@code segment} aparece como segmento completo de ruta, no como parte de otro nombre.
     * Así {@code /mi-proyecto-docs/} no cuenta como {@code docs} (evita descartar todo el corpus en silencio).
     */
    static boolean hasPathSegment(String text, String segment) {
        return Pattern.compile("(?<![\\w.-])" + Pattern.quote(segment) + "(?![\\w.-])")
                .matcher(text)
                .find();
    }

    private record Section(KnowledgeChunk.Language language, String content) {}
}
