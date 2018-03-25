package de.mpii.embedding;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SSPClient extends EmbeddingClient {
    public static final Logger LOGGER = Logger.getLogger(SSPClient.class.getName());

    private DoubleVector[] entitiesEmbedding, relationsEmbedding, semantic;

    double balance;

    double readDouble(DataInputStream in) throws IOException {
        byte[] b = new byte[8];
        in.read(b, 0, 8);
        for (int i = 0; i < 4; ++i) {
            byte x = b[i];
            b[i] = b[7 - i];
            b[7 - i] = x;
        }
        return ByteBuffer.wrap(b).getDouble();
    }

    public SSPClient(String workspace) {
        super(workspace);
        LOGGER.info("Loading embedding SSP client from '" + workspace + ".");

        try {
            // Read embeddings.
            DataInputStream eIn = new DataInputStream(new FileInputStream(
                    new File(workspace + "/ssp")));
            eLength = (int) (readDouble(eIn) + 1e-6);
            balance = readDouble(eIn);
            entitiesEmbedding = new DoubleVector[nEntities];
            for (int i = 0; i < nEntities; ++i) {
                entitiesEmbedding[i] = new DoubleVector(eLength);
                for (int j = 0; j < eLength; ++j) {
                    entitiesEmbedding[i].value[j] = readDouble(eIn);
                }
            }
            relationsEmbedding = new DoubleVector[nRelations];
            for (int i = 0; i < nRelations; ++i) {
                relationsEmbedding[i] = new DoubleVector(eLength);
                for (int j = 0; j < eLength; ++j) {
                    relationsEmbedding[i].value[j] = readDouble(eIn);
                }
            }
            semantic = new DoubleVector[nEntities];
            for (int i = 0; i < nEntities; ++i) {
                semantic[i] = new DoubleVector(eLength);
                for (int j = 0; j < eLength; ++j) {
                    semantic[i].value[j] = readDouble(eIn);
                }
            }
            eIn.close();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public double getScore(int subject, int predicate, int object) {
        double[] sem = new double[eLength];
        double[] err = new double[eLength];
        double sum = 0;
        for (int i = 0; i < eLength; ++i) {
            sem[i] = semantic[subject].value[i] + semantic[object].value[i];
            sum += Math.abs(sem[i]);
            err[i] = entitiesEmbedding[subject].value[i] + relationsEmbedding[predicate].value[i] -
                    entitiesEmbedding[object].value[i];
        }
        sum = Math.max(sum, 1e-5);
        double ste = 0;
        for (int i = 0; i < eLength; ++i) {
            sem[i] /= sum;
            ste += sem[i] * err[i];
        }
        double first = 0, second = 0;
        for (int i = 0; i < eLength; ++i) {
            first += Math.abs(err[i] - ste * sem[i]);
            second += Math.abs(err[i]);
        }

        return -balance * first - second;
    }

    public static void main(String[] args) {
        new SSPClient("../data/fb15k/");
    }
}
