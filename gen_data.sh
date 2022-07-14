#!/usr/bin/env bash

echo 'Separating ideal data into training and test data'
mvn exec:java -Dexec.mainClass=de.mpii.util.TrainingGen -Dexec.args="$1 $2 true"
echo 'Normalizing data'
python script/gen.py $1
