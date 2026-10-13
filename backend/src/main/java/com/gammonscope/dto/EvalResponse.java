package com.gammonscope.dto;

import java.util.List;

/** A ranked list of candidate moves for a position. */
public record EvalResponse(List<EvalMove> moves) {

    /**
     * One candidate move.
     *
     * @param moves     sub-moves as [from, to] pairs in the mover's own coords (24 = bar, -1 = off)
     * @param notation  human-readable move notation (e.g. "24/18 13/11")
     * @param equity    0-ply cubeless money equity for the mover
     * @param win       probability the mover wins
     * @param winGammon probability the mover wins a gammon
     * @param winBackgammon probability the mover wins a backgammon
     * @param loseGammon probability the mover loses a gammon
     * @param loseBackgammon probability the mover loses a backgammon
     */
    public record EvalMove(
            int[][] moves,
            String notation,
            double equity,
            double win,
            double winGammon,
            double winBackgammon,
            double loseGammon,
            double loseBackgammon) {
    }
}
