#!/usr/bin/env bash

echo 'Separating ideal data into training and test data'
javac util/TrainingGen.java
java -classpath util/ TrainingGen $1 $2 false
rm util/TrainingGen.class
echo 'Normalizing data'
python util/gen.py $1
