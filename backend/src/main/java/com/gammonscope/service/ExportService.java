package com.gammonscope.service;

import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gammonscope.model.BgMatch;
import com.gammonscope.model.Game;
import com.gammonscope.model.MoveRecord;
import com.gammonscope.model.MoveType;

/** Serialises a stored match back to Jellyfish .mat or GNU Backgammon .sgf text. */
@Service
public class ExportService {

    private final ObjectMapper objectMapper = new ObjectMapper();

    // --- .mat (Jellyfish) ---

    public String toMat(BgMatch match) {
        StringBuilder sb = new StringBuilder();
        appendMeta(sb, "Event", match.getEvent());
        appendMeta(sb, "Site", match.getSite());
        appendMeta(sb, "Round", match.getRound());
        appendMeta(sb, "EventDate", match.getMatchDate());
        if (match.getVariation() != null && !match.getVariation().equalsIgnoreCase("Backgammon")) {
            appendMeta(sb, "Variation", match.getVariation());
        }

        int length = match.getMatchLength();
        sb.append(' ').append(length).append(" point match\n");

        for (Game game : match.getGames()) {
            sb.append("\n Game ").append(game.getGameNumber()).append('\n');
            sb.append(' ')
              .append(padRight(match.getPlayer0() + " : " + game.getScore0(), 37))
              .append(match.getPlayer1()).append(" : ").append(game.getScore1()).append('\n');

            int cube = 1;
            List<String[]> rows = new ArrayList<>();
            String[] cur = null;
            for (MoveRecord move : game.getMoves()) {
                if (move.getType() == MoveType.DOUBLE) {
                    cube = cube <= 1 ? 2 : cube * 2;
                }
                int col = move.getPlayer();
                // A left-column (player 0) move always opens a new row, so a lead
                // opening by player 1 stays alone with the left cell empty.
                boolean needNew = col == 0 ? cur != null : (cur == null || cur[col] != null);
                if (needNew) {
                    cur = new String[2];
                    rows.add(cur);
                }
                cur[col] = matCell(move, cube);
            }
            if (game.getWinner() >= 0 && game.getPoints() > 0) {
                int col = game.getWinner();
                String win = "Wins " + game.getPoints() + (game.getPoints() == 1 ? " point" : " points");
                boolean needNew = col == 0 ? cur != null : (cur == null || cur[col] != null);
                if (needNew) {
                    cur = new String[2];
                    rows.add(cur);
                }
                cur[col] = win;
            }

            int n = 1;
            for (String[] row : rows) {
                String left = row[0] == null ? "" : row[0];
                String right = row[1] == null ? "" : row[1];
                sb.append(String.format(" %2d) %s%s%n", n++, padRight(left, 37), right));
            }
        }
        return sb.toString();
    }

    private String matCell(MoveRecord move, int cube) {
        switch (move.getType()) {
            case NORMAL:
            case SET_DICE:
                String notation = move.getNotation() == null ? "" : move.getNotation();
                return move.getDice0() + "" + move.getDice1() + ": " + notation;
            case DOUBLE:
                return "Doubles => " + cube;
            case TAKE:
                return "Takes";
            case DROP:
                return "Drops";
            case RESIGN:
                return "Resigns";
            default:
                return "";
        }
    }

    private void appendMeta(StringBuilder sb, String key, String value) {
        if (value != null && !value.isBlank()) {
            sb.append("; [").append(key).append(" \"").append(value).append("\"]\n");
        }
    }

    private String padRight(String s, int width) {
        if (s.length() >= width) {
            return s + " ";
        }
        StringBuilder sb = new StringBuilder(s);
        while (sb.length() < width) {
            sb.append(' ');
        }
        return sb.toString();
    }

    // --- .sgf (GNU Backgammon) ---

    public String toSgf(BgMatch match) {
        StringBuilder sb = new StringBuilder();
        List<Game> games = match.getGames();
        for (Game game : games) {
            sb.append("(;FF[4]GM[6]CA[UTF-8]AP[GammonScope]");
            sb.append("MI[length:").append(match.getMatchLength()).append(']')
              .append("[game:").append(game.getGameNumber()).append(']')
              .append("[ws:").append(game.getScore0()).append(']')
              .append("[bs:").append(game.getScore1()).append(']');
            sb.append("PW[").append(escape(match.getPlayer0())).append(']');
            sb.append("PB[").append(escape(match.getPlayer1())).append(']');
            appendSgfRoot(sb, "EV", match.getEvent());
            appendSgfRoot(sb, "PC", match.getSite());
            appendSgfRoot(sb, "RO", match.getRound());
            appendSgfRoot(sb, "DT", match.getMatchDate());
            if (game.isCrawford()) {
                sb.append("RU[Crawford]");
            }
            if (game.getWinner() >= 0 && game.getPoints() > 0) {
                sb.append("RE[").append(game.getWinner() == 1 ? 'B' : 'W').append('+')
                  .append(game.getPoints()).append(']');
            }
            sb.append('\n');

            for (MoveRecord move : game.getMoves()) {
                appendSgfMove(sb, move);
            }
            sb.append(")\n");
        }
        return sb.toString();
    }

    private void appendSgfMove(StringBuilder sb, MoveRecord move) {
        char who = move.getPlayer() == 1 ? 'B' : 'W';
        switch (move.getType()) {
            case NORMAL -> {
                sb.append(';').append(who).append('[')
                  .append(move.getDice0()).append(move.getDice1())
                  .append(encodeMoves(move)).append(']');
            }
            case DOUBLE -> sb.append(';').append(who).append("[double]");
            case TAKE -> sb.append(';').append(who).append("[take]");
            case DROP -> sb.append(';').append(who).append("[drop]");
            case RESIGN -> sb.append(';').append(who).append("[resign]");
            case SET_DICE -> sb.append(";PL[").append(who).append("]DI[")
                    .append(move.getDice0()).append(move.getDice1()).append(']');
            case SET_CUBE_VALUE -> sb.append(";CV[").append(move.getCubeValue()).append(']');
            case SET_CUBE_POS -> sb.append(";CP[")
                    .append("cwb".charAt((move.getCubeOwner() == null ? -1 : move.getCubeOwner()) + 1)).append(']');
            case SET_BOARD -> appendSgfSetup(sb, move);
            default -> { }
        }
        if (move.getComment() != null && !move.getComment().isBlank()) {
            sb.append("C[").append(escape(move.getComment())).append(']');
        }
        sb.append('\n');
    }

    private String encodeMoves(MoveRecord move) {
        int[][] pairs = parsePairs(move.getMovesInternal());
        if (pairs == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        int player = move.getPlayer();
        for (int[] pair : pairs) {
            sb.append(sgfChar(pair[0], player)).append(sgfChar(pair[1], player));
        }
        return sb.toString();
    }

    private void appendSgfSetup(StringBuilder sb, MoveRecord move) {
        int[][] checkers = parseSetupCheckers(move.getSetup());
        char who = move.getPlayer() == 1 ? 'B' : 'W';
        sb.append(";PL[").append(who).append("]AE[a:y]");
        if (checkers != null) {
            if (sumRow(checkers[0]) > 0) {
                sb.append("AW");
                for (int i = 0; i < 25; i++) {
                    for (int k = 0; k < checkers[0][i]; k++) {
                        sb.append('[').append(sgfChar(i, 0)).append(']');
                    }
                }
            }
            if (sumRow(checkers[1]) > 0) {
                sb.append("AB");
                for (int i = 0; i < 25; i++) {
                    for (int k = 0; k < checkers[1][i]; k++) {
                        sb.append('[').append(i == 24 ? 'y' : sgfChar(i, 1)).append(']');
                    }
                }
            }
        }
    }

    private char sgfChar(int idx, int player) {
        if (idx == 24) {
            return 'y';
        }
        if (idx < 0) {
            return 'z';
        }
        return player == 1 ? (char) ('x' - idx) : (char) ('a' + idx);
    }

    private int sumRow(int[] row) {
        int sum = 0;
        for (int n : row) {
            sum += n;
        }
        return sum;
    }

    private int[][] parseSetupCheckers(String setup) {
        if (setup == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(setup);
            JsonNode c = root.get("checkers");
            if (c == null || !c.isArray() || c.size() < 2) {
                return null;
            }
            int[][] out = new int[2][25];
            for (int p = 0; p < 2; p++) {
                JsonNode row = c.get(p);
                for (int i = 0; i < 25 && i < row.size(); i++) {
                    out[p][i] = row.get(i).asInt();
                }
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private int[][] parsePairs(String json) {
        if (json == null) {
            return null;
        }
        try {
            JsonNode root = objectMapper.readTree(json);
            if (!root.isArray()) {
                return null;
            }
            int[][] out = new int[root.size()][2];
            for (int i = 0; i < root.size(); i++) {
                JsonNode pair = root.get(i);
                out[i][0] = pair.get(0).asInt();
                out[i][1] = pair.get(1).asInt();
            }
            return out;
        } catch (Exception e) {
            return null;
        }
    }

    private void appendSgfRoot(StringBuilder sb, String tag, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(tag).append('[').append(escape(value)).append(']');
        }
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("]", "\\]");
    }
}
