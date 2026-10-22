package com.linktracker.application.source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.linktracker.application.exception.InvalidSourceUrlException;
import com.linktracker.application.model.SourceType;
import java.net.URI;

import com.linktracker.application.source.implementation.GitHubSourceParser;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class GitHubSourceParserTests {

    private final SourceUrlParser parser = new GitHubSourceParser();

    @ParameterizedTest
    @ValueSource(strings = {
        "https://github.com/owner/repository",
        "https://GITHUB.COM/owner/repository",
        "https://github.com/owner/repository/issues/123"
    })
    void recognizesProviderWithoutReplacingFullValidation(String url) {
        assertThat(parser.supports(URI.create(url))).isTrue();
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
        "https://gitlab.com/owner/repository",
        "https://github.com.evil.test/owner/repository",
        "https://github.com@evil.test/owner/repository",
        "/owner/repository"
    })
    void doesNotSelectAnotherProvider(String url) {
        assertThat(parser.supports(url == null ? null : URI.create(url))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://github.com/Spring-Projects/Spring-Boot",
        "https://github.com/spring-projects/spring-boot/",
        "https://github.com/spring-projects/spring-boot.git",
        "HTTPS://GITHUB.COM/SPRING-PROJECTS/SPRING-BOOT.GIT/",
        "  https://github.com/spring-projects/spring-boot  "
    })
    void normalizesVariantsToTheSameSource(String url) {
        ParsedSource result = parser.parse(url);
        assertThat(result).isEqualTo(new ParsedSource(SourceType.GITHUB,
                "https://github.com/spring-projects/spring-boot", "spring-projects/spring-boot"));
        assertThat(parser.parse(result.canonicalUrl())).isEqualTo(result);
    }

    @ParameterizedTest
    @ValueSource(strings = {
        "https://github.com/owner/repo.name",
        "https://github.com/owner/repo_name",
        "https://github.com/owner/.github"
    })
    void preservesAllowedRepositoryCharacters(String url) {
        ParsedSource result = parser.parse(url);
        assertThat(result.canonicalUrl()).isEqualTo(url);
        assertThat(result.externalId()).isEqualTo(url.substring("https://github.com/".length()));
        assertThat(parser.parse(result.canonicalUrl())).isEqualTo(result);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {
        " ",
        "not a URL",
        "github.com/owner/repository",
        "http://github.com/owner/repository",
        "git://github.com/owner/repository",
        "git@github.com:owner/repository.git",
        "https://gitlab.com/owner/repository",
        "https://www.github.com/owner/repository",
        "https://github.com.evil.test/owner/repository",
        "https://github.com@evil.test/owner/repository",
        "https://evil.test@github.com/owner/repository",
        "https://user:password@github.com/owner/repository",
        "https://github.com:443/owner/repository",
        "https://github.com:/owner/repository",
        "https://github.com./owner/repository",
        "https://127.0.0.1/owner/repository",
        "https://github.com",
        "https://github.com/",
        "https://github.com/owner",
        "https://github.com/owner/",
        "https://github.com//repository",
        "https://github.com/owner/repository//",
        "https://github.com/owner/repository/issues/123",
        "https://github.com/owner/repository?tab=readme",
        "https://github.com/owner/repository?",
        "https://github.com/owner/repository#readme",
        "https://github.com/owner/repository#",
        "https://github.com/owner%2Frepository/name",
        "https://github.com/owner/%72epository",
        "https://github.com/owner/.",
        "https://github.com/owner/..",
        "https://github.com/owner/.git",
        "https://github.com/owner/..git",
        "https://github.com/owner/...git",
        "https://github.com/owner/repository.git.git",
        "https://github.com/-owner/repository",
        "https://github.com/owner_/repository",
        "https://github.com/owner/repo name",
        "https://github.com/owner/repo\\name",
        "https://github.com/owner/репозиторий"
    })
    void rejectsUnsupportedOrAmbiguousAddresses(String url) {
        assertThatThrownBy(() -> parser.parse(url))
                .isInstanceOf(InvalidSourceUrlException.class)
                .hasMessage("Expected a GitHub repository URL: https://github.com/owner/repository");
    }
}
