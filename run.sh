#!/usr/bin/env bash

ARGS=`echo "$@"`
mvn exec:java -Dexec.mainClass=de.mpii.Main -Dexec.args="$ARGS"