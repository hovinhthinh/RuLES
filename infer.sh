#!/usr/bin/env bash

ARGS=`echo "$@"`
mvn exec:java -Dexec.mainClass=de.mpii.util.Infer -Dexec.args="$ARGS"