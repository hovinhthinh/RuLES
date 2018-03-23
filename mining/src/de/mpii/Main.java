package de.mpii;

import de.mpii.mining.ConstantMiner;
import de.mpii.mining.Miner;
import de.mpii.mining.MinerConfig;
import org.apache.commons.cli.*;

import java.io.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.logging.Logger;

/**
 * Created by hovinhthinh on 11/13/17.
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    private static Options getOptions() {
        Options options = new Options();

        // workspace
        Option option = new Option("w", "workspace", true, "Path to workspace");
        option.setRequired(true);
        options.addOption(option);

        // outputFilePath
        option = new Option("o", "output", true, "Output file path");
        option.setRequired(false);
        options.addOption(option);

        // disjunction
        option = new Option("dj", "disjunction", false, "Mine rule with disjunction in the head");
        option.setRequired(false);
        options.addOption(option);

        // embeddingModel
        option = new Option("em", "embedding_model", true, "Embedding model ('transe'/'hole'/'ssp')");
        option.setRequired(true);
        options.addOption(option);

        // minConf
        option = new Option("mc", "min_conf", true, "Min confidence of rule (not counting mrr) (default: 0.1)");
        option.setRequired(false);
        options.addOption(option);

        // minSupport
        option = new Option("ms", "min_support", true, "Min support of rule (default: 2)");
        option.setRequired(false);
        options.addOption(option);

        // maxNumVariables
        option = new Option("nv", "max_num_var", true, "Maximum number of variables (default: 3)");
        option.setRequired(false);
        options.addOption(option);

        // maxVariableDegree
        option = new Option("vd", "max_var_deg", true, "Maximum variable degree (number of predicates having the " +
                "same variable) (default: 3)");
        option.setRequired(false);
        options.addOption(option);

        // maxNumAtoms
        option = new Option("na", "max_num_atom", true, "Maximum number of atoms (default: 4)");
        option.setRequired(false);
        options.addOption(option);

        // maxNumUnaryPositiveAtoms
        option = new Option("nupa", "max_num_unary_pos_atom", true, "Maximum number of unary positive atoms (default:" +
                " 0)");
        option.setRequired(false);
        options.addOption(option);

        // maxNumBinaryPositiveAtoms
        option = new Option("nbpa", "max_num_binary_pos_atom", true, "Maximum number of binary positive atoms " +
                "(default: INF)");
        option.setRequired(false);
        options.addOption(option);

        // maxNumExceptionAtoms
        option = new Option("nna", "max_num_neg_atom", true, "Maximum number of exception atoms (default: 1)");
        option.setRequired(false);
        options.addOption(option);

        // maxNumUnaryExceptionAtoms
        option = new Option("nuna", "max_num_unary_neg_atom", true, "Maximum number of unary exception atoms " +
                "(default: 1)");
        option.setRequired(false);
        options.addOption(option);

        // maxNumBinaryExceptionAtoms
        option = new Option("nbna", "max_num_binary_neg_atom", true, "Maximum number of binary exception atoms " +
                "(default: 1)");
        option.setRequired(false);
        options.addOption(option);

        // maxNumInstantiatedExceptionAtoms
        option = new Option("nina", "max_num_inst_neg_atom", true, "Maximum number of instantiated exception atoms " +
                "(default: 0)");
        option.setRequired(false);
        options.addOption(option);

        // maxUniquePredicateOccurrence
        option = new Option("nupo", "max_num_uniq_pred_occur", true, "Maximum number of occurrence of each unique " +
                "predicate (default: 2)");
        option.setRequired(false);
        options.addOption(option);

        // minHeadCoverage
        option = new Option("hc", "min_hc", true, "Minimum head coverage of mined rules (default: 0.01)");
        option.setRequired(false);
        options.addOption(option);

        // minExceptionCoverage
        option = new Option("ec", "min_ec", true, "Minimum exception coverage of adding exception atom (default: 0.1)");
        option.setRequired(false);
        options.addOption(option);

        // embeddingWeight
        option = new Option("ew", "embedding_weight", true, "Weight of embedding in score function (default: 0.3)");
        option.setRequired(false);
        options.addOption(option);

        // usePCAConf
        option = new Option("pca", "use_pca_conf", false, "Use pca confidence instead of standard confidence");
        option.setRequired(false);
        options.addOption(option);

        // xyz
        option = new Option("xyz", "mine_xyz", false, "Fix the form of positive parts to XYZ");
        option.setRequired(false);
        options.addOption(option);

        // numWorkers
        option = new Option("nw", "num_workers", true, "Number of parallel workers (default: 8)");
        option.setRequired(false);
        options.addOption(option);

        return options;
    }

    public static void main(String[] args) throws Exception {
//        args = "-w ../data/imdb -em transe -o tmp -na 3 -ew 0 -ms 2 -nna 1 -nina 0".split("\\s++");

        long currentTime = System.currentTimeMillis();
        MinerConfig config = new MinerConfig();

        // Get config.
        Options options = getOptions();
        CommandLine cmd = null;
        try {
            cmd = new DefaultParser().parse(options, args);
        } catch (ParseException e) {
            System.out.println(e.getMessage());
            new HelpFormatter().printHelp("utility-name", options);
            return;
        }
        String ov = cmd.getOptionValue("nv");
        if (ov != null) {
            config.maxNumVariables = Integer.parseInt(ov);
        }
        ov = cmd.getOptionValue("vd");
        if (ov != null) {
            config.maxVariableDegree = Integer.parseInt(ov);
        }
        ov = cmd.getOptionValue("em");
        if (ov != null) {
            config.embeddingModel = ov;
        }
        ov = cmd.getOptionValue("na");
        if (ov != null) {
            config.maxNumAtoms = Integer.parseInt(ov);
        }
        ov = cmd.getOptionValue("nupa");
        if (ov != null) {
            config.maxNumUnaryPositiveAtoms = Integer.parseInt(ov);
        }
        ov = cmd.getOptionValue("nbpa");
        if (ov != null) {
            config.maxNumBinaryPositiveAtoms = Integer.parseInt(ov);
        }
        ov = cmd.getOptionValue("nna");
        if (ov != null) {
            config.maxNumExceptionAtoms = Integer.parseInt(ov);
        }
        ov = cmd.getOptionValue("nuna");
        if (ov != null) {
            config.maxNumUnaryExceptionAtoms = Integer.parseInt(ov);
        }
        ov = cmd.getOptionValue("nbna");
        if (ov != null) {
            config.maxNumBinaryExceptionAtoms = Integer.parseInt(ov);
        }
        ov = cmd.getOptionValue("nina");
        if (ov != null) {
            config.maxNumInstantiatedExceptionAtoms = Integer.parseInt(ov);
        }
        ov = cmd.getOptionValue("nupo");
        if (ov != null) {
            config.maxUniquePredicateOccurrence = Integer.parseInt(ov);
        }
        ov = cmd.getOptionValue("hc");
        if (ov != null) {
            config.minHeadCoverage = Double.parseDouble(ov);
        }
        ov = cmd.getOptionValue("ec");
        if (ov != null) {
            config.minExceptionCoverage = Double.parseDouble(ov);
        }
        ov = cmd.getOptionValue("ew");
        if (ov != null) {
            config.embeddingWeight = Double.parseDouble(ov);
        }
        ov = cmd.getOptionValue("nw");
        if (ov != null) {
            config.numWorkers = Integer.parseInt(ov);
        }
        if (cmd.hasOption("pca")) {
            config.usePCAConf = true;
        }
        if (cmd.hasOption("dj")) {
            config.disjunction = true;
        }
        if (cmd.hasOption("xyz")) {
            config.xyz = true;
        }
        ov = cmd.getOptionValue("mc");
        if (ov != null) {
            config.minConf = Double.parseDouble(ov);
        }
        ov = cmd.getOptionValue("ms");
        if (ov != null) {
            config.minSupport = Integer.parseInt(ov);
        }
        String output = cmd.getOptionValue("w") + "/rules.txt";
        if (cmd.hasOption("o")) {
            output = cmd.getOptionValue("o");
        }
        // Process.
        config.printConfig();
        if (config.xyz && config.maxNumBinaryPositiveAtoms != 3) {
            config.maxNumBinaryPositiveAtoms = 3;
            LOGGER.info("XYZ enabled: maxNumBinaryPositiveAtoms is set to 3");
        }
        if (config.disjunction) {
            if (config.maxNumAtoms > 4) {
                throw new RuntimeException("Not support num atoms > 4 for disjunction");
            }
            if (config.usePCAConf) {
                throw new RuntimeException("Not support PCA confidence for disjunction");
            }
            ConstantMiner miner = new ConstantMiner(cmd.getOptionValue("w"), config, new PrintWriter(new File(output)));
            miner.mine();
        } else {
            Miner miner = new Miner(cmd.getOptionValue("w"), config, new PrintWriter(new File(output)));
            miner.mine();
        }

        ArrayList<String[]> rules = new ArrayList<>();
        BufferedReader in = new BufferedReader(new InputStreamReader(new FileInputStream(new File(output))));
        String line;
        while ((line = in.readLine()) != null) {
            if (line.isEmpty()) {
                break;
            }
            rules.add(line.split("\t"));
        }
        in.close();
        Collections.sort(rules, new Comparator<String[]>() {
            @Override
            public int compare(String[] o1, String[] o2) {
                return o2[8].compareTo(o1[8]);
            }
        });
        PrintWriter out = new PrintWriter(new File(output + ".sorted"));
        for (String[] rule : rules) {
            for (int i = 0; i < rule.length; ++i) {
                out.print(rule[i]);
                if (i < rule.length - 1) {
                    out.print("\t");
                } else {
                    out.print("\n");
                }
            }
        }
        out.close();

        LOGGER.info("Total runtime: " + (System.currentTimeMillis() - currentTime + "ms"));
    }
}
