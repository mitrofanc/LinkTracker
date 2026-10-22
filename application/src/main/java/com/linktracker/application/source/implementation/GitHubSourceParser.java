package com.linktracker.application.source.implementation;

import com.linktracker.application.exception.InvalidSourceUrlException;
import com.linktracker.application.model.SourceType;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.regex.Pattern;

import com.linktracker.application.source.ParsedSource;
import com.linktracker.application.source.SourceUrlParser;
import org.jspecify.annotations.NonNull;
import org.springframework.stereotype.Component;

@Component
public class GitHubSourceParser implements SourceUrlParser {

    private static final Pattern OWNER = Pattern.compile("[a-z0-9]+(?:-[a-z0-9]+)*");
    private static final Pattern REPOSITORY = Pattern.compile("[a-z0-9._-]+");

    @Override
    public boolean supports(URI uri) {
        return uri != null && "github.com".equalsIgnoreCase(uri.getHost());
    }

    // Parses the address only; makes no network requests and does not verify existence.
    @Override
    public ParsedSource parse(String url) {
        String[] segments = getSegments(url);
        if (segments.length != 3 || !segments[0].isEmpty()) {
            throw new InvalidSourceUrlException();
        }
        String owner = segments[1];
        String repository = segments[2];
        if (repository.endsWith(".git")) {
            repository = repository.substring(0, repository.length() - 4);
        }
        if (!OWNER.matcher(owner).matches()
                || !REPOSITORY.matcher(repository).matches()
                || repository.endsWith(".git")
                || ".".equals(repository) || "..".equals(repository)) {
            throw new InvalidSourceUrlException();
        }

        String externalId = owner + "/" + repository;
        return new ParsedSource(SourceType.GITHUB, "https://github.com/" + externalId, externalId);
    }

    private static String @NonNull [] getSegments(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidSourceUrlException();
        }
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException exception) {
            throw new InvalidSourceUrlException();
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())
                || !"github.com".equalsIgnoreCase(uri.getHost())
                || uri.getRawUserInfo() != null
                || uri.getPort() != -1
                || !"github.com".equalsIgnoreCase(uri.getRawAuthority())
                || uri.getRawQuery() != null
                || uri.getRawFragment() != null) {
            throw new InvalidSourceUrlException();
        }

        // Use the raw path: percent-encoded separators/names are not accepted.
        String path = uri.getRawPath().toLowerCase(Locale.ROOT);
        if (path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        String[] segments = path.split("/", -1);
        return segments;
    }
}
