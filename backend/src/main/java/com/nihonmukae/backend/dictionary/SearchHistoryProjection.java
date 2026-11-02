package com.nihonmukae.backend.dictionary;

import java.time.Instant;


public interface SearchHistoryProjection {
    Long getId();
    Long getWordId();
    String getKanji();
    String getKana();
    String getRawQuery();
    // Native query: Hibernate 6 trả về Instant, không ép được sang ZonedDateTime
    Instant getSearchedAt();
}
