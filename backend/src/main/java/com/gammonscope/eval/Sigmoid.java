package com.gammonscope.eval;

/**
 * Table-based sigmoid approximation ported from gnubg's {@code lib/sigmoid.h}.
 * Computes an approximation of 1 / (1 + e^x) using a lookup table of
 * {@code e[k] = exp(k/10)/10}, matching gnubg's neural-net arithmetic.
 */
final class Sigmoid {

    private static final float[] E = new float[100];

    static {
        for (int k = 0; k < 100; k++) {
            E[k] = (float) (Math.exp(k / 10.0) / 10.0);
        }
    }

    private Sigmoid() {
    }

    static float sigmoid(float xin) {
        if (xin >= 0.0f) {
            if (xin < 10.0f) {
                final float x1 = 10.0f * xin;
                final int i = (int) x1;
                return 1.0f / (1.0f + E[i] * ((float) (10 - i) + x1));
            }
            return 1.0f / 19931.370438230298f;
        } else {
            if (xin > -10.0f) {
                final float x1 = -10.0f * xin;
                final int i = (int) x1;
                return 1.0f - 1.0f / (1.0f + E[i] * ((float) (10 - i) + x1));
            }
            return 19930.370438230298f / 19931.370438230298f;
        }
    }
}
