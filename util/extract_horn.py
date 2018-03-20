import os.path
import sys
from random import shuffle

# create embedding training dataset for imdb.
input = sys.argv[1]

horn_to_line = {}

with open(input) as f:
    for line in f.readlines():
        not_pos = line.find('not')
        if not_pos == -1:
            continue
        horn = line[0:not_pos - 2]
        if horn in horn_to_line:
            continue
        horn_to_line[horn] = line


for (k, v) in horn_to_line:
    print(v)

