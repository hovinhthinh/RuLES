package de.mpii.mining;

/**
 * Created by hovinhthinh on 11/14/17.
 */
public class MinerConfig {
    private static final int INF = Integer.MAX_VALUE;

    // Embedding.
    public String embeddingModel = "transe";

    // Language bias.
    public int maxNumVariables = 3;
    public int maxVariableDegree = 3; // for rules with disjunction, second head atom is ignored for computing variable
    // degree.

    public int maxNumAtoms = 3;
    public int maxNumUnaryPositiveAtoms = 0;
    public int maxNumBinaryPositiveAtoms = INF;
    public int maxNumExceptionAtoms = 0;
    public int maxNumUnaryExceptionAtoms = 1;
    public int maxNumBinaryExceptionAtoms = 1;
    public int maxNumInstantiatedExceptionAtoms = 0;
    public int maxUniquePredicateOccurrence = 2;
    public double minConf = 0.1;
    public int minSupport = 10;

    // Content prune options.
    public double minHeadCoverage = 0.01;
    public double minExceptionConfidence = 0.05;

    // Mining options.
    public int enqueueLimit = 100000000;
    public int numWorkers = 8;
    public boolean xyz = false;

    // Scoring options.
    public double embeddingWeight = 0.3;
    public boolean usePCAConf = false;


    public void printConfig() {
        System.out.println("---------------MinerConfig---------------");
        System.out.println("embeddingModel=" + embeddingModel);
        System.out.println("minConfidence=" + minConf);
        System.out.println("minSupport=" + minSupport);
        System.out.println("maxNumVariables=" + maxNumVariables);
        System.out.println("maxVariableDegree=" + maxVariableDegree);
        System.out.println("maxNumAtoms=" + maxNumAtoms);
        System.out.println("maxNumUnaryPositiveAtoms=" + maxNumUnaryPositiveAtoms);
        System.out.println("maxNumBinaryPositiveAtoms=" + maxNumBinaryPositiveAtoms);
        System.out.println("maxNumExceptionAtoms=" + maxNumExceptionAtoms);
        System.out.println("maxNumUnaryExceptionAtoms=" + maxNumUnaryExceptionAtoms);
        System.out.println("maxNumBinaryExceptionAtoms=" + maxNumBinaryExceptionAtoms);
        System.out.println("maxNumInstantiatedExceptionAtoms=" + maxNumInstantiatedExceptionAtoms);
        System.out.println("maxUniquePredicateOccurrence=" + maxUniquePredicateOccurrence);
        System.out.println("minHeadCoverage=" + minHeadCoverage);
        System.out.println("minExceptionConfidence=" + minExceptionConfidence);
        System.out.println("embeddingWeight=" + embeddingWeight);
        System.out.println("usePCAConf=" + usePCAConf);
        System.out.println("numWorkers=" + numWorkers);
        System.out.println("xyz=" + xyz);
        System.out.println("-----------------------------------------");
        System.out.println("-----------------------------------------");
    }
}
