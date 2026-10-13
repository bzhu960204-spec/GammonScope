package com.gammonscope.parser;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.stereotype.Component;

import com.gammonscope.model.MoveType;
import com.gammonscope.parser.ParsedMatch.ParsedGame;
import com.gammonscope.parser.ParsedMatch.ParsedMove;

@Component
public class MatParser implements GameFileParser {

    private static final Pattern MATCH_LEN =
            Pattern.compile("^\\s*(\\d+)\\s+point\\s+match", Pattern.CASE_INSENSITIVE);
    private static final Pattern GAME_START =
            Pattern.compile("^\\s*Game\\s+(\\d+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern SCORE_LINE =
            Pattern.compile("^\\s*(.*?)\\s*:\\s*(\\d+)\\s+(.*?)\\s*:\\s*(\\d+)");
    private static final Pattern MOVE_ROW = Pattern.compile("^\\s*(\\d+)\\)(.*)$");
    private static final Pattern META = Pattern.compile("\\[(\\w+)\\s+\"?([^\"\\]]*)\"?\\]");

    @Override
    public boolean supports(String fileName, String content) {
        if (fileName != null && fileName.toLowerCase().endsWith(".mat")) {
            return true;
        }
        return MATCH_LEN.matcher(content).find();
    }

    @Override
    public String formatTag() {
        return "MAT";
    }

    @Override
    public ParsedMatch parse(String content, String fileName) {
        ParsedMatch match = new ParsedMatch();
        match.sourceFormat = "MAT";

        String[] lines = content.split("\\r?\\n");
        int i = 0;
        boolean lengthFound = false;

        for (; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }
            if (trimmed.startsWith("#") || trimmed.startsWith(";")) {
                applyMeta(match, trimmed);
                continue;
            }
            Matcher m = MATCH_LEN.matcher(line);
            if (m.find()) {
                match.matchLength = Integer.parseInt(m.group(1));
                lengthFound = true;
                i++;
                break;
            }
        }

        if (!lengthFound) {
            throw new ImportException("Not a valid .mat file: no \"N point match\" header found.");
        }

        ParsedGame game = null;
        for (; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            if (trimmed.isEmpty()) {
                continue;
            }

            Matcher gm = GAME_START.matcher(line);
            if (gm.find()) {
                game = new ParsedGame();
                game.gameNumber = Integer.parseInt(gm.group(1));
                match.games.add(game);

                // The next non-empty line holds the player names and scores.
                int j = i + 1;
                while (j < lines.length && lines[j].trim().isEmpty()) {
                    j++;
                }
                if (j < lines.length) {
                    Matcher sm = SCORE_LINE.matcher(lines[j]);
                    if (sm.find()) {
                        setPlayerNames(match, sm.group(1).trim(), sm.group(3).trim());
                        game.score0 = Integer.parseInt(sm.group(2));
                        game.score1 = Integer.parseInt(sm.group(4));
                        i = j;
                    }
                }
                continue;
            }

            if (game == null) {
                continue;
            }

            Matcher rm = MOVE_ROW.matcher(line);
            if (rm.find()) {
                parseMoveRow(game, rm.group(2));
            }
        }

        return match;
    }

    private void setPlayerNames(ParsedMatch match, String p0, String p1) {
        if (!p0.isEmpty()) {
            match.player0 = stripRating(p0);
        }
        if (!p1.isEmpty()) {
            match.player1 = stripRating(p1);
        }
    }

    private String stripRating(String name) {
        // GridGammon mat files append ",<rating>" to the player name.
        int comma = name.indexOf(',');
        if (comma > 0) {
            String rest = name.substring(comma + 1).trim();
            if (rest.matches("\\d+")) {
                return name.substring(0, comma).trim();
            }
        }
        return name;
    }

    private void applyMeta(ParsedMatch match, String commentLine) {
        Matcher m = META.matcher(commentLine);
        if (!m.find()) {
            return;
        }
        String key = m.group(1);
        String value = m.group(2).trim();
        switch (key.toLowerCase()) {
            case "event" -> match.event = value;
            case "site" -> match.site = value;
            case "round" -> match.round = value;
            case "eventdate" -> match.matchDate = value;
            case "variation" -> match.variation = value;
            default -> {
                // ignore other metadata
            }
        }
    }

    private void parseMoveRow(ParsedGame game, String body) {
        String[] cells = splitColumns(body);
        if (!cells[0].trim().isEmpty()) {
            addCell(game, 0, cells[0].trim());
        }
        if (cells.length > 1 && !cells[1].trim().isEmpty()) {
            addCell(game, 1, cells[1].trim());
        }
    }

    /** Splits the move-row body into the two player columns, mirroring gnubg's heuristic. */
    private String[] splitColumns(String body) {
        int firstColon = body.indexOf(':');
        if (firstColon >= 0) {
            int secondColon = body.indexOf(':', firstColon + 1);
            if (secondColon > 3) {
                int cut = secondColon - 2; // start of the right cell's dice, e.g. "63:"
                // back up over the whitespace between the two columns
                while (cut > 0 && Character.isWhitespace(body.charAt(cut - 1))) {
                    cut--;
                }
                return new String[] { body.substring(0, cut), body.substring(secondColon - 2) };
            }
        }
        if (body.length() > 15) {
            int sep = body.indexOf("  ", 15);
            if (sep >= 0) {
                return new String[] { body.substring(0, sep), body.substring(sep + 2) };
            }
        }
        return new String[] { body };
    }

    private void addCell(ParsedGame game, int player, String cell) {
        if (cell.length() >= 3 && isDie(cell.charAt(0)) && isDie(cell.charAt(1)) && cell.charAt(2) == ':') {
            ParsedMove move = new ParsedMove();
            move.player = player;
            move.type = MoveType.NORMAL;
            move.dice0 = cell.charAt(0) - '0';
            move.dice1 = cell.charAt(1) - '0';
            String rest = cell.substring(3).trim();
            move.notation = rest;
            move.moves = MatNotation.parseCell(rest);
            game.moves.add(move);
            return;
        }

        String lower = cell.toLowerCase();
        if (lower.startsWith("double") || lower.startsWith("beaver") || lower.startsWith("raccoon")) {
            ParsedMove move = newAction(player, MoveType.DOUBLE);
            move.cubeValue = extractCube(cell);
            game.moves.add(move);
        } else if (lower.startsWith("take")) {
            game.moves.add(newAction(player, MoveType.TAKE));
        } else if (lower.startsWith("drop") || lower.startsWith("pass")) {
            game.moves.add(newAction(player, MoveType.DROP));
        } else if (lower.startsWith("win")) {
            int points = extractInt(cell);
            game.winner = player;
            game.points = points > 0 ? points : game.points;
        }
    }

    private ParsedMove newAction(int player, MoveType type) {
        ParsedMove move = new ParsedMove();
        move.player = player;
        move.type = type;
        return move;
    }

    private Integer extractCube(String cell) {
        int arrow = cell.indexOf("=>");
        if (arrow >= 0) {
            int v = extractInt(cell.substring(arrow));
            return v > 0 ? v : null;
        }
        return null;
    }

    private int extractInt(String s) {
        Matcher m = Pattern.compile("(\\d+)").matcher(s);
        return m.find() ? Integer.parseInt(m.group(1)) : -1;
    }

    private boolean isDie(char c) {
        return c >= '1' && c <= '6';
    }
}
