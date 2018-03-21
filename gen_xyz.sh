# args: <workspace>
python util/encode_input_rumis.py "$1/training.data.txt" > tmp
java -jar comparison/rumis-1.0.jar -e=pos -l=tmp
cat horn-rules-stats.txt | awk -F$'\t' '$2 >= 10' > "$1/xyz.input"
rm horn-rules.txt
rm horn-rules-stats.txt
rm tmp
