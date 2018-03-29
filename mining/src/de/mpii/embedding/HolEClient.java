package de.mpii.embedding;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.logging.Logger;

public class HolEClient extends EmbeddingClient {
    public static final Logger LOGGER = Logger.getLogger(HolEClient.class.getName());
    // Automatically turn on CACHED_CORREL if nEntities <= this threshold.
    private static final int CACHED_CORREL_THRESHOLD = 15000;
    double correl[][][];
    private boolean CACHED_CORREL = false;
    private DoubleVector[] entitiesEmbedding, relationsEmbedding;
    private double[][] fftEntitiesEmbeddingReal, fftEntitiesEmbeddingImag;

    private boolean optimized = false;

    public HolEClient(String workspace) {
        super(workspace);
        LOGGER.info("Loading embedding HolE client from '" + workspace + ".");

        try {
            // Read embeddings.
            DataInputStream eIn = new DataInputStream(new FileInputStream(
                    new File(workspace + "/hole")));
            eLength = (int) (eIn.readDouble() + 1e-6);
            entitiesEmbedding = new DoubleVector[nEntities];
            for (int i = 0; i < nEntities; ++i) {
                entitiesEmbedding[i] = new DoubleVector(eLength);
                for (int j = 0; j < eLength; ++j) {
                    entitiesEmbedding[i].value[j] = eIn.readDouble();
                }
            }
            relationsEmbedding = new DoubleVector[nRelations];
            for (int i = 0; i < nRelations; ++i) {
                relationsEmbedding[i] = new DoubleVector(eLength);
                for (int j = 0; j < eLength; ++j) {
                    relationsEmbedding[i].value[j] = eIn.readDouble();
                }
            }
            eIn.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (CACHED_CORREL) {
            correl = new double[nEntities][nEntities][];
            LOGGER.info("Cache Enabled");
        }

        optimized = ((eLength & (eLength - 1)) == 0 && eLength >= 256) ? optimized = true : false;

        if (optimized) {
            fftEntitiesEmbeddingReal = new double[nEntities][];
            fftEntitiesEmbeddingImag = new double[nEntities][eLength];
            for (int i = 0; i < nEntities; ++i) {
                fftEntitiesEmbeddingReal[i] = Arrays.copyOf(entitiesEmbedding[i].value, eLength);
                FFTBase.fft(fftEntitiesEmbeddingReal[i], fftEntitiesEmbeddingImag[i], true);
            }
            LOGGER.info("FFT Optimization Enabled");
        }
    }

    public static double[] getCircularCorrelation(double[] s, double[] o) {
        double[] r = new double[s.length];
        int t = 0;
        for (int k = 0; k < r.length; ++k) {
            r[k] = 0;
            for (int i = 0; i < r.length; ++i) {
                t = i + k;
                if (t >= r.length) {
                    t -= r.length;
                }
                r[k] += s[i] * o[t];
            }
        }
        return r;
    }

    public static double[] getCircularCorrelation_log_test(double[] s, double[] o) {
        int l = s.length;
        double[] sReal = Arrays.copyOf(s, l), oReal = Arrays.copyOf(o, l);
        double[] sImag = new double[l], oImag = new double[l];
        FFTBase.fft(sReal, sImag, true);
        FFTBase.fft(oReal, oImag, true);
        double[] real = new double[l], imag = new double[l];
        for (int i = 0; i < l; ++i) {
            real[i] = sReal[i] * oReal[i] + sImag[i] * oImag[i];
            imag[i] = sReal[i] * oImag[i] - sImag[i] * oReal[i];
        }
        FFTBase.fft(real, imag, false);
        for (int i = 0; i < l; ++i) {
            real[i] /= l;
        }
        return real;
    }

    public static void main(String[] args) {
//        new HolEClient("../data/fb15k/");
        double[] a = new double[]{3, 4, 5, 6, 7, 8, 7, 7};
        double[] b = new double[]{0, 9, 8, 6, 4, 5, 1, 1};

        int x = a.length;

        double[] c1 = getCircularCorrelation_log_test(a, b);
        double[] c2 = getCircularCorrelation(a, b);
        for (int i = 0; i < x; ++i) {
            System.out.printf("%.3f\t%.3f\n", c1[i], c2[i]);
        }
    }

    public double[] getCircularCorrelation_log_optimized(int subject, int object) {
        double[] real = new double[eLength], imag = new double[eLength];
        for (int i = 0; i < eLength; ++i) {
            real[i] = fftEntitiesEmbeddingReal[subject][i] * fftEntitiesEmbeddingReal[object][i] +
                    fftEntitiesEmbeddingImag[subject][i] * fftEntitiesEmbeddingImag[object][i];
            imag[i] = fftEntitiesEmbeddingReal[subject][i] * fftEntitiesEmbeddingImag[object][i] -
                    fftEntitiesEmbeddingImag[subject][i] * fftEntitiesEmbeddingReal[object][i];
        }
        FFTBase.fft(real, imag, false);
        for (int i = 0; i < eLength; ++i) {
            real[i] /= eLength;
        }

        return real;
    }

    @Override
    public double getScore(int subject, int predicate, int object) {
        if (CACHED_CORREL) {
            if (correl[subject][object] == null) {
                correl[subject][object] = optimized ? getCircularCorrelation_log_optimized(subject, object) :
                        getCircularCorrelation(entitiesEmbedding[subject].value, entitiesEmbedding[object].value);
            }
            double result = 0;
            for (int i = 0; i < eLength; ++i) {
                result += correl[subject][object][i] * relationsEmbedding[predicate].value[i];
            }
            return 1.0 / (1 + Math.exp(-result));

        } else {
            double[] r = optimized ? getCircularCorrelation_log_optimized(subject, object) :
                    getCircularCorrelation(entitiesEmbedding[subject].value, entitiesEmbedding[object].value);
            double result = 0;
            for (int k = 0; k < eLength; ++k) {
                result += r[k] * relationsEmbedding[predicate].value[k];
            }
            return 1.0 / (1 + Math.exp(-result));
        }
    }
}
