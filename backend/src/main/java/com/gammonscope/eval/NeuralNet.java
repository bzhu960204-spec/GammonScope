package com.gammonscope.eval;

/**
 * A single feed-forward neural network, ported faithfully from gnubg's
 * {@code lib/neuralnet.c}. One hidden layer, sigmoid activations. Weight
 * layouts and the sigmoid approximation match gnubg so that outputs are
 * numerically (near) identical.
 */
public final class NeuralNet {

    final int cInput;
    final int cHidden;
    final int cOutput;
    final float betaHidden;
    final float betaOutput;

    /** [input][hidden] laid out input-major, length cInput*cHidden. */
    final float[] hiddenWeight;
    /** [output][hidden] laid out output-major, length cHidden*cOutput. */
    final float[] outputWeight;
    final float[] hiddenThreshold;
    final float[] outputThreshold;

    NeuralNet(int cInput, int cHidden, int cOutput, float betaHidden, float betaOutput,
              float[] hiddenWeight, float[] outputWeight, float[] hiddenThreshold, float[] outputThreshold) {
        this.cInput = cInput;
        this.cHidden = cHidden;
        this.cOutput = cOutput;
        this.betaHidden = betaHidden;
        this.betaOutput = betaOutput;
        this.hiddenWeight = hiddenWeight;
        this.outputWeight = outputWeight;
        this.hiddenThreshold = hiddenThreshold;
        this.outputThreshold = outputThreshold;
    }

    /** Evaluate the network for {@code input}, writing cOutput values into {@code output}. */
    void evaluate(float[] input, float[] output) {
        final int cH = cHidden;
        final float[] ar = new float[cH];

        for (int i = 0; i < cH; i++) {
            ar[i] = hiddenThreshold[i];
        }

        int w = 0;
        for (int i = 0; i < cInput; i++) {
            final float ari = input[i];
            if (ari == 0.0f) {
                w += cH;
            } else if (ari == 1.0f) {
                for (int j = 0; j < cH; j++) {
                    ar[j] += hiddenWeight[w++];
                }
            } else {
                for (int j = 0; j < cH; j++) {
                    ar[j] += hiddenWeight[w++] * ari;
                }
            }
        }

        for (int i = 0; i < cH; i++) {
            ar[i] = Sigmoid.sigmoid(-betaHidden * ar[i]);
        }

        int ow = 0;
        for (int i = 0; i < cOutput; i++) {
            float r = outputThreshold[i];
            for (int j = 0; j < cH; j++) {
                r += ar[j] * outputWeight[ow++];
            }
            output[i] = Sigmoid.sigmoid(-betaOutput * r);
        }
    }
}
