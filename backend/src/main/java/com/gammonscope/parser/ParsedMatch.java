package com.gammonscope.parser;

import java.util.ArrayList;
import java.util.List;

import com.gammonscope.model.MoveType;

/** Format-neutral result of parsing a game-record file, later mapped to JPA entities. */
public class ParsedMatch {

    public String player0 = "Player 1";
    public String player1 = "Player 2";
    public int matchLength;
    public String variation = "Backgammon";
    public String result;
    public String event;
    public String site;
    public String round;
    public String matchDate;
    public String sourceFormat;

    public final List<ParsedGame> games = new ArrayList<>();

    public static class ParsedGame {
        public int gameNumber;
        public int score0;
        public int score1;
        public int winner = -1;
        public int points;
        public boolean crawford;
        /** Optional JSON board override; null means the standard starting position. */
        public String setupBoard;
        public final List<ParsedMove> moves = new ArrayList<>();
    }

    public static class ParsedMove {
        public int player;
        public MoveType type;
        public int dice0;
        public int dice1;
        /** [from,to] internal-index pairs for NORMAL moves. */
        public List<int[]> moves;
        public String notation;
        public String comment;
        public Integer cubeValue;
        public Integer resigned;
        /** Cube owner for SET_CUBE_POS: -1 center, 0 or 1. */
        public Integer cubeOwner;
        /** JSON board override for SET_BOARD records; null otherwise. */
        public String setup;
    }
}
