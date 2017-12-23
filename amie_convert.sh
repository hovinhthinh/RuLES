#!/usr/bin/env bash

javac comparison/AMIEConverter.java
java -classpath comparison/ AMIEConverter $1
rm comparison/AMIEConverter.class
