package com.linktracker.application.source;

import com.linktracker.application.exception.InvalidSourceUrlException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class SourceUrlResolver {

    private final List<SourceUrlParser> parsers;

    public ParsedSource resolve(String url) {
        if (url == null || url.isBlank()) {
            throw new InvalidSourceUrlException();
        }
        URI uri;
        try {
            uri = new URI(url.strip());
        } catch (URISyntaxException exception) {
            throw new InvalidSourceUrlException();
        }
        return parsers.stream()
                .filter(parser -> parser.supports(uri))
                .findFirst()
                .orElseThrow(InvalidSourceUrlException::new)
                .parse(url);
    }
}
