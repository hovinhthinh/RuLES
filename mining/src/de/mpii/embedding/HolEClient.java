package de.mpii.embedding;

import de.mpii.util.Pair;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Arrays;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class HolEClient extends EmbeddingClient {
    public static final Logger LOGGER = Logger.getLogger(HolEClient.class.getName());
    // Automatically turn on CACHED_CORREL if nEntities <= this threshold.
    private static final int CACHED_CORREL_THRESHOLD = 15000;

    private boolean CACHED_CORREL = false;

    private DoubleVector[] entitiesEmbedding, relationsEmbedding;
    double correl[][][];

    private double[][] fftEntitiesEmbeddingReal, fftEntitiesEmbeddingImag;

    public HolEClient(String workspace) {
        LOGGER.info("Loading embedding HolE client from '" + workspace + ".");

        try {
            // Read nEntities, nRelations, eLength.
            Scanner metaIn = new Scanner(new File(workspace + "/meta.txt"));
            nEntities = metaIn.nextInt();
            if (nEntities <= CACHED_CORREL_THRESHOLD) {
                CACHED_CORREL = true;
            }
            nRelations = metaIn.nextInt();
            int nClasses = metaIn.nextInt();
            metaIn.close();
            trueFacts = new FactEncodedSetPerPredicate[nRelations];
            cachedRankQueries = new ConcurrentHashMap[nRelations];
            for (int i = 0; i < nRelations; ++i) {
                trueFacts[i] = new FactEncodedSetPerPredicate();
                cachedRankQueries[i] = new ConcurrentHashMap<>();
            }
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
            // Read true facts;
            Scanner fIn = new Scanner(new File(workspace + "/train.txt"));
            while (fIn.hasNext()) {
                int s = fIn.nextInt(), p = fIn.nextInt(), o = fIn.nextInt();
                trueFacts[p].addFact(s, o);
            }
            fIn.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        if (CACHED_CORREL) {
            correl = new double[nEntities][nEntities][];
        }

        fftEntitiesEmbeddingReal = new double[nEntities][];
        fftEntitiesEmbeddingImag = new double[nEntities][eLength];
        for (int i = 0; i < nEntities; ++i) {
            fftEntitiesEmbeddingReal[i] = Arrays.copyOf(entitiesEmbedding[i].value, eLength);
            FFT.transform(fftEntitiesEmbeddingReal[i], fftEntitiesEmbeddingImag[i]);
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

    public static double[] getCircularCorrelation_log(double[] s, double[] o) {
        int fftSize = s.length;
        double[] ns = Arrays.copyOf(s, fftSize), no = Arrays.copyOf(o, fftSize);
        Pair<double[], double[]> sfft = new Pair<>(ns, new double[fftSize]);
        Pair<double[], double[]> offt = new Pair<>(no, new double[fftSize]);
        FFT.transform(sfft.first, sfft.second);
        FFT.transform(offt.first, offt.second);
        double[] real = new double[fftSize], imag = new double[fftSize];
        for (int i = 0; i < fftSize; ++i) {
            real[i] = sfft.first[i] * offt.first[i] + sfft.second[i] * offt.second[i];
            imag[i] = sfft.first[i] * offt.second[i] - sfft.second[i] * offt.first[i];
        }
        FFT.inverseTransform(real, imag);
        for (int i = 0; i < fftSize; ++i) {
            real[i] /= fftSize;
        }
        return real;
    }

    public double[] getCircularCorrelation_log_optimized(int subject, int object) {
        double[] real = new double[eLength], imag = new double[eLength];
        for (int i = 0; i < eLength; ++i) {
            real[i] = fftEntitiesEmbeddingReal[subject][i] * fftEntitiesEmbeddingReal[object][i] +
                    fftEntitiesEmbeddingImag[subject][i] * fftEntitiesEmbeddingImag[object][i];
            imag[i] = fftEntitiesEmbeddingReal[subject][i] * fftEntitiesEmbeddingImag[object][i] -
                    fftEntitiesEmbeddingImag[subject][i] * fftEntitiesEmbeddingReal[object][i];
        }
        FFT.inverseTransform(real, imag);
        for (int i = 0; i < eLength; ++i) {
            real[i] /= eLength;
        }
        return real;
    }

    @Override
    public double getScore(int subject, int predicate, int object) {
        if (CACHED_CORREL) {
            if (correl[subject][object] == null) {
                correl[subject][object] = getCircularCorrelation_log_optimized(subject, object);
            }
            double result = 0;
            for (int i = 0; i < eLength; ++i) {
                result += correl[subject][object][i] * relationsEmbedding[predicate].value[i];
            }
            return 1.0 / (1 + Math.exp(-result));

        } else {
            double[] r = getCircularCorrelation_log_optimized(subject, object);
            double result = 0;
            for (int k = 0; k < eLength; ++k) {
                result += r[k] * relationsEmbedding[predicate].value[k];
            }
            return 1.0 / (1 + Math.exp(-result));
        }
    }

    public static void main(String[] args) {
//        new HolEClient("../data/fb15k/");
//        double[] a = new double[]{7, 7, 7, 7, 7, 2, 3, -1, -1, -1, 1.5};
//        double[] b = new double[]{1, 1, 1, 1, 1, 9, 8, 0, -1, -1, 1.5};
//
//        int x = a.length;
//
//        double[] c1 = getCircularCorrelation_log(a, b);
//        double[] c2 = getCircularCorrelation(a, b);
//        for (int i = 0; i < x; ++i) {
//            System.out.printf("%.3f\t%.3f\n", c1[i], c2[i]);
//        }
    }
}
