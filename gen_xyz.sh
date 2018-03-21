# args: <workspace>
java -jar comparison/rumis-1.0.jar -e=pos -l="$1/training.data.txt"
rm horn-rules.txt
mv horn-rules-stats.txt "$1/xyz.txt"
