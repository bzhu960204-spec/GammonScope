package com.gammonscope.eval;

import java.util.ArrayList;
import java.util.List;

/**
 * A faithful port of the parts of gnubg's {@code eval.c}, {@code lib/inputs.c}
 * and {@code lib/neuralnet.c} needed for a 0-ply cubeless (money) move hint:
 * position classification, neural-net input encoding, static evaluation, legal
 * move generation and move scoring.
 *
 * Board representation matches gnubg: {@code board[2][25]} where {@code board[1]}
 * is the player on roll and {@code board[0]} the opponent, each from that
 * player's own perspective (index 0 = ace point, 23 = 24-point, 24 = bar).
 *
 * Bearoff databases are not used, so positions that gnubg would resolve from a
 * bearoff database are evaluated with the race network instead.
 */
public final class GnubgEval {

    public static final int OUTPUT_WIN = 0;
    public static final int OUTPUT_WINGAMMON = 1;
    public static final int OUTPUT_WINBACKGAMMON = 2;
    public static final int OUTPUT_LOSEGAMMON = 3;
    public static final int OUTPUT_LOSEBACKGAMMON = 4;
    private static final int NUM_OUTPUTS = 5;

    /* Contact input indices (see enum in eval.c). */
    private static final int I_OFF1 = 0;
    private static final int I_BREAK_CONTACT = 3;
    private static final int I_BACK_CHEQUER = 4;
    private static final int I_BACK_ANCHOR = 5;
    private static final int I_FORWARD_ANCHOR = 6;
    private static final int I_PIPLOSS = 7;
    private static final int I_P1 = 8;
    private static final int I_P2 = 9;
    private static final int I_BACKESCAPES = 10;
    private static final int I_ACONTAIN = 11;
    private static final int I_ACONTAIN2 = 12;
    private static final int I_CONTAIN = 13;
    private static final int I_CONTAIN2 = 14;
    private static final int I_MOBILITY = 15;
    private static final int I_MOMENT2 = 16;
    private static final int I_ENTER = 17;
    private static final int I_ENTER2 = 18;
    private static final int I_TIMING = 19;
    private static final int I_BACKBONE = 20;
    private static final int I_BACKG = 21;
    private static final int I_BACKG1 = 22;
    private static final int I_FREEPIP = 23;
    private static final int I_BACKRESCAPES = 24;
    private static final int MORE_INPUTS = 25;

    private static final int RI_OFF = 92;
    private static final int RI_NCROSS = 106;
    private static final int HALF_RACE_INPUTS = 107;

    public enum PositionClass { OVER, RACE, CRASHED, CONTACT }

    private final NeuralNet nnContact;
    private final NeuralNet nnRace;
    private final NeuralNet nnCrashed;

    public GnubgEval(Weights weights) {
        this.nnContact = weights.contact;
        this.nnRace = weights.race;
        this.nnCrashed = weights.crashed;
    }

    /** A candidate move: sub-moves and the resulting evaluation. */
    public static final class ScoredMove {
        public final int[][] subMoves;   // [ [from,to], ... ] in mover's own coords (24=bar, -1=off)
        public final String notation;
        public final float[] output;     // 5 outputs from the mover's perspective
        public final float equity;        // cubeless money equity for the mover

        ScoredMove(int[][] subMoves, String notation, float[] output, float equity) {
            this.subMoves = subMoves;
            this.notation = notation;
            this.output = output;
            this.equity = equity;
        }
    }

    /**
     * Produce a ranked list of legal moves for {@code board} (board[1] on roll)
     * given dice {@code d0}/{@code d1}, best (highest equity) first.
     */
    public List<ScoredMove> hint(int[][] board, int d0, int d1) {
        List<Move> moves = generateMoves(board, d0, d1);
        List<ScoredMove> scored = new ArrayList<>(moves.size());

        for (Move m : moves) {
            int[][] swapped = swapSides(m.result);
            float[] out = evaluatePosition(swapped);
            invertEvaluation(out);
            float equity = out[OUTPUT_WIN] * 2.0f - 1.0f
                    + (out[OUTPUT_WINGAMMON] - out[OUTPUT_LOSEGAMMON])
                    + (out[OUTPUT_WINBACKGAMMON] - out[OUTPUT_LOSEBACKGAMMON]);
            scored.add(new ScoredMove(m.publicSubMoves(), m.notation(board), out, equity));
        }

        scored.sort((a, b) -> Float.compare(b.equity, a.equity));
        return scored;
    }

    /** Static 0-ply evaluation of {@code board} (board[1] on roll). */
    public float[] evaluatePosition(int[][] board) {
        float[] out = new float[NUM_OUTPUTS];
        PositionClass pc = classifyPosition(board);
        switch (pc) {
            case OVER:
                evalOver(board, out);
                break;
            case RACE: {
                float[] in = new float[2 * HALF_RACE_INPUTS];
                calculateRaceInputs(board, in);
                nnRace.evaluate(in, out);
                sanityCheck(board, out);
                break;
            }
            case CRASHED: {
                float[] in = new float[2 * (25 * 4 + MORE_INPUTS)];
                calculateCrashedInputs(board, in);
                nnCrashed.evaluate(in, out);
                sanityCheck(board, out);
                break;
            }
            case CONTACT:
            default: {
                float[] in = new float[2 * (25 * 4 + MORE_INPUTS)];
                calculateContactInputs(board, in);
                nnContact.evaluate(in, out);
                sanityCheck(board, out);
                break;
            }
        }
        return out;
    }

    public PositionClass classifyPosition(int[][] anBoard) {
        int nOppBack;
        for (nOppBack = 24; nOppBack >= 0; --nOppBack) {
            if (anBoard[0][nOppBack] != 0) {
                break;
            }
        }
        int nBack;
        for (nBack = 24; nBack >= 0; --nBack) {
            if (anBoard[1][nBack] != 0) {
                break;
            }
        }
        if (nBack < 0 || nOppBack < 0) {
            return PositionClass.OVER;
        }

        if (nBack + nOppBack > 22) {
            final int N = 6;
            for (int side = 0; side < 2; ++side) {
                int[] board = anBoard[side];
                int tot = 0;
                for (int i = 0; i < 25; ++i) {
                    tot += board[i];
                }
                if (tot <= N) {
                    return PositionClass.CRASHED;
                } else if (board[0] > 1) {
                    if (tot <= N + board[0]) {
                        return PositionClass.CRASHED;
                    } else if ((1 + tot - (board[0] + board[1])) <= N && board[1] > 1) {
                        return PositionClass.CRASHED;
                    }
                } else {
                    if (tot <= N + (board[1] - 1)) {
                        return PositionClass.CRASHED;
                    }
                }
            }
            return PositionClass.CONTACT;
        }
        return PositionClass.RACE;
    }

    /* ------------------------------------------------------------------ */
    /* Input encoding                                                      */
    /* ------------------------------------------------------------------ */

    private static void baseInputs(int[][] anBoard, float[] arInput) {
        for (int j = 0; j < 2; ++j) {
            int off = j * 25 * 4;
            int[] board = anBoard[j];
            for (int i = 0; i < 24; i++) {
                int nc = Math.min(board[i], 15);
                arInput[off + i * 4] = INPVEC[nc][0];
                arInput[off + i * 4 + 1] = INPVEC[nc][1];
                arInput[off + i * 4 + 2] = INPVEC[nc][2];
                arInput[off + i * 4 + 3] = INPVEC[nc][3];
            }
            int nc = Math.min(board[24], 15);
            arInput[off + 96] = INPVECB[nc][0];
            arInput[off + 97] = INPVECB[nc][1];
            arInput[off + 98] = INPVECB[nc][2];
            arInput[off + 99] = INPVECB[nc][3];
        }
    }

    private static void menOffAll(int[] anBoard, float[] arInput, int base) {
        int menOff = 15;
        for (int i = 0; i < 25; i++) {
            menOff -= anBoard[i];
        }
        if (menOff <= 5) {
            arInput[base] = menOff != 0 ? menOff / 5.0f : 0.0f;
            arInput[base + 1] = 0.0f;
            arInput[base + 2] = 0.0f;
        } else if (menOff <= 10) {
            arInput[base] = 1.0f;
            arInput[base + 1] = (menOff - 5) / 5.0f;
            arInput[base + 2] = 0.0f;
        } else {
            arInput[base] = 1.0f;
            arInput[base + 1] = 1.0f;
            arInput[base + 2] = (menOff - 10) / 5.0f;
        }
    }

    private static void menOffNonCrashed(int[] anBoard, float[] arInput, int base) {
        int menOff = 15;
        for (int i = 0; i < 25; ++i) {
            menOff -= anBoard[i];
        }
        if (menOff <= 2) {
            arInput[base] = menOff != 0 ? menOff / 3.0f : 0.0f;
            arInput[base + 1] = 0.0f;
            arInput[base + 2] = 0.0f;
        } else if (menOff <= 5) {
            arInput[base] = 1.0f;
            arInput[base + 1] = (menOff - 3) / 3.0f;
            arInput[base + 2] = 0.0f;
        } else {
            arInput[base] = 1.0f;
            arInput[base + 1] = 1.0f;
            arInput[base + 2] = (menOff - 6) / 3.0f;
        }
    }

    private static void calculateContactInputs(int[][] anBoard, float[] arInput) {
        baseInputs(anBoard, arInput);
        int b1 = 25 * 4 * 2;
        menOffNonCrashed(anBoard[0], arInput, b1 + I_OFF1);
        calculateHalfInputs(anBoard[1], anBoard[0], arInput, b1);
        int b2 = 25 * 4 * 2 + MORE_INPUTS;
        menOffNonCrashed(anBoard[1], arInput, b2 + I_OFF1);
        calculateHalfInputs(anBoard[0], anBoard[1], arInput, b2);
    }

    private static void calculateCrashedInputs(int[][] anBoard, float[] arInput) {
        baseInputs(anBoard, arInput);
        int b1 = 25 * 4 * 2;
        menOffAll(anBoard[1], arInput, b1 + I_OFF1);
        calculateHalfInputs(anBoard[1], anBoard[0], arInput, b1);
        int b2 = 25 * 4 * 2 + MORE_INPUTS;
        menOffAll(anBoard[0], arInput, b2 + I_OFF1);
        calculateHalfInputs(anBoard[0], anBoard[1], arInput, b2);
    }

    private static void calculateRaceInputs(int[][] anBoard, float[] inputs) {
        for (int side = 0; side < 2; ++side) {
            int[] board = anBoard[side];
            int off = side * HALF_RACE_INPUTS;
            int menOff = 15;
            for (int i = 0; i < 23; ++i) {
                int nc = board[i];
                int k = i * 4;
                menOff -= nc;
                inputs[off + k] = (nc == 1) ? 1.0f : 0.0f;
                inputs[off + k + 1] = (nc == 2) ? 1.0f : 0.0f;
                inputs[off + k + 2] = (nc >= 3) ? 1.0f : 0.0f;
                inputs[off + k + 3] = nc > 3 ? (nc - 3) / 2.0f : 0.0f;
            }
            for (int k = 0; k < 14; ++k) {
                inputs[off + RI_OFF + k] = (menOff == (k + 1)) ? 1.0f : 0.0f;
            }
            int nCross = 0;
            for (int k = 1; k < 4; ++k) {
                for (int i = 6 * k; i < 6 * k + 6; ++i) {
                    int nc = board[i];
                    if (nc != 0) {
                        nCross += nc * k;
                    }
                }
            }
            inputs[off + RI_NCROSS] = nCross / 10.0f;
        }
    }

    private static int msb32(int n) {
        return 31 - Integer.numberOfLeadingZeros(n);
    }

    private static void calculateHalfInputs(int[] anBoard, int[] anBoardOpp, float[] af, int base) {
        int i, j, k, l, nOppBack, n;

        for (nOppBack = 24; nOppBack >= 0; --nOppBack) {
            if (anBoardOpp[nOppBack] != 0) {
                break;
            }
        }
        nOppBack = 23 - nOppBack;

        {
            int np = 0;
            for (i = nOppBack + 1; i < 25; i++) {
                if (anBoard[i] != 0) {
                    np += (i + 1 - nOppBack) * anBoard[i];
                }
            }
            af[base + I_BREAK_CONTACT] = np / (15 + 152.0f);
        }
        {
            int p = 0;
            for (i = 0; i < nOppBack; i++) {
                if (anBoard[i] != 0) {
                    p += (i + 1) * anBoard[i];
                }
            }
            af[base + I_FREEPIP] = p / 100.0f;
        }
        {
            int t = 0;
            int no = 0;
            int m = (nOppBack >= 11) ? nOppBack : 11;

            t += 24 * anBoard[24];
            no += anBoard[24];

            for (i = 23; i > m; --i) {
                if (anBoard[i] != 0 && anBoard[i] != 2) {
                    int ns = (anBoard[i] > 2) ? (anBoard[i] - 2) : 1;
                    no += ns;
                    t += i * ns;
                }
            }
            for (; i >= 6; --i) {
                if (anBoard[i] != 0) {
                    int nc = anBoard[i];
                    no += nc;
                    t += i * nc;
                }
            }
            for (i = 5; i >= 0; --i) {
                if (anBoard[i] > 2) {
                    t += i * (anBoard[i] - 2);
                    no += (anBoard[i] - 2);
                } else if (anBoard[i] < 2) {
                    int nm = (2 - anBoard[i]);
                    if (no >= nm) {
                        t -= i * nm;
                        no -= nm;
                    }
                }
            }
            af[base + I_TIMING] = t / 100.0f;
        }

        {
            int nBack;
            for (nBack = 24; nBack >= 0; --nBack) {
                if (anBoard[nBack] != 0) {
                    break;
                }
            }
            af[base + I_BACK_CHEQUER] = nBack / 24.0f;

            for (i = ((nBack == 24) ? 23 : nBack); i >= 0; --i) {
                if (anBoard[i] >= 2) {
                    break;
                }
            }
            af[base + I_BACK_ANCHOR] = i / 24.0f;

            n = 0;
            for (j = 18; j <= i; ++j) {
                if (anBoard[j] >= 2) {
                    n = 24 - j;
                    break;
                }
            }
            if (n == 0) {
                for (j = 17; j >= 12; --j) {
                    if (anBoard[j] >= 2) {
                        n = 24 - j;
                        break;
                    }
                }
            }
            af[base + I_FORWARD_ANCHOR] = n == 0 ? 2.0f : n / 6.0f;
        }

        /* Piploss */
        int nBoard = 0;
        for (i = 0; i < 6; i++) {
            if (anBoard[i] >= 2) {
                nBoard++;
            }
        }

        int[] aHit = new int[39];

        for (i = (nBoard > 2) ? 23 : 21; i >= 0; i--) {
            if (anBoardOpp[i] == 1) {
                for (j = 24 - i; j < 25; j++) {
                    if (anBoard[j] != 0 && !(j < 6 && anBoard[j] == 2)) {
                        for (n = 0; n < 5; n++) {
                            int comb = AAN_COMBINATION[j - 24 + i][n];
                            if (comb == -1) {
                                break;
                            }
                            int[] pi = A_INTERMEDIATE[comb];
                            boolean cannotHit = false;
                            if (pi[0] != 0) { /* fAll */
                                if (pi[4] > 1) { /* nFaces */
                                    for (k = 0; k < 3 && pi[1 + k] > 0; k++) {
                                        if (anBoardOpp[i - pi[1 + k]] > 1) {
                                            cannotHit = true;
                                            break;
                                        }
                                    }
                                }
                            } else {
                                if (anBoardOpp[i - pi[1]] > 1 && anBoardOpp[i - pi[2]] > 1) {
                                    cannotHit = true;
                                }
                            }
                            if (cannotHit) {
                                continue;
                            }
                            aHit[comb] |= 1 << j;
                        }
                    }
                }
            }
        }

        int[] aRollChequers = new int[21];
        int[] aRollPips = new int[21];

        if (anBoard[24] == 0) {
            for (i = 0; i < 21; i++) {
                n = -1;
                for (j = 0; j < 4; j++) {
                    int r = AA_ROLL[i][j];
                    if (r < 0) {
                        break;
                    }
                    if (aHit[r] == 0) {
                        continue;
                    }
                    int[] pi = A_INTERMEDIATE[r];
                    if (pi[4] == 1) {
                        k = msb32(aHit[r]);
                        if (n != k || anBoard[k] > 1) {
                            aRollChequers[i]++;
                        }
                        n = k;
                        if (k - pi[5] + 1 > aRollPips[i]) {
                            aRollPips[i] = k - pi[5] + 1;
                        }
                        if (AA_ROLL[i][3] >= 0 && (aHit[r] & ~(1 << k)) != 0) {
                            aRollChequers[i]++;
                        }
                    } else {
                        if (aRollChequers[i] == 0) {
                            aRollChequers[i] = 1;
                        }
                        k = msb32(aHit[r]);
                        if (k - pi[5] + 1 > aRollPips[i]) {
                            aRollPips[i] = k - pi[5] + 1;
                        }
                        for (l = 0; l < 3 && pi[1 + l] > 0; l++) {
                            if (anBoardOpp[23 - k + pi[1 + l]] == 1) {
                                aRollChequers[i]++;
                                break;
                            }
                        }
                    }
                }
            }
        } else if (anBoard[24] == 1) {
            for (i = 0; i < 21; i++) {
                n = 0;
                for (j = 0; j < 4; j++) {
                    int r = AA_ROLL[i][j];
                    if (r < 0) {
                        break;
                    }
                    if (aHit[r] == 0) {
                        continue;
                    }
                    int[] pi = A_INTERMEDIATE[r];
                    if (pi[4] == 1) {
                        for (k = msb32(aHit[r]); k > 0; k--) {
                            if ((aHit[r] & (1 << k)) != 0) {
                                if (n != 0 && k != 24) {
                                    break;
                                }
                                if (k != 24) {
                                    int npip = A_INTERMEDIATE[AA_ROLL[i][1 - j]][5];
                                    if (anBoardOpp[npip - 1] > 1) {
                                        break;
                                    }
                                    n = 1;
                                }
                                aRollChequers[i]++;
                                if (k - pi[5] + 1 > aRollPips[i]) {
                                    aRollPips[i] = k - pi[5] + 1;
                                }
                            }
                        }
                    } else {
                        if ((aHit[r] & (1 << 24)) == 0) {
                            continue;
                        }
                        if (aRollChequers[i] == 0) {
                            aRollChequers[i] = 1;
                        }
                        if (25 - pi[5] > aRollPips[i]) {
                            aRollPips[i] = 25 - pi[5];
                        }
                        for (k = 0; k < 3 && pi[1 + k] > 0; k++) {
                            if (anBoardOpp[pi[1 + k] + 1] == 1) {
                                aRollChequers[i]++;
                                break;
                            }
                        }
                    }
                }
            }
        } else {
            for (i = 0; i < 21; i++) {
                for (j = 0; j < 2; j++) {
                    int r = AA_ROLL[i][j];
                    if ((aHit[r] & (1 << 24)) == 0) {
                        continue;
                    }
                    int[] pi = A_INTERMEDIATE[r];
                    if (pi[4] != 1) {
                        continue;
                    }
                    aRollChequers[i]++;
                    if (25 - pi[5] > aRollPips[i]) {
                        aRollPips[i] = 25 - pi[5];
                    }
                }
            }
        }

        {
            int np = 0;
            int n1 = 0;
            int n2 = 0;
            for (i = 0; i < 6; i++) {
                int nc = aRollChequers[i];
                np += aRollPips[i];
                if (nc > 0) {
                    n1 += 1;
                    if (nc > 1) {
                        n2 += 1;
                    }
                }
            }
            for (; i < 21; i++) {
                int nc = aRollChequers[i];
                np += aRollPips[i] * 2;
                if (nc > 0) {
                    n1 += 2;
                    if (nc > 1) {
                        n2 += 2;
                    }
                }
            }
            af[base + I_PIPLOSS] = np / (12.0f * 36.0f);
            af[base + I_P1] = n1 / 36.0f;
            af[base + I_P2] = n2 / 36.0f;
        }

        af[base + I_BACKESCAPES] = escapes(anBoard, 23 - nOppBack) / 36.0f;
        af[base + I_BACKRESCAPES] = escapes1(anBoard, 23 - nOppBack) / 36.0f;

        for (n = 36, i = 15; i < 24 - nOppBack; i++) {
            if ((j = escapes(anBoard, i)) < n) {
                n = j;
            }
        }
        af[base + I_ACONTAIN] = (36 - n) / 36.0f;
        af[base + I_ACONTAIN2] = af[base + I_ACONTAIN] * af[base + I_ACONTAIN];

        if (nOppBack < 0) {
            i = 15;
            n = 36;
        }
        for (; i < 24; i++) {
            if ((j = escapes(anBoard, i)) < n) {
                n = j;
            }
        }
        af[base + I_CONTAIN] = (36 - n) / 36.0f;
        af[base + I_CONTAIN2] = af[base + I_CONTAIN] * af[base + I_CONTAIN];

        for (n = 0, i = 6; i < 25; i++) {
            if (anBoard[i] != 0) {
                n += (i - 5) * anBoard[i] * escapes(anBoardOpp, i);
            }
        }
        af[base + I_MOBILITY] = n / 3600.0f;

        j = 0;
        n = 0;
        for (i = 0; i < 25; i++) {
            int ni = anBoard[i];
            if (ni != 0) {
                j += ni;
                n += i * ni;
            }
        }
        n = (n + j - 1) / j;
        j = 0;
        k = 0;
        for (i = n + 1; i < 25; i++) {
            int ni = anBoard[i];
            if (ni != 0) {
                j += ni;
                k += ni * (i - n) * (i - n);
            }
        }
        if (j != 0) {
            k = (k + j - 1) / j;
        }
        af[base + I_MOMENT2] = k / 400.0f;

        if (anBoard[24] > 0) {
            int loss = 0;
            boolean two = anBoard[24] > 1;
            for (i = 0; i < 6; ++i) {
                if (anBoardOpp[i] > 1) {
                    loss += 4 * (i + 1);
                    for (j = i + 1; j < 6; ++j) {
                        if (anBoardOpp[j] > 1) {
                            loss += 2 * (i + j + 2);
                        } else if (two) {
                            loss += 2 * (i + 1);
                        }
                    }
                } else if (two) {
                    for (j = i + 1; j < 6; ++j) {
                        if (anBoardOpp[j] > 1) {
                            loss += 2 * (j + 1);
                        }
                    }
                }
            }
            af[base + I_ENTER] = loss / (36.0f * (49.0f / 6.0f));
        } else {
            af[base + I_ENTER] = 0.0f;
        }

        n = 0;
        for (i = 0; i < 6; i++) {
            n += anBoardOpp[i] > 1 ? 1 : 0;
        }
        af[base + I_ENTER2] = (36 - (n - 6) * (n - 6)) / 36.0f;

        {
            int pa = -1;
            int w = 0;
            int tot = 0;
            for (int np = 23; np > 0; --np) {
                if (anBoard[np] >= 2) {
                    if (pa == -1) {
                        pa = np;
                        continue;
                    }
                    int d = pa - np;
                    w += AC_BACKBONE[d] * anBoard[pa];
                    tot += anBoard[pa];
                }
            }
            af[base + I_BACKBONE] = tot != 0 ? 1.0f - (w / (tot * 11.0f)) : 0.0f;
        }

        {
            int nAc = 0;
            for (i = 18; i < 24; ++i) {
                if (anBoard[i] > 1) {
                    ++nAc;
                }
            }
            af[base + I_BACKG] = 0.0f;
            af[base + I_BACKG1] = 0.0f;
            if (nAc >= 1) {
                int tot = 0;
                for (i = 18; i < 25; ++i) {
                    tot += anBoard[i];
                }
                if (nAc > 1) {
                    af[base + I_BACKG] = (tot - 3) / 4.0f;
                } else {
                    af[base + I_BACKG1] = tot / 8.0f;
                }
            }
        }
    }

    private static int escapes(int[] anBoard, int n) {
        int af = 0;
        int m = (n < 12) ? n : 12;
        for (int i = 0; i < m; i++) {
            af |= AN_POINT[anBoard[24 + i - n]] << i;
        }
        return AN_ESCAPES[af];
    }

    private static int escapes1(int[] anBoard, int n) {
        int af = 0;
        int m = (n < 12) ? n : 12;
        for (int i = 0; i < m; i++) {
            af |= AN_POINT[anBoard[24 + i - n]] << i;
        }
        return AN_ESCAPES1[af];
    }

    /* ------------------------------------------------------------------ */
    /* Static evaluation of terminal positions and sanity adjustments      */
    /* ------------------------------------------------------------------ */

    private static void evalOver(int[][] anBoard, float[] arOutput) {
        final int n = 15;
        int i;
        for (i = 0; i < 25; i++) {
            if (anBoard[0][i] != 0) {
                break;
            }
        }
        if (i == 25) {
            arOutput[OUTPUT_WIN] = arOutput[OUTPUT_WINGAMMON] = arOutput[OUTPUT_WINBACKGAMMON] = 0.0f;
            int c = 0;
            for (i = 0; i < 25; i++) {
                c += anBoard[1][i];
            }
            if (c == n) {
                arOutput[OUTPUT_LOSEGAMMON] = 1.0f;
                for (i = 18; i < 25; i++) {
                    if (anBoard[1][i] != 0) {
                        arOutput[OUTPUT_LOSEBACKGAMMON] = 1.0f;
                        return;
                    }
                }
                arOutput[OUTPUT_LOSEBACKGAMMON] = 0.0f;
                return;
            }
            arOutput[OUTPUT_LOSEGAMMON] = arOutput[OUTPUT_LOSEBACKGAMMON] = 0.0f;
            return;
        }
        for (i = 0; i < 25; i++) {
            if (anBoard[1][i] != 0) {
                break;
            }
        }
        if (i == 25) {
            arOutput[OUTPUT_WIN] = 1.0f;
            arOutput[OUTPUT_LOSEGAMMON] = arOutput[OUTPUT_LOSEBACKGAMMON] = 0.0f;
            int c = 0;
            for (i = 0; i < 25; i++) {
                c += anBoard[0][i];
            }
            if (c == n) {
                arOutput[OUTPUT_WINGAMMON] = 1.0f;
                for (i = 18; i < 25; i++) {
                    if (anBoard[0][i] != 0) {
                        arOutput[OUTPUT_WINBACKGAMMON] = 1.0f;
                        return;
                    }
                }
                arOutput[OUTPUT_WINBACKGAMMON] = 0.0f;
                return;
            }
            arOutput[OUTPUT_WINGAMMON] = arOutput[OUTPUT_WINBACKGAMMON] = 0.0f;
        }
    }

    private static void sanityCheck(int[][] anBoard, float[] arOutput) {
        int[] ac = new int[2];
        int[] anBack = new int[2];
        int[] anCross = new int[2];
        int[] anGammonCross = {1, 1};
        int[] anMaxTurns = new int[2];

        for (int j = 0; j < 2; j++) {
            int nciq = 0;
            for (int i = 0; i < 6; i++) {
                if (anBoard[j][i] != 0) {
                    anBack[j] = i;
                    nciq += anBoard[j][i];
                }
            }
            ac[j] = anCross[j] = nciq;

            nciq = 0;
            for (int i = 6; i < 12; i++) {
                if (anBoard[j][i] != 0) {
                    anBack[j] = i;
                    nciq += anBoard[j][i];
                }
            }
            ac[j] += nciq;
            anCross[j] += 2 * nciq;
            anGammonCross[j] += nciq;

            nciq = 0;
            for (int i = 12; i < 18; i++) {
                if (anBoard[j][i] != 0) {
                    anBack[j] = i;
                    nciq += anBoard[j][i];
                }
            }
            ac[j] += nciq;
            anCross[j] += 3 * nciq;
            anGammonCross[j] += 2 * nciq;

            nciq = 0;
            for (int i = 18; i < 24; i++) {
                if (anBoard[j][i] != 0) {
                    anBack[j] = i;
                    nciq += anBoard[j][i];
                }
            }
            ac[j] += nciq;
            anCross[j] += 4 * nciq;
            anGammonCross[j] += 3 * nciq;

            if (anBoard[j][24] != 0) {
                anBack[j] = 24;
                ac[j] += anBoard[j][24];
                anCross[j] += 5 * anBoard[j][24];
                anGammonCross[j] += 4 * anBoard[j][24];
            }
        }

        boolean fContact = anBack[0] + anBack[1] >= 24;

        if (!fContact) {
            for (int i = 0; i < 2; i++) {
                anMaxTurns[i] = anCross[i] * 2;
            }
            if (anMaxTurns[1] == 0) {
                anMaxTurns[1] = 1;
            }
        }

        if (!fContact && anCross[0] > 4 * (anMaxTurns[1] - 1)) {
            arOutput[OUTPUT_WIN] = 1.0f;
        }

        if (ac[0] < 15) {
            arOutput[OUTPUT_WINGAMMON] = arOutput[OUTPUT_WINBACKGAMMON] = 0.0f;
        } else if (!fContact) {
            if (anCross[1] > 8 * anGammonCross[0]) {
                arOutput[OUTPUT_WINGAMMON] = 0.0f;
            } else if (anGammonCross[0] > 4 * (anMaxTurns[1] - 1)) {
                arOutput[OUTPUT_WINGAMMON] = 1.0f;
            }
            if (anBack[0] < 18) {
                arOutput[OUTPUT_WINBACKGAMMON] = 0.0f;
            }
        }

        if (!fContact && anCross[1] > 4 * anMaxTurns[0]) {
            arOutput[OUTPUT_WIN] = 0.0f;
        }

        if (ac[1] < 15) {
            arOutput[OUTPUT_LOSEGAMMON] = arOutput[OUTPUT_LOSEBACKGAMMON] = 0.0f;
        } else if (!fContact) {
            if (anCross[0] > 8 * anGammonCross[1] - 4) {
                arOutput[OUTPUT_LOSEGAMMON] = 0.0f;
            } else if (anGammonCross[1] > 4 * anMaxTurns[0]) {
                arOutput[OUTPUT_LOSEGAMMON] = 1.0f;
            }
            if (anBack[1] < 18) {
                arOutput[OUTPUT_LOSEBACKGAMMON] = 0.0f;
            }
        }

        if (arOutput[OUTPUT_WINGAMMON] > arOutput[OUTPUT_WIN]) {
            arOutput[OUTPUT_WINGAMMON] = arOutput[OUTPUT_WIN];
        }
        float lose = 1.0f - arOutput[OUTPUT_WIN];
        if (arOutput[OUTPUT_LOSEGAMMON] > lose) {
            arOutput[OUTPUT_LOSEGAMMON] = lose;
        }
        if (arOutput[OUTPUT_WINBACKGAMMON] > arOutput[OUTPUT_WINGAMMON]) {
            arOutput[OUTPUT_WINBACKGAMMON] = arOutput[OUTPUT_WINGAMMON];
        }
        if (arOutput[OUTPUT_LOSEBACKGAMMON] > arOutput[OUTPUT_LOSEGAMMON]) {
            arOutput[OUTPUT_LOSEBACKGAMMON] = arOutput[OUTPUT_LOSEGAMMON];
        }

        if (fContact) {
            float noise = 1 / 10000.0f;
            for (int i = OUTPUT_WINGAMMON; i < NUM_OUTPUTS; ++i) {
                if (arOutput[i] < noise) {
                    arOutput[i] = 0.0f;
                }
            }
        }
    }

    private static void invertEvaluation(float[] ar) {
        ar[OUTPUT_WIN] = 1.0f - ar[OUTPUT_WIN];
        float r = ar[OUTPUT_WINGAMMON];
        ar[OUTPUT_WINGAMMON] = ar[OUTPUT_LOSEGAMMON];
        ar[OUTPUT_LOSEGAMMON] = r;
        r = ar[OUTPUT_WINBACKGAMMON];
        ar[OUTPUT_WINBACKGAMMON] = ar[OUTPUT_LOSEBACKGAMMON];
        ar[OUTPUT_LOSEBACKGAMMON] = r;
    }

    private static int[][] swapSides(int[][] board) {
        return new int[][] {board[1].clone(), board[0].clone()};
    }

    /* ------------------------------------------------------------------ */
    /* Move generation                                                     */
    /* ------------------------------------------------------------------ */

    private static final class Move {
        final int[] anMove;      // up to 8 ints: pairs of (from, to), unused = -1
        final int cMoves;
        final int cPips;
        final int[][] result;    // board after move, mover still board[1]
        final boolean[] hit;     // hit flag per sub-move

        Move(int[] anMove, int cMoves, int cPips, int[][] result, boolean[] hit) {
            this.anMove = anMove;
            this.cMoves = cMoves;
            this.cPips = cPips;
            this.result = result;
            this.hit = hit;
        }

        int[][] publicSubMoves() {
            List<int[]> list = new ArrayList<>();
            for (int i = 0; i < 8 && anMove[i] >= 0; i += 2) {
                int from = anMove[i];
                int to = anMove[i + 1];
                list.add(new int[] {from, to < 0 ? -1 : to});
            }
            return list.toArray(new int[0][]);
        }

        String notation(int[][] startBoard) {
            // Recompute hits by replaying against a copy to label '*'.
            int[][] b = new int[][] {startBoard[0].clone(), startBoard[1].clone()};
            StringBuilder sb = new StringBuilder();
            List<String> tokens = new ArrayList<>();
            for (int i = 0; i < 8 && anMove[i] >= 0; i += 2) {
                int from = anMove[i];
                int to = anMove[i + 1];
                boolean isHit = to >= 0 && b[0][23 - to] == 1;
                applyRaw(b, from, to);
                String fromLabel = from == 24 ? "bar" : Integer.toString(from + 1);
                String toLabel = to < 0 ? "off" : Integer.toString(to + 1);
                tokens.add(fromLabel + "/" + toLabel + (isHit ? "*" : ""));
            }
            // Combine identical tokens into "token(n)".
            int idx = 0;
            while (idx < tokens.size()) {
                String t = tokens.get(idx);
                int count = 1;
                while (idx + count < tokens.size() && tokens.get(idx + count).equals(t)) {
                    count++;
                }
                if (sb.length() > 0) {
                    sb.append(' ');
                }
                sb.append(t);
                if (count > 1) {
                    sb.append('(').append(count).append(')');
                }
                idx += count;
            }
            return sb.toString();
        }
    }

    private static void applyRaw(int[][] anBoard, int iSrc, int iDest) {
        anBoard[1][iSrc]--;
        if (iDest < 0) {
            return;
        }
        if (anBoard[0][23 - iDest] != 0) {
            anBoard[1][iDest] = 1;
            anBoard[0][23 - iDest] = 0;
            anBoard[0][24]++;
        } else {
            anBoard[1][iDest]++;
        }
    }

    private static boolean applySubMove(int[][] anBoard, int iSrc, int nRoll) {
        int iDest = iSrc - nRoll;
        if (iSrc < 0 || iSrc > 24 || iDest >= iSrc || anBoard[1][iSrc] < 1) {
            return false;
        }
        anBoard[1][iSrc]--;
        if (iDest < 0) {
            return true;
        }
        if (anBoard[0][23 - iDest] != 0) {
            if (anBoard[0][23 - iDest] > 1) {
                return false;
            }
            anBoard[1][iDest] = 1;
            anBoard[0][23 - iDest] = 0;
            anBoard[0][24]++;
        } else {
            anBoard[1][iDest]++;
        }
        return true;
    }

    private static boolean legalMove(int[][] anBoard, int iSrc, int nPips) {
        int iDest = iSrc - nPips;
        if (iDest >= 0) {
            return anBoard[0][23 - iDest] < 2;
        }
        int nBack;
        for (nBack = 24; nBack > 0; nBack--) {
            if (anBoard[1][nBack] > 0) {
                break;
            }
        }
        return nBack <= 5 && (iSrc == nBack || iDest == -1);
    }

    private static final class MoveList {
        final List<Move> moves = new ArrayList<>();
        int cMaxMoves = 0;
        int cMaxPips = 0;
    }

    private List<Move> generateMoves(int[][] anBoard, int n0, int n1) {
        int[] anRoll = new int[4];
        anRoll[0] = n0;
        anRoll[1] = n1;
        anRoll[2] = anRoll[3] = (n0 == n1) ? n0 : 0;

        MoveList ml = new MoveList();
        int[] anMoves = new int[8];
        java.util.Arrays.fill(anMoves, -1);
        generateMovesSub(ml, anRoll, 0, 23, 0, anBoard, anMoves);

        if (n0 != n1) {
            int tmp = anRoll[0];
            anRoll[0] = anRoll[1];
            anRoll[1] = tmp;
            java.util.Arrays.fill(anMoves, -1);
            generateMovesSub(ml, anRoll, 0, 23, 0, anBoard, anMoves);
        }
        return ml.moves;
    }

    private boolean generateMovesSub(MoveList pml, int[] anRoll, int nMoveDepth,
                                     int iPip, int cPip, int[][] anBoard, int[] anMoves) {
        if (nMoveDepth > 3 || anRoll[nMoveDepth] == 0) {
            return true;
        }

        if (anBoard[1][24] != 0) {
            if (anBoard[0][anRoll[nMoveDepth] - 1] >= 2) {
                return true;
            }
            anMoves[nMoveDepth * 2] = 24;
            anMoves[nMoveDepth * 2 + 1] = 24 - anRoll[nMoveDepth];

            int[][] anBoardNew = {anBoard[0].clone(), anBoard[1].clone()};
            applySubMove(anBoardNew, 24, anRoll[nMoveDepth]);

            if (generateMovesSub(pml, anRoll, nMoveDepth + 1, 23, cPip + anRoll[nMoveDepth], anBoardNew, anMoves)) {
                saveMoves(pml, nMoveDepth + 1, cPip + anRoll[nMoveDepth], anMoves, anBoardNew);
            }
            return false;
        }

        boolean fUsed = false;
        for (int i = iPip; i >= 0; i--) {
            if (anBoard[1][i] != 0 && legalMove(anBoard, i, anRoll[nMoveDepth])) {
                anMoves[nMoveDepth * 2] = i;
                anMoves[nMoveDepth * 2 + 1] = i - anRoll[nMoveDepth];

                int[][] anBoardNew = {anBoard[0].clone(), anBoard[1].clone()};
                applySubMove(anBoardNew, i, anRoll[nMoveDepth]);

                if (generateMovesSub(pml, anRoll, nMoveDepth + 1,
                        anRoll[0] == anRoll[1] ? i : 23,
                        cPip + anRoll[nMoveDepth], anBoardNew, anMoves)) {
                    saveMoves(pml, nMoveDepth + 1, cPip + anRoll[nMoveDepth], anMoves, anBoardNew);
                }
                fUsed = true;
            }
        }
        return !fUsed;
    }

    private void saveMoves(MoveList pml, int cMoves, int cPip, int[] anMoves, int[][] anBoard) {
        if (cMoves < pml.cMaxMoves || cPip < pml.cMaxPips) {
            return;
        }
        if (cMoves > pml.cMaxMoves || cPip > pml.cMaxPips) {
            pml.moves.clear();
        }
        pml.cMaxMoves = cMoves;
        pml.cMaxPips = cPip;

        String key = positionKey(anBoard);
        for (Move m : pml.moves) {
            if (key.equals(positionKey(m.result))) {
                return;
            }
        }

        int[] stored = new int[8];
        java.util.Arrays.fill(stored, -1);
        for (int i = 0; i < cMoves * 2; i++) {
            stored[i] = anMoves[i] > -1 ? anMoves[i] : -1;
        }
        int[][] result = {anBoard[0].clone(), anBoard[1].clone()};
        pml.moves.add(new Move(stored, cMoves, cPip, result, null));
    }

    private static String positionKey(int[][] board) {
        StringBuilder sb = new StringBuilder(50);
        for (int p = 0; p < 2; p++) {
            for (int i = 0; i < 25; i++) {
                sb.append((char) ('0' + board[p][i]));
            }
        }
        return sb.toString();
    }

    /* ------------------------------------------------------------------ */
    /* Static tables (ported verbatim from eval.c / lib/inputs.c)          */
    /* ------------------------------------------------------------------ */

    private static final int[][] AAN_COMBINATION = {
            {0, -1, -1, -1, -1},
            {1, 2, -1, -1, -1},
            {3, 4, 5, -1, -1},
            {6, 7, 8, 9, -1},
            {10, 11, 12, -1, -1},
            {13, 14, 15, 16, 17},
            {18, 19, 20, -1, -1},
            {21, 22, 23, 24, -1},
            {25, 26, 27, -1, -1},
            {28, 29, -1, -1, -1},
            {30, -1, -1, -1, -1},
            {31, 32, 33, -1, -1},
            {-1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1},
            {34, -1, -1, -1, -1},
            {35, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1},
            {36, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1},
            {37, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1},
            {-1, -1, -1, -1, -1},
            {38, -1, -1, -1, -1}
    };

    /** Each row: { fAll, inter0, inter1, inter2, nFaces, nPips }. */
    private static final int[][] A_INTERMEDIATE = {
            {1, 0, 0, 0, 1, 1},
            {1, 0, 0, 0, 1, 2},
            {1, 1, 0, 0, 2, 2},
            {1, 0, 0, 0, 1, 3},
            {0, 1, 2, 0, 2, 3},
            {1, 1, 2, 0, 3, 3},
            {1, 0, 0, 0, 1, 4},
            {0, 1, 3, 0, 2, 4},
            {1, 2, 0, 0, 2, 4},
            {1, 1, 2, 3, 4, 4},
            {1, 0, 0, 0, 1, 5},
            {0, 1, 4, 0, 2, 5},
            {0, 2, 3, 0, 2, 5},
            {1, 0, 0, 0, 1, 6},
            {0, 1, 5, 0, 2, 6},
            {0, 2, 4, 0, 2, 6},
            {1, 3, 0, 0, 2, 6},
            {1, 2, 4, 0, 3, 6},
            {0, 1, 6, 0, 2, 7},
            {0, 2, 5, 0, 2, 7},
            {0, 3, 4, 0, 2, 7},
            {0, 2, 6, 0, 2, 8},
            {0, 3, 5, 0, 2, 8},
            {1, 4, 0, 0, 2, 8},
            {1, 2, 4, 6, 4, 8},
            {0, 3, 6, 0, 2, 9},
            {0, 4, 5, 0, 2, 9},
            {1, 3, 6, 0, 3, 9},
            {0, 4, 6, 0, 2, 10},
            {1, 5, 0, 0, 2, 10},
            {0, 5, 6, 0, 2, 11},
            {1, 6, 0, 0, 2, 12},
            {1, 4, 8, 0, 3, 12},
            {1, 3, 6, 9, 4, 12},
            {1, 5, 10, 0, 3, 15},
            {1, 4, 8, 12, 4, 16},
            {1, 6, 12, 0, 3, 18},
            {1, 5, 10, 15, 4, 20},
            {1, 6, 12, 18, 4, 24}
    };

    private static final int[][] AA_ROLL = {
            {0, 2, 5, 9},
            {1, 8, 17, 24},
            {3, 16, 27, 33},
            {6, 23, 32, 35},
            {10, 29, 34, 37},
            {13, 31, 36, 38},
            {0, 1, 4, -1},
            {0, 3, 7, -1},
            {1, 3, 12, -1},
            {0, 6, 11, -1},
            {1, 6, 15, -1},
            {3, 6, 20, -1},
            {0, 10, 14, -1},
            {1, 10, 19, -1},
            {3, 10, 22, -1},
            {6, 10, 26, -1},
            {0, 13, 18, -1},
            {1, 13, 21, -1},
            {3, 13, 25, -1},
            {6, 13, 28, -1},
            {10, 13, 30, -1}
    };

    private static final int[] AN_POINT = {0, 0, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1, 1};

    private static final int[] AC_BACKBONE = {
            11, 11, 11, 11, 11, 11, 11, 6, 5, 4, 3, 2, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0
    };

    private static final float[][] INPVEC = {
            {0, 0, 0, 0}, {1, 0, 0, 0}, {0, 1, 0, 0}, {0, 0, 1, 0},
            {0, 0, 1, 0.5f}, {0, 0, 1, 1}, {0, 0, 1, 1.5f}, {0, 0, 1, 2},
            {0, 0, 1, 2.5f}, {0, 0, 1, 3}, {0, 0, 1, 3.5f}, {0, 0, 1, 4},
            {0, 0, 1, 4.5f}, {0, 0, 1, 5}, {0, 0, 1, 5.5f}, {0, 0, 1, 6}
    };

    private static final float[][] INPVECB = {
            {0, 0, 0, 0}, {1, 0, 0, 0}, {1, 1, 0, 0}, {1, 1, 1, 0},
            {1, 1, 1, 0.5f}, {1, 1, 1, 1}, {1, 1, 1, 1.5f}, {1, 1, 1, 2},
            {1, 1, 1, 2.5f}, {1, 1, 1, 3}, {1, 1, 1, 3.5f}, {1, 1, 1, 4},
            {1, 1, 1, 4.5f}, {1, 1, 1, 5}, {1, 1, 1, 5.5f}, {1, 1, 1, 6}
    };

    private static final int[] AN_ESCAPES = new int[0x1000];
    private static final int[] AN_ESCAPES1 = new int[0x1000];

    static {
        for (int i = 0; i < 0x1000; i++) {
            int c = 0;
            for (int n0 = 0; n0 <= 5; n0++) {
                for (int n1 = 0; n1 <= n0; n1++) {
                    if ((i & (1 << (n0 + n1 + 1))) == 0 && !(((i & (1 << n0)) != 0) && ((i & (1 << n1)) != 0))) {
                        c += (n0 == n1) ? 1 : 2;
                    }
                }
            }
            AN_ESCAPES[i] = c;
        }

        AN_ESCAPES1[0] = 0;
        for (int i = 1; i < 0x1000; i++) {
            int c = 0;
            int low = 0;
            while ((i & (1 << low)) == 0) {
                ++low;
            }
            for (int n0 = 0; n0 <= 5; n0++) {
                for (int n1 = 0; n1 <= n0; n1++) {
                    if ((n0 + n1 + 1 > low) && (i & (1 << (n0 + n1 + 1))) == 0
                            && !(((i & (1 << n0)) != 0) && ((i & (1 << n1)) != 0))) {
                        c += (n0 == n1) ? 1 : 2;
                    }
                }
            }
            AN_ESCAPES1[i] = c;
        }
    }
}
