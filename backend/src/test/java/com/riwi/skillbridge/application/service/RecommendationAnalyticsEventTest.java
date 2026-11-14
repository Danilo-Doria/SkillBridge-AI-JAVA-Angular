package com.riwi.skillbridge.application.service;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.application.port.out.RecommendationEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.application.recommendation.InputType;
import com.riwi.skillbridge.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.domain.model.Offering;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** HU-104 (R1): RecommendationGenerated llega al topic que escucha analytics (trending). */
class RecommendationAnalyticsEventTest {

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void generated_se_publica_tambien_en_recommendation_events_con_el_mismo_event_id() {
        AiRecommendationPort ai = mock(AiRecommendationPort.class);
        OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
        AuditEventPublisherPort audit = mock(AuditEventPublisherPort.class);
        RecommendationEventPublisherPort analytics = mock(RecommendationEventPublisherPort.class);
        Offering offering = new Offering(UUID.randomUUID(), UUID.randomUUID(), "Java", "d", "BACKEND", BigDecimal.TEN, true);
        when(offerings.findAllActive()).thenReturn(List.of(offering));
        when(ai.recommend(any(), any())).thenReturn(new AiStructuredResponse("ok",
            List.of(new AiStructuredResponse.AiItem(offering.id().toString(), 0.9, "r"))));

        new AiRecommendationService(ai, offerings, audit, null, 0, analytics)
            .recommend(new RecommendationRequest(InputType.TEXT, "java", null, UUID.randomUUID()));

        ArgumentCaptor<BusinessEvent> toAnalytics = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(analytics, times(1)).publish(toAnalytics.capture());
        assertEquals("RecommendationGenerated", toAnalytics.getValue().eventType());
        ArgumentCaptor<BusinessEvent> toAudit = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(audit, times(2)).publish(toAudit.capture());
        assertEquals(toAnalytics.getValue().eventId(), toAudit.getAllValues().get(1).eventId());
    }
}
