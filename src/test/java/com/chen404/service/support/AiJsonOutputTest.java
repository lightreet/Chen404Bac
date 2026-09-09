package com.chen404.service.support;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.Arguments;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class AiJsonOutputTest {
    @ParameterizedTest
    @MethodSource("outputs")
    void preservesJsonWhileRemovingOptionalFence(String input, String expected) {
        assertEquals(expected, AiJsonOutput.stripCodeFence(input));
    }

    static Stream<Arguments> outputs() {
        return Stream.of(
                Arguments.of(null, ""), Arguments.of("  ", ""),
                Arguments.of("  {\"text\":\"```\"}  ", "{\"text\":\"```\"}"),
                Arguments.of("```json\n{\"value\":1}\n```", "{\"value\":1}"),
                Arguments.of("```\n[1,2]\n```", "[1,2]"));
    }
}
