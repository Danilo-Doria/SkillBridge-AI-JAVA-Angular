package com.riwi.mcp;

import java.util.Set;
import java.util.regex.Pattern;

public final class TriageAgent {
    private static final Set<String> ALLOWED = Set.of("audit_by_correlation", "booking_status", "queue_status", "ai_providers_status");
    private static final Pattern CITATION = Pattern.compile("(?i)(?:eventId|correlationId)\\s*[:=]\\s*[\\w-]+");
    private final AuditClient client;
    private final int maxSteps;
    public TriageAgent(AuditClient client, int maxSteps) {
        if (maxSteps < 1 || maxSteps > 8) throw new IllegalArgumentException("maxSteps must be between 1 and 8");
        this.client = client; this.maxSteps = maxSteps;
    }
    public String execute(String tool, String argument) throws Exception {
        if (!ALLOWED.contains(tool)) throw new SecurityException("tool is not listed: " + tool);
        String raw = switch (tool) {
            case "audit_by_correlation" -> client.byCorrelation(argument);
            case "booking_status" -> client.get("/api/bookings/" + java.net.URLEncoder.encode(argument, java.nio.charset.StandardCharsets.UTF_8));
            case "queue_status" -> client.get("/api/ops/queue/status");
            case "ai_providers_status" -> client.get("/api/ai/providers/status");
            default -> throw new SecurityException("tool is not listed: " + tool);
        };
        return redact(raw);
    }
    public static String redact(String raw) {
        return raw.replaceAll("(?i)(\\\"?(?:email|token|authorization|zoomPasscode|password|cardNumber)\\\"?\\s*[:=]\\s*\\\"?)[^,\\\"} ]+", "$1[REDACTED]");
    }
    public static boolean hasVerifiableCitation(String text) { return CITATION.matcher(text).find(); }
}
