package com.gammonscope.parser;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import com.gammonscope.model.MoveType;
import com.gammonscope.parser.ParsedMatch.ParsedGame;
import com.gammonscope.parser.ParsedMatch.ParsedMove;

/** Parses GNU Backgammon SGF (GM[6]) files. Each game is a top-level (;...) tree in the collection. */
@Component
public class SgfParser implements GameFileParser {

    @Override
    public boolean supports(String fileName, String content) {
        if (fileName != null && fileName.toLowerCase().endsWith(".sgf")) {
            return true;
        }
        return content.contains("GM[6]");
    }

    @Override
    public String formatTag() {
        return "SGF";
    }

    @Override
    public ParsedMatch parse(String content, String fileName) {
        List<List<Node>> trees = new Cursor(content).parseCollection();
        if (trees.isEmpty()) {
            throw new ImportException("Not a valid .sgf file: no game trees found.");
        }

        ParsedMatch match = new ParsedMatch();
        match.sourceFormat = "SGF";
        boolean matchMetaSet = false;

        for (List<Node> nodes : trees) {
            if (nodes.isEmpty()) {
                continue;
            }
            Node root = nodes.get(0);
            if (!root.has("GM") && !root.has("MI") && !root.has("PW")) {
                continue;
            }

            ParsedGame game = new ParsedGame();
            applyRoot(match, game, root);
            if (!matchMetaSet) {
                matchMetaSet = true;
            }

            for (int i = 1; i < nodes.size(); i++) {
                applyNode(game, nodes.get(i));
            }
            deriveWinnerFromDrop(game);
            match.games.add(game);
        }

        if (match.games.isEmpty()) {
            throw new ImportException("Not a valid GNU Backgammon .sgf file.");
        }
        return match;
    }

    private void applyRoot(ParsedMatch match, ParsedGame game, Node root) {
        for (String mi : root.values("MI")) {
            int colon = mi.indexOf(':');
            if (colon < 0) {
                continue;
            }
            String key = mi.substring(0, colon).trim();
            String val = mi.substring(colon + 1).trim();
            try {
                int n = Integer.parseInt(val);
                switch (key) {
                    case "length" -> match.matchLength = n;
                    case "game" -> game.gameNumber = n;
                    case "ws" -> game.score0 = n;
                    case "bs" -> game.score1 = n;
                    default -> { }
                }
            } catch (NumberFormatException ignored) {
                // non-numeric MI entry
            }
        }

        String pw = root.first("PW");
        String pb = root.first("PB");
        if (pw != null && !pw.isEmpty()) {
            match.player0 = pw;
        }
        if (pb != null && !pb.isEmpty()) {
            match.player1 = pb;
        }
        if (root.has("EV")) {
            match.event = root.first("EV");
        }
        if (root.has("PC")) {
            match.site = root.first("PC");
        }
        if (root.has("RO")) {
            match.round = root.first("RO");
        }
        if (root.has("DT")) {
            match.matchDate = root.first("DT");
        }
        for (String rule : root.values("RU")) {
            if (rule.equalsIgnoreCase("Crawford") || rule.equalsIgnoreCase("CrawfordGame")) {
                game.crawford = true;
            } else if (rule.startsWith("Nackgammon")) {
                match.variation = "Nackgammon";
            } else if (rule.startsWith("Hypergammon")) {
                match.variation = "Hypergammon";
            }
        }
    }

    private void applyNode(ParsedGame game, Node node) {
        String moveVal = null;
        int player = 0;
        if (node.has("B")) {
            moveVal = node.first("B");
            player = 1;
        } else if (node.has("W")) {
            moveVal = node.first("W");
            player = 0;
        }

        if (moveVal != null) {
            ParsedMove move = decodeMove(player, moveVal);
            if (move != null) {
                move.comment = node.first("C");
                game.moves.add(move);
            }
            return;
        }

        if (node.has("DI")) {
            String di = node.first("DI");
            if (di != null && di.length() >= 2) {
                ParsedMove move = new ParsedMove();
                move.player = "B".equals(node.first("PL")) ? 1 : 0;
                move.type = MoveType.SET_DICE;
                move.dice0 = di.charAt(0) - '0';
                move.dice1 = di.charAt(1) - '0';
                game.moves.add(move);
            }
        }
        if (node.has("AB") || node.has("AW") || node.has("AE")) {
            ParsedMove move = new ParsedMove();
            move.type = MoveType.SET_BOARD;
            move.player = "B".equals(node.first("PL")) ? 1 : 0;
            move.setup = buildSetup(node);
            game.moves.add(move);
        }
        if (node.has("CV")) {
            ParsedMove move = new ParsedMove();
            move.type = MoveType.SET_CUBE_VALUE;
            try {
                move.cubeValue = Integer.parseInt(node.first("CV").trim());
            } catch (NumberFormatException ignored) {
                move.cubeValue = null;
            }
            if (move.cubeValue != null) {
                game.moves.add(move);
            }
        }
        if (node.has("CP")) {
            ParsedMove move = new ParsedMove();
            move.type = MoveType.SET_CUBE_POS;
            move.cubeOwner = cubeOwnerFromCP(node.first("CP"));
            game.moves.add(move);
        }
    }

    /** Builds a JSON board override from AW/AB/PL. AE clears the board, so we start from empty. */
    private String buildSetup(Node node) {
        int[][] checkers = new int[2][25];
        for (String v : node.values("AW")) {
            addSetupCheckers(checkers, 0, v);
        }
        for (String v : node.values("AB")) {
            addSetupCheckers(checkers, 1, v);
        }
        StringBuilder sb = new StringBuilder("{\"checkers\":[");
        appendRow(sb, checkers[0]);
        sb.append(',');
        appendRow(sb, checkers[1]);
        sb.append("],\"off\":[").append(bornOff(checkers[0])).append(',').append(bornOff(checkers[1]))
          .append("],\"turn\":").append("B".equals(node.first("PL")) ? 1 : 0).append('}');
        return sb.toString();
    }

    private void addSetupCheckers(int[][] checkers, int player, String value) {
        if (value == null || value.isEmpty()) {
            return;
        }
        int colon = value.indexOf(':');
        if (colon == 1 && value.length() >= 3) {
            for (char ch = value.charAt(0); ch <= value.charAt(2); ch++) {
                int idx = point(ch, player);
                if (idx >= 0 && idx <= 24) {
                    checkers[player][idx]++;
                }
            }
            return;
        }
        int idx = point(value.charAt(0), player);
        if (idx >= 0 && idx <= 24) {
            checkers[player][idx]++;
        }
    }

    private int bornOff(int[] row) {
        int sum = 0;
        for (int n : row) {
            sum += n;
        }
        return Math.max(0, 15 - sum);
    }

    private void appendRow(StringBuilder sb, int[] row) {
        sb.append('[');
        for (int i = 0; i < row.length; i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(row[i]);
        }
        sb.append(']');
    }

    private Integer cubeOwnerFromCP(String cp) {
        if (cp == null || cp.isEmpty()) {
            return -1;
        }
        char c = Character.toLowerCase(cp.charAt(0));
        return c == 'w' ? 0 : c == 'b' ? 1 : -1;
    }

    private ParsedMove decodeMove(int player, String value) {
        ParsedMove move = new ParsedMove();
        move.player = player;
        if ("double".equals(value)) {
            move.type = MoveType.DOUBLE;
            return move;
        }
        if ("take".equals(value)) {
            move.type = MoveType.TAKE;
            return move;
        }
        if ("drop".equals(value)) {
            move.type = MoveType.DROP;
            return move;
        }
        if (value.length() < 2) {
            return null;
        }

        move.type = MoveType.NORMAL;
        move.dice0 = value.charAt(0) - '0';
        move.dice1 = value.charAt(1) - '0';
        if (move.dice0 < 1 || move.dice0 > 6 || move.dice1 < 1 || move.dice1 > 6) {
            return null;
        }

        List<int[]> pairs = new ArrayList<>();
        StringBuilder notation = new StringBuilder();
        for (int i = 0; i < 4; i++) {
            int idx = 2 + 2 * i;
            if (idx + 1 >= value.length()) {
                break;
            }
            int from = point(value.charAt(idx), player);
            int to = point(value.charAt(idx + 1), player);
            pairs.add(new int[] { from, to });
            if (notation.length() > 0) {
                notation.append(' ');
            }
            notation.append(label(from)).append('/').append(label(to));
        }
        move.moves = pairs;
        move.notation = notation.toString();
        return move;
    }

    private int point(char ch, int player) {
        if (ch == 'y') {
            return 24;
        }
        if (ch >= 'a' && ch <= 'x') {
            return player == 1 ? ('x' - ch) : (ch - 'a');
        }
        return -1;
    }

    private String label(int internal) {
        if (internal == 24) {
            return "bar";
        }
        if (internal < 0) {
            return "off";
        }
        return Integer.toString(internal + 1);
    }

    private void deriveWinnerFromDrop(ParsedGame game) {
        for (int i = game.moves.size() - 1; i >= 0; i--) {
            ParsedMove m = game.moves.get(i);
            if (m.type == MoveType.DROP) {
                game.winner = 1 - m.player;
                return;
            }
        }
    }

    // --- Minimal SGF structure ---

    private static final class Node {
        final Map<String, List<String>> props = new LinkedHashMap<>();

        boolean has(String key) {
            return props.containsKey(key);
        }

        String first(String key) {
            List<String> v = props.get(key);
            return v == null || v.isEmpty() ? null : v.get(0);
        }

        List<String> values(String key) {
            return props.getOrDefault(key, List.of());
        }
    }

    private static final class Cursor {
        private final String s;
        private int i;

        Cursor(String s) {
            this.s = s;
        }

        List<List<Node>> parseCollection() {
            List<List<Node>> trees = new ArrayList<>();
            while (i < s.length()) {
                skipWs();
                if (i < s.length() && s.charAt(i) == '(') {
                    trees.add(parseTree());
                } else {
                    i++;
                }
            }
            return trees;
        }

        private List<Node> parseTree() {
            i++; // consume '('
            List<Node> nodes = new ArrayList<>();
            while (i < s.length()) {
                skipWs();
                if (i >= s.length()) {
                    break;
                }
                char c = s.charAt(i);
                if (c == ';') {
                    i++;
                    nodes.add(parseNode());
                } else if (c == '(') {
                    // Follow the first variation's main line, then skip the rest.
                    nodes.addAll(parseTree());
                    skipWs();
                    while (i < s.length() && s.charAt(i) == '(') {
                        skipTree();
                        skipWs();
                    }
                    break;
                } else if (c == ')') {
                    i++;
                    return nodes;
                } else {
                    i++;
                }
            }
            if (i < s.length() && s.charAt(i) == ')') {
                i++;
            }
            return nodes;
        }

        private void skipTree() {
            int depth = 0;
            while (i < s.length()) {
                char c = s.charAt(i);
                if (c == '[') {
                    skipValue();
                    continue;
                }
                if (c == '(') {
                    depth++;
                } else if (c == ')') {
                    depth--;
                    if (depth == 0) {
                        i++;
                        return;
                    }
                }
                i++;
            }
        }

        private Node parseNode() {
            Node node = new Node();
            while (i < s.length()) {
                skipWs();
                if (i >= s.length()) {
                    break;
                }
                char c = s.charAt(i);
                if (c >= 'A' && c <= 'Z') {
                    String ident = readIdent();
                    List<String> values = new ArrayList<>();
                    skipWs();
                    while (i < s.length() && s.charAt(i) == '[') {
                        values.add(readValue());
                        skipWs();
                    }
                    node.props.put(ident, values);
                } else {
                    break;
                }
            }
            return node;
        }

        private String readIdent() {
            StringBuilder sb = new StringBuilder();
            while (i < s.length() && Character.isUpperCase(s.charAt(i))) {
                sb.append(s.charAt(i++));
            }
            return sb.toString();
        }

        private String readValue() {
            i++; // consume '['
            StringBuilder sb = new StringBuilder();
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '\\') {
                    if (i < s.length()) {
                        char next = s.charAt(i++);
                        if (next != '\n') {
                            sb.append(next);
                        }
                    }
                } else if (c == ']') {
                    break;
                } else {
                    sb.append(c);
                }
            }
            return sb.toString();
        }

        private void skipValue() {
            i++; // consume '['
            while (i < s.length()) {
                char c = s.charAt(i++);
                if (c == '\\') {
                    i++;
                } else if (c == ']') {
                    break;
                }
            }
        }

        private void skipWs() {
            while (i < s.length() && Character.isWhitespace(s.charAt(i))) {
                i++;
            }
        }
    }
}
