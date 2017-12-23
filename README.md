# Enhancing Rule mining with Embedding
[![Build Status](https://travis-ci.org/hovinhthinh/kg-comp-embedding-rule.svg?branch=master)](https://travis-ci.org/hovinhthinh/kg-comp-embedding-rule)
### Prerequisites
python, numpy, scipy, scikit-learn, jdk, ant
### 0. Build the project
```
$ cd mining/ && ant build && cd ../
```
This will generate a jar file for the mining system at `./mining/build.jar`
### 1. Workspace
We should prepare a folder containing a single data file  with name `ideal.data.txt` of the knowledge graph, in which each triple is represented in one line using the RDF form:
```
<entity>[tab]<predicate>[tab]<object>
```
For representing unary predicate, the `<predicate>` of the triple should be `<type>`:
```
<entity>[tab]<type>[tab]<class>
```
For representing hierarchical between classes, the `<predicate>` of the triple should be `<subClassOf>`:
```
<classA>[tab]<subClassOf>[tab]<classB>
```
However, we currently ignore all information about the class hierarchy.
Below is example of the input file:
```
<He_Would_a_Hunting_Go>     <directedBy>     <George_Nichols_(actor)>
<Too_Beautiful_for_You>     <type>           <wikicat_French-language_films>
<wikicat_1941_musicals>     <subClassOf>     <wordnet_musical_107019172>
```
We prepared the workspace for IMDB dataset at `./data/imdb/`
### 2. Generate training and test set
```
$ bash gen_data.sh <workspace>
# Ex: $ bash gen_data.sh ./data/imdb/
```
### 3. Train the embedding with TransE
We currently support TransE as the embedding model.
```
$ bash run_transe.sh --workspace <workspace> --margin <margin> --lr <learning_rate> --ncomp <embedding_dimensions>
# Ex: $ bash run_transe.sh --workspace ./data/imdb/ --margin 3 --lr 0.1 --ncomp 50
```
The embedding model will run and the embedding data will be stored in file `embedding` in the workspace folder.
### 4. Run the mining system
```
$ java -jar mining/build.jar -w <workspace>
# Ex: $ java -jar mining/build.jar -w ./data/imdb/
```
This command will run the mining system will default config. Output file is at `<workspace>/rules.txt`. Sorted
version on score is at `<workspace>/rules.txt.sorted`. To see full list of supported parameters, run the jar file
without any parameter:
```
$ java -jar mining/build.jar
```
The printed message:
```
Missing required options: w
usage: utility-name
 -w,--workspace <arg>                    Path to workspace
 -o,--output <arg>                       Output file path (default: '<workspace>/rules.txt')
 -nv,--max_num_var <arg>                 Maximum number of variables (default: 4)
 -vd,--max_var_deg <arg>                 Maximum variable degree (number of predicates having the same variable) (default: 3)
 -na,--max_num_atom <arg>                Maximum number of atoms (default: 5)
 -nbpa,--max_num_binary_pos_atom <arg>   Maximum number of binary positive atoms (default: INF)
 -nupa,--max_num_unary_pos_atom <arg>    Maximum number of unary positive atoms (default: INF)
 -nna,--max_num_neg_atom <arg>           Maximum number of exception atoms (default: 1)
 -nbna,--max_num_binary_neg_atom <arg>   Maximum number of binary exception atoms (default: 1)
 -nuna,--max_num_unary_neg_atom <arg>    Maximum number of unary exception atoms (default: 1)
 -nupo,--max_num_uniq_pred_occur <arg>   Maximum number of occurrence of each unique predicate (default: 2)
 -hc,--min_hc <arg>                      Minimum head coverage of mined rules (default: 0.02)
 -ec,--min_ec <arg>                      Minimum exception coverage of adding exception atom (default: 0.2)
 -ew,--embedding_weight <arg>            Weight of embedding in score function (default: 0.8)
 -pca,--use_pca_conf                     Use pca confidence instead of standard confidence
 -nw,--num_workers <arg>                 Number of parallel workers (default: 8)
```
It is recommended to extend the memory for java job with Xmx option depending on your machine. For example, following command will run the mining system with 100GB RAM.
```
$ java -Xmx100G -jar mining/build.jar -w <workspace> -o <outputfile>
```
Mined rules will be outputted to `<outputfile>` and `<outputfile>.sorted`.
### 5. Infer new facts
```
$ bash infer.sh <workspace> <rulesfile> <numrules> <outputfacts>
# Ex: $ bash infer.sh ./data/imdb/ ./data/imdb/rules.txt.sorted 100 ./data/imdb/new_facts.txt
```
This will infer new rules and write to `<outputfacts>`, rules from top `<numrules>` lines will be processed, hence,
remember to use `<rulesfile>`, which contains sorted rules.
