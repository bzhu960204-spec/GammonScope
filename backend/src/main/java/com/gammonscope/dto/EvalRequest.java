package com.gammonscope.dto;

/**
 * Request for a move hint: the current board (each player's own perspective),
 * who is on roll, and the dice rolled.
 */
public record EvalRequest(
        int[][] checkers,
        int onRoll,
        int dice0,
        int dice1) {
}
