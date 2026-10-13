package com.gammonscope.parser;

import org.springframework.stereotype.Component;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gammonscope.model.MoveType;
import com.gammonscope.parser.ParsedMatch.ParsedGame;
import com.gammonscope.parser.ParsedMatch.ParsedMove;

/**
 * Parses GammonScope's native JSON match format. Move notation follows the same
 * convention as .mat files (e.g. "8/5 6/5"; 25/X enters from the bar, X/0 bears off).
 */
@Component
public class JsonParser implements GameFileParser {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    public boolean supports(String fileName, String content) {
        if (fileName != null && fileName.toLowerCase().endsWith(".json")) {
            return true;
        }
        String trimmed = content.stripLeading();
        return trimmed.startsWith("{") && content.contains("\"games\"")
                && (content.contains("\"players\"") || content.contains("\"matchLength\""));
    }

    @Override
    public String formatTag() {
        return "JSON";
    }

    @Override
    public ParsedMatch parse(String content, String fileName) {
        JsonNode root;
        try {
            root = objectMapper.readTree(content);
        } catch (Exception ex) {
            throw new ImportException("Not a valid JSON file: " + ex.getMessage());
        }
        if (root == null || !root.isObject()) {
            throw new ImportException("Not a valid JSON match: expected a top-level object.");
        }

        ParsedMatch match = new ParsedMatch();
        match.sourceFormat = "JSON";

        match.event = text(root, "event");
        match.site = text(root, "site");
        match.round = text(root, "round");
        match.matchDate = text(root, "date", "matchDate");
        match.result = text(root, "result");
        if (root.hasNonNull("matchLength")) {
            match.matchLength = root.get("matchLength").asInt();
        }
        String variation = text(root, "variation");
        if (variation != null && !variation.isBlank()) {
            match.variation = variation;
        }

        JsonNode players = root.get("players");
        if (players != null && players.isObject()) {
            String white = text(players, "white", "player0");
            String black = text(players, "black", "player1");
            if (white != null && !white.isBlank()) {
                match.player0 = white;
            }
            if (black != null && !black.isBlank()) {
                match.player1 = black;
            }
        }

        JsonNode games = root.get("games");
        if (games == null || !games.isArray() || games.isEmpty()) {
            throw new ImportException("Not a valid JSON match: \"games\" array is missing or empty.");
        }

        int index = 0;
        for (JsonNode gameNode : games) {
            match.games.add(parseGame(gameNode, index++));
        }
        return match;
    }

    private ParsedGame parseGame(JsonNode gameNode, int index) {
        ParsedGame game = new ParsedGame();
        game.gameNumber = gameNode.hasNonNull("gameNumber") ? gameNode.get("gameNumber").asInt() : index + 1;
        game.crawford = gameNode.path("crawford").asBoolean(false);

        JsonNode score = gameNode.get("score");
        if (score != null && score.isArray() && score.size() >= 2) {
            game.score0 = score.get(0).asInt();
            game.score1 = score.get(1).asInt();
        }

        if (gameNode.has("winner")) {
            game.winner = resolvePlayer(gameNode.get("winner"), -1);
        }
        if (gameNode.hasNonNull("points")) {
            game.points = gameNode.get("points").asInt();
        }

        JsonNode setup = gameNode.get("setup");
        if (setup != null && !setup.isNull()) {
            game.setupBoard = setup.toString();
        }

        JsonNode moves = gameNode.get("moves");
        if (moves != null && moves.isArray()) {
            for (JsonNode moveNode : moves) {
                ParsedMove move = parseMove(moveNode);
                if (move != null) {
                    game.moves.add(move);
                }
            }
        }
        return game;
    }

    private ParsedMove parseMove(JsonNode node) {
        if (node == null || !node.isObject()) {
            return null;
        }
        ParsedMove move = new ParsedMove();
        move.player = resolvePlayer(node.get("player"), 0);
        move.type = parseType(text(node, "type"));
        move.comment = text(node, "comment");

        JsonNode dice = node.get("dice");
        if (dice != null && dice.isArray() && dice.size() >= 2) {
            move.dice0 = dice.get(0).asInt();
            move.dice1 = dice.get(1).asInt();
        }

        String play = text(node, "play", "move", "notation");
        if (play != null && !play.isBlank()) {
            move.notation = play.trim();
            move.moves = MatNotation.parseCell(play);
        }

        if (node.hasNonNull("cubeValue")) {
            move.cubeValue = node.get("cubeValue").asInt();
        }
        if (node.hasNonNull("cubeOwner")) {
            move.cubeOwner = node.get("cubeOwner").asInt();
        }
        if (node.hasNonNull("resign")) {
            move.resigned = node.get("resign").asInt();
        }

        JsonNode setup = node.get("setup");
        if (setup != null && !setup.isNull()) {
            move.setup = setup.toString();
        }
        return move;
    }

    private MoveType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return MoveType.NORMAL;
        }
        String normalized = raw.trim().toUpperCase().replace('-', '_').replace(' ', '_');
        return switch (normalized) {
            case "DOUBLE" -> MoveType.DOUBLE;
            case "TAKE" -> MoveType.TAKE;
            case "DROP", "PASS" -> MoveType.DROP;
            case "RESIGN" -> MoveType.RESIGN;
            case "SET_DICE", "SETDICE" -> MoveType.SET_DICE;
            case "SET_BOARD", "SETBOARD" -> MoveType.SET_BOARD;
            case "SET_CUBE_VALUE", "SETCUBEVALUE" -> MoveType.SET_CUBE_VALUE;
            case "SET_CUBE_POS", "SETCUBEPOS" -> MoveType.SET_CUBE_POS;
            default -> MoveType.NORMAL;
        };
    }

    /** Resolves a player reference ("white"/"black", "player0"/"player1", or 0/1) to an index. */
    private int resolvePlayer(JsonNode node, int fallback) {
        if (node == null || node.isNull()) {
            return fallback;
        }
        if (node.isInt() || node.isNumber()) {
            return node.asInt() == 1 ? 1 : 0;
        }
        String s = node.asText("").trim().toLowerCase();
        return switch (s) {
            case "black", "player1", "p1", "1", "o" -> 1;
            case "white", "player0", "p0", "0", "x" -> 0;
            default -> fallback;
        };
    }

    private String text(JsonNode node, String... keys) {
        for (String key : keys) {
            JsonNode value = node.get(key);
            if (value != null && value.isValueNode() && !value.isNull()) {
                String s = value.asText();
                if (s != null && !s.isEmpty()) {
                    return s;
                }
            }
        }
        return null;
    }
}
