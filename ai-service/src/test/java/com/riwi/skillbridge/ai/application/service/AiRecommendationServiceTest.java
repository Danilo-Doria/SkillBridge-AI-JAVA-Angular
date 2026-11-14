package com.riwi.skillbridge.ai.application.service;

import com.riwi.skillbridge.ai.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.ai.application.port.out.AiStructuredResponse;
import com.riwi.skillbridge.ai.application.port.out.OfferingRepositoryPort;
import com.riwi.skillbridge.ai.application.port.out.event.AuditEventPublisherPort;
import com.riwi.skillbridge.ai.application.port.out.event.BusinessEvent;
import com.riwi.skillbridge.ai.application.port.out.event.RecommendationGeneratedPayload;
import com.riwi.skillbridge.ai.application.recommendation.InputType;
import com.riwi.skillbridge.ai.application.recommendation.RecommendationRequest;
import com.riwi.skillbridge.ai.application.recommendation.RecommendationResult;
import com.riwi.skillbridge.ai.domain.exception.AiProviderException;
import com.riwi.skillbridge.ai.domain.model.Offering;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AiRecommendationServiceTest {

    private final AiRecommendationPort ai = mock(AiRecommendationPort.class);
    private final OfferingRepositoryPort offerings = mock(OfferingRepositoryPort.class);
    private final AuditEventPublisherPort events = mock(AuditEventPublisherPort.class);
    private final AiRecommendationService service = new AiRecommendationService(ai, offerings, events);

    @Test
    void sendsWholeActiveCatalogDropsUnknownOfferingsAndPublishesBothEvents() {
        Offering spring = new Offering(UUID.randomUUID(), UUID.randomUUID(), "Spring", "d", "c", BigDecimal.TEN, true);
        Offering angular = new Offering(UUID.randomUUID(), UUID.randomUUID(), "Angular", "d", "c", BigDecimal.TEN, true);
        when(offerings.findAllActive()).thenReturn(List.of(spring, angular));
        when(ai.recommend(any(), eq(List.of(spring, angular)))).thenReturn(new AiStructuredResponse("ok", List.of(
                new AiStructuredResponse.AiItem(spring.id().toString(), 0.9, "r1"),
                new AiStructuredResponse.AiItem("no-es-uuid", 0.8, "r2"),
                new AiStructuredResponse.AiItem(UUID.randomUUID().toString(), 0.7, "r3")), "gemini", "m1"));

        UUID user = UUID.randomUUID();
        RecommendationResult result = service.recommend(new RecommendationRequest(InputType.TEXT, "aprender", null, user));

        assertThat(result.recommendations()).hasSize(1);
        assertThat(result.recommendations().get(0).offeringTitle()).isEqualTo("Spring");
        ArgumentCaptor<BusinessEvent<?>> captor = ArgumentCaptor.forClass(BusinessEvent.class);
        verify(events, times(2)).publish(captor.capture());
        assertThat(captor.getAllValues()).extracting(BusinessEvent::eventType)
                .containsExactly("RecommendationRequested", "RecommendationGenerated");
        RecommendationGeneratedPayload payload = (RecommendationGeneratedPayload) captor.getAllValues().get(1).payload();
        assertThat(payload.provider()).isEqualTo("gemini");
        assertThat(payload.modelUsed()).isEqualTo("m1");
        assertThat(payload.offeringIds()).containsExactly(spring.id());
        assertThat(captor.getAllValues().get(1).actorUserId()).isEqualTo(user.toString());
    }

    @Test
    void propagatesProviderUnavailabilityWithoutInventingResults() {
        when(offerings.findAllActive()).thenReturn(List.of());
        when(ai.recommend(any(), any())).thenThrow(new AiProviderException("none", AiProviderException.ErrorType.UNAVAILABLE));

        assertThatThrownBy(() -> service.recommend(new RecommendationRequest(InputType.TEXT, "x", null, UUID.randomUUID())))
                .isInstanceOf(AiProviderException.class);
        verify(events, times(1)).publish(any()); // solo RecommendationRequested
    }
}
