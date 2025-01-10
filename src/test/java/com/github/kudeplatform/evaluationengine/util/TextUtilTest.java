package com.github.kudeplatform.evaluationengine.util;

import com.github.kudeplatform.evaluationengine.domain.Repository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TextUtilTest {

    @Test
    void parseRepositoriesFromMassInput() {
        // given
        final String massInput = """
                URL;Name
                http://example.com;Example Repository
                not-a-url;Invalid Repository
                http://example.org;Another Repository
                
                """;

        // when
        final var repositories = TextUtil.parseRepositoriesFromMassInput(massInput, "username", "token");

        // then
        assertThat(repositories).containsExactly(
                new Repository("Example Repository", "http://example.com", "username", "token"),
                new Repository("Another Repository", "http://example.org", "username", "token")
        );
    }
}