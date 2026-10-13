package com.gammonscope.parser;

import java.util.ArrayList;
import java.util.List;

/** Parses human-readable backgammon move notation (e.g. "24/18 13/10", "bar/20", "6/off(2)")
 *  into internal [from,to] index pairs. Internal indices: point P(1..24) -> P-1, bar -> 24, off -> -1. */
public final class MatNotation {

    private MatNotation() {
    }

    public static List<int[]> parseCell(String cell) {
        List<int[]> pairs = new ArrayList<>();
        if (cell == null) {
            return pairs;
        }
        for (String token : cell.trim().split("\\s+")) {
            if (token.isEmpty()) {
                continue;
            }
            parseToken(token, pairs);
        }
        return pairs;
    }

    private static void parseToken(String token, List<int[]> out) {
        int repeat = 1;
        int paren = token.indexOf('(');
        if (paren >= 0) {
            int close = token.indexOf(')', paren);
            String inner = token.substring(paren + 1, close < 0 ? token.length() : close);
            try {
                repeat = Integer.parseInt(inner.trim());
            } catch (NumberFormatException ex) {
                repeat = 1;
            }
            token = token.substring(0, paren);
        }

        String[] steps = token.split("/");
        List<Integer> points = new ArrayList<>();
        for (String step : steps) {
            Integer p = toInternal(step);
            if (p != null) {
                points.add(p);
            }
        }
        if (points.size() < 2) {
            return;
        }

        List<int[]> tokenPairs = new ArrayList<>();
        for (int i = 0; i + 1 < points.size(); i++) {
            tokenPairs.add(new int[] { points.get(i), points.get(i + 1) });
        }
        for (int r = 0; r < repeat; r++) {
            for (int[] pair : tokenPairs) {
                out.add(new int[] { pair[0], pair[1] });
            }
        }
    }

    private static Integer toInternal(String raw) {
        String s = raw.replace("*", "").trim();
        if (s.isEmpty()) {
            return null;
        }
        String lower = s.toLowerCase();
        if (lower.startsWith("bar") || lower.equals("b")) {
            return 24;
        }
        if (lower.startsWith("off") || lower.equals("o") || lower.equals("-")) {
            return -1;
        }
        try {
            int p = Integer.parseInt(s);
            if (p == 0) {
                return -1; // GridGammon numeric convention: 0 = off (bear off)
            }
            if (p == 25) {
                return 24; // GridGammon numeric convention: 25 = bar (entering point)
            }
            if (p < 1 || p > 24) {
                return null;
            }
            return p - 1;
        } catch (NumberFormatException ex) {
            return null;
        }
    }
}
