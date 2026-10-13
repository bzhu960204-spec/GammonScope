package com.gammonscope.dto;

import java.time.Instant;

public record MatchSummary(
        Long id,
        String player0,
        String player1,
        int matchLength,
        String variation,
        String result,
        String sourceFormat,
        String event,
        String matchDate,
        Instant importedAt,
        long gameCount) {
}
