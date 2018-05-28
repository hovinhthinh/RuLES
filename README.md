# RuLES - Rule Learning with Embedding Support
[![Build Status](https://travis-ci.org/hovinhthinh/kg-comp-embedding-rule.svg?branch=master)](https://travis-ci.org/hovinhthinh/kg-comp-embedding-rule)

RuLES is a system for mining nonmonotonic rules from a knowledge graph (KG) under the Open World Assumption (OWA)
that accounts for the guidance from an pre-trained embedding model.

### Prerequisites
- For mining system (using Java): jdk, ant
- For embedding models:
    - TransE, HolE models (using Python): python, numpy, scipy, scikit-learn
    - SSP model (using C++): icc, boost, armadillo (recommend armadillo4)
### 0. Build the project
```
$ cd mining/ && ant build && cd ../
```
This will generate a jar file for the mining system at `./mining/build.jar`

`Additional`: If we want to run SSP embedding model:
```
$ icc -std=c++11 -O3 -qopenmp -larmadillo -xHost embedding/ssp/ssp_main.cpp -o embedding/ssp_main
```
If there is any error, you might need to change the environment source of `icc` before compiling:
```
$ source /opt/intel/bin/compilervars.sh intel64 # (intel64 or ia32 depending on system architecture)
```
### 1. Workspace
We should prepare a folder containing a single data file  with name `ideal.data.txt` of the knowledge graph, in which each triple is represented in one line using the RDF form:
```
subject[tab]predicate[tab]object
```
For representing unary predicate, the `predicate` of the triple should be `<type>`:
```
entity[tab]<type>[tab]class
```
For representing hierarchical between classes, the `predicate` of the triple should be `<subClassOf>`:
```
classA[tab]<subClassOf>[tab]classB
```
However, we currently ignore all information about the class hierarchy.
Below is example of the input file:
```
He_Would_a_Hunting_Go     directedBy     George_Nichols_(actor)
Too_Beautiful_for_You     <type>           wikicat_French-language_films
wikicat_1941_musicals     <subClassOf>     wordnet_musical_107019172
```
`Additional textual description`:
To run SSP embedding model, we can attach additional textual description to file `entities_description.txt`, in which each line represents description of a entity:
```
entity[tab]description
```
in which, `entity` shouldn't have any space in between.

We prepared the workspace for IMDB dataset at `./data/imdb/`, FB15K(with entities description) at `./data/fb15k-new/`
 and WIKI44K(with entities description) at `./data/wiki44k/`
and
### 2. Generate training and test set
```
$ bash gen_data.sh <workspace> <training_ratio>
# Ex: $ bash gen_data.sh ./data/imdb/ 0.8
```
### 3. Train the embedding model
We can choose run each of these models:
#### 3.1. TransE with AdaGrad
```
$ bash run_transe.sh --workspace <workspace> --margin <margin> --lr <starting_learning_rate> --ncomp
<embedding_dimensions>
# Ex: $ bash run_transe.sh --workspace ./data/imdb/ --margin 3 --lr 0.1 --ncomp 50
# Ex: $ bash run_transe.sh --workspace ./data/fb15k-new/ --margin 1 --lr 0.1 --ncomp 50
```
The embedding model will run and the embedding data will be stored in file `transe` in the workspace folder.
#### 3.2. HolE with AdaGrad
```
$ bash run_hole.sh --workspace <workspace> --margin <margin> --lr <starting_learning_rate> --ncomp <embedding_dimensions>
# Ex: $ bash run_hole.sh --workspace ./data/imdb/ --margin 0.2 --lr 0.1 --ncomp 128
# Ex: $ bash run_hole.sh --workspace ./data/fb15k-new/ --margin 0.15 --lr 0.1 --ncomp 128
# It is recommmended to used <embedding_dimensions> = 2^x to speed up computation of Fast Fourier Transform.
```
The embedding model will run and the embedding data will be stored in file `hole` in the workspace folder.
#### 3.3. SSP
```
$ ./embedding/ssp_main <workspace> <embedding_dimensions> <learning_rate> <margin> <balance_factor> <joint_weight>
# Ex: $ ./embedding/ssp_main ./data/fb15k-new/ 100 0.001 1.8 0.2 0.1
# This will run the SSP model on FB15K with Joint setting. A joint_weight = 0 indicates the Standard setting.
```
The embedding model will run and the embedding data will be stored in file `ssp` in the workspace folder.
### 4. Run the mining system
```
$ java -jar mining/build.jar -w <workspace> -em <embedding_model>
# Ex: $ java -jar mining/build.jar -w ./data/imdb/ -em transe
```
This command will run the mining system will default config. Output file is at `<workspace>/rules.txt`. Sorted
version on score is at `<workspace>/rules.txt.sorted`. To see full list of supported parameters, run the jar file
without any parameter:
```
$ java -jar mining/build.jar
```
The printed message:
```
Missing required options: w, em
usage: utility-name
 -w,--workspace <arg>                    Path to workspace
 -o,--output <arg>                       Output file path (default: '<workspace>/rules.txt')
 -ms,--min_support <arg>                 Min support of rule (default: 10)
 -mc,--min_conf <arg>                    Min confidence of rule (not counting mrr) (default: 0.1)
 -nv,--max_num_var <arg>                 Maximum number of variables (default: 3)
 -vd,--max_var_deg <arg>                 Maximum variable degree (number of predicates having the same variable) (default: 3)
 -na,--max_num_atom <arg>                Maximum number of atoms (default: 3)
 -nbpa,--max_num_binary_pos_atom <arg>   Maximum number of binary positive atoms (default: INF)
 -nupa,--max_num_unary_pos_atom <arg>    Maximum number of unary positive atoms (default: 0)
 -nna,--max_num_neg_atom <arg>           Maximum number of exception atoms (default: 0)
 -nbna,--max_num_binary_neg_atom <arg>   Maximum number of binary exception atoms (default: 1)
 -nina,--max_num_inst_neg_atom <args>    Maximum number of instantiated exception atoms (default: 0)
 -nuna,--max_num_unary_neg_atom <arg>    Maximum number of unary exception atoms (default: 1)
 -nupo,--max_num_uniq_pred_occur <arg>   Maximum number of occurrence of each unique predicate (default: 2)
 -hc,--min_hc <arg>                      Minimum head coverage of mined rules (default: 0.01)
 -ec,--min_ec <arg>                      Minimum exception confidence of adding exception atom (default: 0.05)
 -em,--embedding_model <arg>             Embedding model ('transe'/'hole'/'ssp')
 -ew,--embedding_weight <arg>            Weight of embedding in score function (default: 0.3)
 -pca,--use_pca_conf                     Use pca confidence instead of standard confidence
 -xyz,--mine_xyz                         Fix the form of positive parts to XYZ
 -dj,--disjunction                       Mine rule with disjunction in the head (experimental)
 -nw,--num_workers <arg>                 Number of parallel workers (default: 8)
```
It is recommended to extend the memory for java job with Xmx option depending on your machine. For example, following command will run the mining system with 100GB RAM.
```
$ java -XX:-UseGCOverheadLimit -Xmx100G -jar mining/build.jar -w <workspace> -em <embedding_model>
```
Mined rules will be outputted to `<outputfile>` and `<outputfile>.sorted`.
### 5. Infer new facts
```
$ bash infer.sh <workspace> <rulesfile> <numrules> <outputfacts>
# Ex: $ bash infer.sh ./data/imdb/ ./data/imdb/rules.txt.sorted 100 ./data/imdb/new_facts.txt
```
This will infer new rules and write to `<outputfacts>`, rules from top `<numrules>` lines will be processed, hence,
remember to use `<rulesfile>`, which contains sorted rules.
### References
...
