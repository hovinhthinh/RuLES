import sys

map = {}
with open("data/fb15k/id2info") as f:
    for line in f:
        arr = line.split("\t")
        map[arr[0]] = arr[1]

with open(sys.argv[1]) as f:
    data = f.readlines()

with open(sys.argv[1] + ".decoded", "w") as f:
    for line in data:
        parts = line.strip().split("\t")
        f.write("%s\t%s\t%s\t%s\n" % (map[parts[0]], parts[1], map[parts[2]], parts[3]))
