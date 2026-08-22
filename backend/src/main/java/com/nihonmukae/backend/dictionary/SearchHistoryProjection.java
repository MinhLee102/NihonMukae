package com.nihonmukae.backend.dictionary;

import java.time.ZonedDateTime;


public interface SearchHistoryProjection {
    Long getId();
    Long getWordId();
    String getKanji();
    String getKana();
    String getRawQuery();
    ZonedDateTime getSearchedAt();
}
