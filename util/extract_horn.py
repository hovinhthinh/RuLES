import sys

# sys.argv = ["$", "./exp3/xyzna4nv3nna1mc.1hc.0cms10ew.3.sorted", "0.2", "./exp3/fb15k.embed.10.ec02"]
# create embedding training dataset for imdb.
input = sys.argv[1]
mec = float(sys.argv[2])

horn_to_line = {}

out = []
with open(input) as f:
    for line in f.readlines():
        line = line.strip()
        not_pos = line.find(', not ')
        if not_pos == -1:
            continue
        ec = float(line.split('\t')[12])
        if ec < mec:
            continue
        horn = line[0:not_pos]
        if horn in horn_to_line:
            continue
        horn_to_line[horn] = line
        out.append(line)

with open(sys.argv[3], "w") as f:
    for line in out:
        f.write(line + "\n")
