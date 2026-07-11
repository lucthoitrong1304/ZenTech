package hcmute.edu.zentech.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LokiServiceLogParsingTest {

    @Test
    void extractsBackendFrontendAndAiRequestTraceIds() {
        assertEquals("ZT-Ab12Cd34", LokiService.extractTraceId("[ZT-Ab12Cd34] request"));
        assertEquals("ZT-FE-Ab12Cd34", LokiService.extractTraceId("[ZT-FE-Ab12Cd34] request"));
        assertEquals("ZT-AI-Ab12Cd34", LokiService.extractTraceId("[ZT-AI-Ab12Cd34] request"));
    }

    @Test
    void doesNotTreatAiSystemMarkerAsRequestTrace() {
        assertEquals("", LokiService.extractTraceId("[ZT-AI-SYSTEM] Application startup complete"));
    }

    @Test
    void escapesLogQlLiteralCharacters() {
        assertEquals("trace\\\\id\\\"line\\nnext", LokiService.escapeLogQlLiteral("trace\\id\"line\nnext"));
    }
}
