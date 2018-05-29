# RuLES - Rule Learning with Embedding Support
[![Build Status](https://travis-ci.org/hovinhthinh/RuLES.svg?branch=master)](https://travis-ci.org/hovinhthinh/RuLES)

RuLES [1] is a system for mining non-monotonic rules from a knowledge graph (KG) under the Open World Assumption (OWA)
that accounts for the guidance from a pre-trained embedding model.

Our system RuLES is implemented in Java 8 and reuses the available implementations of embedding models in different languages. Currently, the system runs on Linux and theoretically it can also run on Windows provided that all required software is installed properly.

### Prerequisites
- For the mining system: `jdk` (we use v1.8.0), `ant` (we use v1.9.4)
- For the embedding models, we currently support TransE [2], HolE [3] and SSP [4] models and reuse their existing
implementations [5,6]. Following software should be installed for the corresponding models:
    - TransE, HolE (implemented in Python): `python` (we use v2.7.9), `numpy` (we use v1.13.1), `scipy` (we use v0.19.1), `scikit-learn` (we use v0.19.0)
    - SSP (implemented in C++): `icc` (we use v18.0.1), `boost` (we use v1.55.0.2), `armadillo` (recommend v4)
### 0. Installation
```
$ cd mining/ && ant build && cd ../
```
This command generates a jar file for the mining system at `./mining/build.jar`. For the embedding models, we reuse existing implementations from the authors of these models. Since the implementation of TransE and HolE are in Python, there is no need for compiling their source code. However, in case we want to use SSP model, which is implemented in C++, we need to run the following command to compile its source code:
```
$ icc -std=c++11 -O3 -qopenmp -larmadillo -xHost embedding/ssp/ssp_main.cpp -o embedding/ssp_main
```
If there is any error, you might need to change the environment source of `icc` before compiling:
```
$ source /opt/intel/bin/compilervars.sh intel64 # (intel64 or ia32 depending on system architecture)
```
### 1. Data Preparation
We need to prepare a `<workspace>`, which is a folder containing the file `ideal.data.txt`, consisting of all facts in the input KG. Each line of this file describes a triple of the input KG in the RDF form:
```
subject[tab]predicate[tab]object
```
where `subject`, `predicate` and `object` should not have any space in between. For representing unary facts, the predicate `<type>` (with the brackets) should be used, and `object` should present the `subject`'s class as usual:
```
entity[tab]<type>[tab]class
```
For representing hierarchy between classes, the predicate `<subClassOf>` (with the brackets) should be used:
```
classA[tab]<subClassOf>[tab]classB
```
However, we currently ignore all information about the class hierarchy.
We provide an example of the input file below:
```
He_Would_a_Hunting_Go     directedBy     George_Nichols_(actor)
Too_Beautiful_for_You     <type>           wikicat_French-language_films
wikicat_1941_musicals     <subClassOf>     wordnet_musical_107019172
```
Additional external data sources are required depending on the chosen embedding model. If we use TransE or HolE models, no external data is needed. However, for the usage of SSP model, the file `entities_description.txt` should also be provided in the `<workspace>`. Each line of this file describes the description of an entity in the following form:
```
entity[tab]description
```
Here, `description` is space-separated and should be preprocessed (e.g. trim, to lower case, remove special characters). Below is an example of the description file:
```
Oklahoma     state of the united states of america
Falkland_Islands     archipelago in the south atlantic ocean
London     capital of england and the united kingdom
```
We prepared the workspace for IMDB dataset at `./data/imdb/`, FB15K(with entities description) at `./data/fb15k-new/`
 and WIKI44K(with entities description) at `./data/wiki44k/`.
### 2. Data Sampling
In the next step, the following command should be run to sample the training KG:
```
$ bash gen_data.sh <workspace> <training_ratio>
# Ex: $ bash gen_data.sh ./data/imdb/ 0.8
```
where `<workspace>` is the workspace folder as described before, and `<training_ratio>` denotes the ratio of the size of the training KG to the size of the input KG. Intuitively, if we want to run the system on the whole input KG, a `<training_ratio>` of 1 should be used. For the evaluation described in [1], the `<training_ratio>` being used is 0.8.
### 3. Embedding Model Training
Depending on the desired embedding model, we run the corresponding commands to train the models.
#### 3.1. TransE with AdaGrad
```
$ bash run_transe.sh --workspace <workspace> --margin <margin> --lr <starting_learning_rate> --ncomp
<embedding_dimension>
# Ex: $ bash run_transe.sh --workspace ./data/imdb/ --margin 3 --lr 0.1 --ncomp 50
# Ex: $ bash run_transe.sh --workspace ./data/fb15k-new/ --margin 1 --lr 0.1 --ncomp 50
```
The embedding model will run and the embedding data will be stored in file `transe` in the workspace folder.
#### 3.2. HolE with AdaGrad
```
$ bash run_hole.sh --workspace <workspace> --margin <margin> --lr <starting_learning_rate> --ncomp <embedding_dimension>
# Ex: $ bash run_hole.sh --workspace ./data/imdb/ --margin 0.2 --lr 0.1 --ncomp 128
# Ex: $ bash run_hole.sh --workspace ./data/fb15k-new/ --margin 0.15 --lr 0.1 --ncomp 128
# We recommmend to use <embedding_dimension> = 2^x to speed up computation of Fast Fourier Transform.
```
The embedding model will run and the embedding data will be stored in file `hole` in the workspace folder.
#### 3.3. SSP
```
$ ./embedding/ssp_main <workspace> <embedding_dimension> <learning_rate> <margin> <balance_factor> <joint_weight>
# Ex: $ ./embedding/ssp_main ./data/fb15k-new/ 100 0.001 1.8 0.2 0.1
# This will run the SSP model on FB15K with Joint setting. A joint_weight = 0 indicates the Standard setting.
```
The embedding model will run and the embedding data will be stored in file `ssp` in the workspace folder.

Please read the original papers [2,3,4] of these models for the meaning of the parameters being used.
### 4. End-to-End Mining System Execution
The following command runs the mining system in its most basic setting:
```
$ java -jar mining/build.jar -w <workspace> -em <embedding_model>
# Ex: $ java -jar mining/build.jar -w ./data/imdb/ -em transe
```
where `<workspace>` is the prepared data folder, and `<embedding_model>` is equal to either `transe`, `hole` or `ssp`, corresponding to the embedding model being used. The system outputs mined rules to the file `<workspace>/rules.txt` on the fly. After the mining process is done, all extracted rules will be ranked in the decreasing order of the hybrid quality and then be written to the file `<workspace>/rules.txt.sorted`.

To see full list of supported parameters, run the jar file without any parameter:
```
$ java -jar mining/build.jar
```
The printed message:
```
Missing required options: w, em
usage: utility-name
 -w,--workspace <arg>                    Path to workspace
 -em,--embedding_model <arg>             Embedding model ('transe'/'hole'/'ssp')
 -ew,--embedding_weight <arg>            Weight of embedding in score function (default: 0.3)
 -nw,--num_workers <arg>                 Number of parallel workers (default: 8)
 -o,--output <arg>                       Output file path (default: '<workspace>/rules.txt')
 -nv,--max_num_var <arg>                 Maximum number of variables (default: 3)
 -vd,--max_var_deg <arg>                 Maximum variable degree (number of predicates having the same variable) (default: 3)
 -nupo,--max_num_uniq_pred_occur <arg>   Maximum number of occurrence of each unique predicate (default: 2)
 -na,--max_num_atom <arg>                Maximum number of atoms (default: 3)
 -nna,--max_num_neg_atom <arg>           Maximum number of exception atoms (default: 0)
 -nbpa,--max_num_binary_pos_atom <arg>   Maximum number of binary positive atoms (default: INF)
 -nupa,--max_num_unary_pos_atom <arg>    Maximum number of unary positive atoms (default: 0)
 -nbna,--max_num_binary_neg_atom <arg>   Maximum number of binary exception atoms (default: 1)
 -nina,--max_num_inst_neg_atom <args>    Maximum number of instantiated exception atoms (default: 0)
 -nuna,--max_num_unary_neg_atom <arg>    Maximum number of unary exception atoms (default: 1)
 -xyz,--mine_xyz                         Fix the form of positive parts to XYZ
 -pca,--use_pca_conf                     Use pca confidence instead of standard confidence
 -ms,--min_support <arg>                 Min support of rule (default: 10)
 -hc,--min_hc <arg>                      Minimum head coverage of mined rules (default: 0.01)
 -mc,--min_conf <arg>                    Min confidence of rule (not counting mrr) (default: 0.1)
 -ec,--min_ec <arg>                      Minimum exception confidence of adding exception atom (default: 0.05)
```
It is recommended to extend the memory for the java job with `Xmx` option depending on the configuration of your machine. For example, following command runs the mining system with 400GB RAM:
```
$ java -XX:-UseGCOverheadLimit -Xmx400G -jar mining/build.jar -w <workspace> -em <embedding_model>
```
### 5. System Extendability
Our system is flexible for plugging in an arbitrary embedding model. Below, we briefly discuss how to do so in Java.

__Step 1:__
After sampling the data, our system generates several files in the `<workspace>`. However, we need to focus on the file `<workspace>/meta.txt`, which stores the signature of the given KG in the following format:
- The first two numbers of the first line are the number of entities _e_ and relations _r_ of the KG, respectively. We index the entities with ids from _0_ to _(e-1)_ and correspondingly the relations with ids from _0_ to _(r-1)_.
- Each line of the next _e_ lines stores the string value of one entity of the KG, from _0<sup>th</sup>_ entity to _(e-1)<sup>th</sup>_ entity.
- Each line of the next _r_ lines stores the string value of one relation of the KG, from _0<sup>th</sup>_ relation to _(r-1)<sup>th</sup>_ relation.

__Step 2:__
Train the custom embedding model on the given KG. In this step, we can reuse the implementation of the embedding model in any language.

__Step 3:__
Create a class extending the abstract class `de.mpii.embedding.EmbeddingClient` that will work as a bridge between our mining system and the pre-trained embedding model. This class must implement the following methods:
- A constructor that has a `String workspace` as one parameter (denoting the `<workspace>` folder) and calls the following constructor of the superclass: `super(workspace);`
- A function that overrides the abstract method `public abstract double getScore(int subject, int predicate, int object)`; returning the likelihood score of a fact given its subject, predicate and object ids, computed based on the pre-trained embedding model. The mapping of these ids to their real values is described in the signature file as before. To interact with the embedding model within this function, a naive strategy is to write all optimized parameters of the trained model in step 2 to a file, and then reload them in the constructor of this class.

__Step 4:__
Edit the constructor of the class ```de.mpii.mining.Miner``` to add a short name (e.g. `transe`,`hole` or `ssp` as we currently have) and a class instantiation for the custom embedding model.

__Step 5:__
Simply use the chosen short name as the value for the parameter `-em,--embedding_model <arg>` when executing the mining system.
### 6. Infering new facts
```
$ bash infer.sh <workspace> <rulesfile> <numrules> <outputfacts>
# Ex: $ bash infer.sh ./data/imdb/ ./data/imdb/rules.txt.sorted 100 ./data/imdb/new_facts.txt
```
This command infers new rules and write to file`<outputfacts>`, rules from top `<numrules>` lines will be processed, hence,
remember to use `<rulesfile>`, which contains sorted rules.
### References
[1] V. Thinh Ho, D. Stepanova, M. Gad-Elrab, E. Kharlamov and G. Weikum. Rule Learning from Knowledge
Graphs Guided by Embedding Models. In Proc. *17th International Semantic Web Conference (ISWC 2018)*, to appear, 2018.

[2] A. Bordes, N. Usunier, A. Garc´ ıa-Duran, J. Weston, and O. Yakhnenko. Translating Embeddings for Modeling
Multi-relational Data. In NIPS, 2013.

[3] M. Nickel, L. Rosasco, and T. A. Poggio. Holographic embeddings of knowledge graphs. In AAAI, 2016.

[4] H. Xiao, M. Huang, L. Meng, and X. Zhu. SSP: semantic space projection for knowledge graph embedding with text
descriptions. In AAAI, 2017.

[5] Reused imlementations of TransE & HolE:
https://github.com/mnick/scikit-kge

[6] Reused implementation of SSP: https://github.com/bookmanhan/Embedding
