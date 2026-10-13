package com.gammonscope.eval;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;

/**
 * Loads the three main gnubg neural networks (contact, race, crashed) from the
 * text {@code gnubg.weights} file. The file layout, taken from gnubg's
 * {@code EvalInitialise}/{@code NeuralNetLoad}, is:
 * <pre>
 *   GNU Backgammon &lt;version&gt;
 *   &lt;net&gt; x6  (contact, race, crashed, then three pruning nets we ignore)
 * </pre>
 * Each net begins with a header line
 * {@code cInput cHidden cOutput dummy betaHidden betaOutput} followed by the
 * hidden weights, output weights, hidden thresholds and output thresholds,
 * one float per line.
 */
public final class Weights {

    public final NeuralNet contact;
    public final NeuralNet race;
    public final NeuralNet crashed;

    private Weights(NeuralNet contact, NeuralNet race, NeuralNet crashed) {
        this.contact = contact;
        this.race = race;
        this.crashed = crashed;
    }

    public static Weights load(InputStream in) throws IOException {
        String text = new String(in.readAllBytes(), StandardCharsets.ISO_8859_1);
        int nl = text.indexOf('\n');
        if (nl < 0 || !text.startsWith("GNU Backgammon")) {
            throw new IOException("Not a gnubg weights file");
        }
        String[] tokens = text.substring(nl + 1).trim().split("\\s+");
        Cursor c = new Cursor(tokens);

        NeuralNet contact = readNet(c);
        NeuralNet race = readNet(c);
        NeuralNet crashed = readNet(c);
        return new Weights(contact, race, crashed);
    }

    private static NeuralNet readNet(Cursor c) {
        int cInput = Integer.parseInt(c.next());
        int cHidden = Integer.parseInt(c.next());
        int cOutput = Integer.parseInt(c.next());
        c.next(); // dummy / nTrained
        float betaHidden = Float.parseFloat(c.next());
        float betaOutput = Float.parseFloat(c.next());

        float[] hiddenWeight = readFloats(c, cInput * cHidden);
        float[] outputWeight = readFloats(c, cHidden * cOutput);
        float[] hiddenThreshold = readFloats(c, cHidden);
        float[] outputThreshold = readFloats(c, cOutput);

        return new NeuralNet(cInput, cHidden, cOutput, betaHidden, betaOutput,
                hiddenWeight, outputWeight, hiddenThreshold, outputThreshold);
    }

    private static float[] readFloats(Cursor c, int n) {
        float[] a = new float[n];
        for (int i = 0; i < n; i++) {
            a[i] = Float.parseFloat(c.next());
        }
        return a;
    }

    private static final class Cursor {
        private final String[] tokens;
        private int pos;

        Cursor(String[] tokens) {
            this.tokens = tokens;
        }

        String next() {
            return tokens[pos++];
        }
    }
}
