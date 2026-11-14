package com.riwi.skillbridge.infrastructure.config;

import org.junit.jupiter.api.Test;
import org.springframework.util.backoff.BackOff;
import org.springframework.util.backoff.BackOffExecution;

import static org.assertj.core.api.Assertions.assertThat;

class KafkaMessagingConfigurationTest {

    @Test
    void contractBackOffIsThreeRetriesStartingAtOneSecondDoubling() {
        BackOff backOff = KafkaMessagingConfiguration.backOff(3, 1000, 2.0, 10000);
        BackOffExecution execution = backOff.start();

        assertThat(execution.nextBackOff()).isEqualTo(1000);
        assertThat(execution.nextBackOff()).isEqualTo(2000);
        assertThat(execution.nextBackOff()).isEqualTo(4000);
        assertThat(execution.nextBackOff()).isEqualTo(BackOffExecution.STOP);
    }
}
