# args: <workspace>
python util/encode_input_rumis.py "$1/training.data.txt" > tmp
java -jar comparison/rumis-1.0.jar -e=pos -l=tmp
cat horn-rules-stats.txt | awk -F$'\t' '$2 >= 10' > tmp1
java -cp mining/build.jar de.mpii.util.GenXYZ "$1" tmp1 tmp2
cat tmp2 | awk -F$'\t' '$2 >= 10' | sort -t$'\t' -k3 -nr > "$1/xyz.input"
rm horn-rules.txt horn-rules-stats.txt tmp tmp1 tmp2
