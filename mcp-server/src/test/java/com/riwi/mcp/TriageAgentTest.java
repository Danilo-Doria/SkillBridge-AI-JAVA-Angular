package com.riwi.mcp;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class TriageAgentTest {
    @Test void rejectsToolsOutsideClosedCatalog() {
        var agent = new TriageAgent(null, 4);
        assertThrows(SecurityException.class, () -> agent.execute("delete_booking", "b-1"));
    }
    @Test void redactsSensitiveFields() {
        var result = TriageAgent.redact("{\"email\":\"a@b.test\",\"token\":\"secret\",\"eventId\":\"evt-1\"}");
        assertTrue(result.contains("[REDACTED]"));
        assertFalse(result.contains("secret"));
        assertTrue(result.contains("eventId"));
    }
    @Test void detectsEvidenceCitations() {
        assertTrue(TriageAgent.hasVerifiableCitation("eventId: evt-1"));
        assertFalse(TriageAgent.hasVerifiableCitation("no evidence"));
    }
}
