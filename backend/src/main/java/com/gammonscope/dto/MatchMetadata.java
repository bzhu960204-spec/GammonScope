package com.gammonscope.dto;

/** Editable match-level metadata used for manual creation and in-place editing. */
public record MatchMetadata(
        String player0,
        String player1,
        int matchLength,
        String variation,
        String result,
        String event,
        String site,
        String round,
        String matchDate) {
}
