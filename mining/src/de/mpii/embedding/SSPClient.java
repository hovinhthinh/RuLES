package de.mpii.embedding;

import java.io.DataInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.util.Scanner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

public class SSPClient extends EmbeddingClient {
    public static final Logger LOGGER = Logger.getLogger(SSPClient.class.getName());

    private DoubleVector[] entitiesEmbedding, relationsEmbedding, semantic;

    double balance;

    public SSPClient(String workspace) {
        LOGGER.info("Loading embedding SSP client from '" + workspace + ".");

        try {
            // Read nEntities, nRelations, eLength.
            Scanner metaIn = new Scanner(new File(workspace + "/meta.txt"));
            nEntities = metaIn.nextInt();
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
                    new File(workspace + "/ssp")));
            eLength = (int) (eIn.readDouble() + 1e-6);
            balance = eIn.readDouble();
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
            semantic = new DoubleVector[nEntities];
            for (int i = 0; i < nEntities; ++i) {
                semantic[i] = new DoubleVector(eLength);
                for (int j = 0; j < eLength; ++j) {
                    semantic[i].value[j] = eIn.readDouble();
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
