package com.linktracker.application.source;

import java.net.URI;

public interface SourceUrlParser {

    boolean supports(URI uri);

    ParsedSource parse(String url);
}
