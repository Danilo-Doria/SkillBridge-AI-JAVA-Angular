package com.riwi.mcp;

import io.modelcontextprotocol.spec.McpSchema;
import java.util.List;
import java.util.Map;

public final class ToolCatalog {
    private ToolCatalog() {}
    public static List<McpSchema.Tool> definitions() {
        return List.of(
            stringTool("audit_by_correlation", "Audit trace by correlationId", "Returns chronological audit evidence.", "correlationId"),
            stringTool("booking_status", "Booking and payment status", "Returns read-only booking status.", "bookingId"),
            noArgTool("queue_status", "Queue and DLQ status", "Returns read-only queue and DLQ status."),
            noArgTool("ai_providers_status", "AI provider status", "Returns read-only status of configured AI providers."));
    }
    private static McpSchema.Tool stringTool(String name, String title, String description, String property) {
        return McpSchema.Tool.builder().name(name).title(title).description(description)
            .inputSchema(new McpSchema.JsonSchema("object", Map.of(property, Map.of("type", "string", "minLength", 1)), List.of(property), false, Map.of(), Map.of()))
            .annotations(readOnlyAnnotations()).build();
    }
    private static McpSchema.Tool noArgTool(String name, String title, String description) {
        return McpSchema.Tool.builder().name(name).title(title).description(description)
            .inputSchema(new McpSchema.JsonSchema("object", Map.of(), List.of(), false, Map.of(), Map.of()))
            .annotations(readOnlyAnnotations()).build();
    }
    private static McpSchema.ToolAnnotations readOnlyAnnotations() {
        return new McpSchema.ToolAnnotations("Read-only incident evidence", true, false, true, false, false);
    }
}
