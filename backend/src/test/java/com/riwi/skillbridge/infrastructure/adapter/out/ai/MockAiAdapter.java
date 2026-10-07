package com.riwi.skillbridge.infrastructure.adapter.out.ai;

import com.riwi.skillbridge.application.port.out.AiRecommendationPort;
import com.riwi.skillbridge.domain.model.Offering;
import org.springframework.boot.test.context.TestComponent;

import java.util.List;

/**
 * Mock AI adapter for testing purposes.
 * 
 * This adapter implements AiRecommendationPort and returns deterministic responses
 * without making external API calls. Use this in tests to verify application logic
 * without depending on actual AI provider availability or network connectivity.
 * 
 * Usage in tests:
 * 
 * @SpringBootTest
 * public class SomeServiceTest {
 *     @MockBean
 *     private AiRecommendationPort aiPort;
 *     
 *     @Test
 *     void testWithMockAdapter() {
 *         when(aiPort.recommend(anyString(), anyList()))
 *             .thenReturn("Mock recommendation");
 *     }
 * }
 * 
 * Or inject directly:
 * 
 * @Bean
 * @Primary
 * public AiRecommendationPort mockAiAdapter() {
 *     return new MockAiAdapter();
 * }
 */
@TestComponent
public class MockAiAdapter implements AiRecommendationPort {
    
    @Override
    public String recommend(String goal, List<Offering> offerings) {
        return """
                Based on your goal: "%s"
                
                Recommended services:
                1. %s - Perfect match for your learning goals
                2. %s - Comprehensive support in this area
                
                Next steps: Schedule a session with our mentors to discuss your specific needs.
                """.formatted(
                    goal,
                    offerings.isEmpty() ? "Service 1" : offerings.get(0).title(),
                    offerings.size() > 1 ? offerings.get(1).title() : "Service 2"
                );
    }
}
