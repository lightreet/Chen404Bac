package com.chen404.domain.dto;

import com.chen404.exception.BadRequestException;
import com.chen404.service.impl.ArticleServiceImpl;
import com.chen404.service.support.ArticlePolicyValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.BeanWrapperImpl;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ArticleCommandValidationTest {
    private static final ValidatorFactory FACTORY = Validation.buildDefaultValidatorFactory();
    private static final Validator VALIDATOR = FACTORY.getValidator();

    @AfterAll
    static void closeValidator() { FACTORY.close(); }

    @ParameterizedTest
    @MethodSource("invalidInputs")
    void shouldRejectInvalidIntegersAtRequestAndServiceBoundaries(Class<?> commandType, String field, int invalid)
            throws Exception {
        Object command = commandType.getConstructor().newInstance();
        BeanWrapperImpl wrapper = new BeanWrapperImpl(command);
        wrapper.setPropertyValue("title", "标题");
        wrapper.setPropertyValue("content", "内容");
        wrapper.setPropertyValue("categoryId", 1L);
        wrapper.setPropertyValue("status", 1);
        wrapper.setPropertyValue(field, invalid);
        assertTrue(VALIDATOR.validate(command).stream()
                .anyMatch(violation -> field.equals(violation.getPropertyPath().toString())));

        ArticleServiceImpl service = new ArticleServiceImpl();
        if (command instanceof CreateArticleCommand create) {
            assertThrows(BadRequestException.class, () -> service.createArticle(create, 7L));
        } else {
            assertThrows(BadRequestException.class, () -> service.updateArticle(1L, (UpdateArticleCommand) command, 7L));
        }
    }

    @Test
    void shouldAcceptEverySupportedPolicyCombinationAndOptionalDefaults() {
        for (int status = 0; status <= 2; status++) {
            for (int visibility = 0; visibility <= 3; visibility++) {
                for (int policy = 0; policy <= 3; policy++) {
                    CreateArticleCommand command = new CreateArticleCommand();
                    command.setTitle("title");
                    command.setContent("content");
                    command.setCategoryId(1L);
                    command.setStatus(status);
                    command.setVisibility(visibility);
                    command.setCommentPolicy(policy);
                    assertTrue(VALIDATOR.validate(command).isEmpty());
                    CreateArticleCommand article = new CreateArticleCommand();
                    article.setStatus(status);
                    article.setVisibility(visibility);
                    article.setCommentPolicy(policy);
                    assertDoesNotThrow(() -> ArticlePolicyValidator.validate(article));
                }
            }
        }
        CreateArticleCommand defaults = new CreateArticleCommand();
        defaults.setStatus(0);
        assertDoesNotThrow(() -> ArticlePolicyValidator.validate(defaults));
        defaults.setStatus(null);
        assertThrows(BadRequestException.class, () -> ArticlePolicyValidator.validate(defaults));
    }

    static Stream<Arguments> invalidInputs() {
        return Stream.of(CreateArticleCommand.class, UpdateArticleCommand.class).flatMap(type ->
                Stream.of("status", "visibility", "commentPolicy", "isTop", "isRecommend", "isOriginal")
                        .flatMap(field -> Stream.of(-1, firstUnsupportedValue(field), 99)
                                .map(value -> Arguments.of(type, field, value))));
    }

    private static int firstUnsupportedValue(String field) {
        return switch (field) {
            case "status" -> 3;
            case "visibility", "commentPolicy" -> 4;
            default -> 2;
        };
    }
}
