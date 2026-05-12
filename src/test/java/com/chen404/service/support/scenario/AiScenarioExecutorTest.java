package com.chen404.service.support.scenario;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiScenarioExecutorTest {

    @Test
    void shouldDispatchToMatchingScenarioDefinition() {
        AiScenarioExecutor executor = new AiScenarioExecutor(List.of(new StubScenarioDefinition()));

        AiScenarioResult<String> result = executor.execute(
                AiScenarioRequest.of(AiScenarioCode.ARTICLE_RECOMMEND, "seed")
        );

        assertEquals("handled-seed", result.data());
    }

    private static final class StubScenarioDefinition implements AiScenarioDefinition<String, String> {

        @Override
        public AiScenarioCode code() {
            return AiScenarioCode.ARTICLE_RECOMMEND;
        }

        @Override
        public AiScenarioResult<String> execute(AiScenarioRequest<String> request) {
            return AiScenarioResult.of("handled-" + request.payload());
        }
    }
}
