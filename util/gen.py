import os.path
import sys
from random import shuffle

# create embedding training dataset for imdb.
WORKSPACE = sys.argv[1]
TEST_NUM = 2000
VALID_NUM = 2000

with open(WORKSPACE + '/training.data.txt', 'r') as fin:
    data = fin.readlines()
with open(WORKSPACE + '/test.data.txt', 'r') as fin:
    data_test = fin.readlines()

entities = {}
entities_arr = []

relations = {}
relations_arr = []

data_encoded = []
data_test_encoded = []

types_encoded = []
types_test_encoded = []

types = {}
types_arr = []

num_err = 0

for line in data:
    arr = line.strip().split('\t')
    if len(arr) != 3:
        # print('err:', line)
        num_err += 1
        continue
    s, p, o = arr
    if p == '<subClassOf>':
        continue
    if s not in entities:
        entities[s] = len(entities_arr)
        entities_arr.append(s)
    if p == '<type>':
        if o not in types:
            types[o] = len(types_arr)
            types_arr.append(o)
        types_encoded.append((entities[s], types[o]))
        continue
    if o not in entities:
        entities[o] = len(entities_arr)
        entities_arr.append(o)
    if p not in relations:
        relations[p] = len(relations_arr)
        relations_arr.append(p)

    data_encoded.append((entities[s], relations[p], entities[o]))

for line in data_test:
    arr = line.strip().split('\t')
    if len(arr) != 3:
        # print('err:', line)
        num_err += 1
        continue
    s, p, o = arr
    if p == '<subClassOf>':
        continue
    if s not in entities:
        entities[s] = len(entities_arr)
        entities_arr.append(s)
    if p == '<type>':
        if o not in types:
            types[o] = len(types_arr)
            types_arr.append(o)
        types_test_encoded.append((entities[s], types[o]))
        continue
    if o not in entities:
        entities[o] = len(entities_arr)
        entities_arr.append(o)
    if p not in relations:
        relations[p] = len(relations_arr)
        relations_arr.append(p)

    data_test_encoded.append((entities[s], relations[p], entities[o]))

print('num_err:', num_err)

with open(WORKSPACE + '/meta.txt', 'w') as out:
    out.write("%d\t%d\t%d\n" % (len(entities_arr), len(relations_arr), len(types_arr)))
    for v in entities_arr:
        out.write("%s\n" % v)
    for v in relations_arr:
        out.write("%s\n" % v)
    for v in types_arr:
        out.write("%s\n" % v)
    for v in range(len(types_encoded)):
        out.write("%d\t%d\n" % types_encoded[v])

for i in range(5):
    shuffle(data_encoded)
    shuffle(data_test_encoded)

with open(WORKSPACE + '/test.txt', 'w') as out:
    for v in range(TEST_NUM):
        out.write("%d\t%d\t%d\n" % data_test_encoded[v]);

with open(WORKSPACE + '/valid.txt', 'w') as out:
    for v in range(TEST_NUM, TEST_NUM + VALID_NUM):
        out.write("%d\t%d\t%d\n" % data_test_encoded[v]);

with open(WORKSPACE + '/train.txt', 'w') as out:
    for v in range(len(data_encoded)):
        out.write("%d\t%d\t%d\n" % data_encoded[v]);

# Gen description data
if os.path.isfile(WORKSPACE + "/entities_description.txt"):
    print('Processing entities description')
    entity2id = {}
    e = None
    with open(WORKSPACE + "/meta.txt") as f:
        line = f.readline()
        e = int(line.split("\t")[0])
        for i in range(e):
            entity2id[f.readline().strip()] = i

    data = {}
    with open(WORKSPACE + "/entities_description.txt") as f:
        for line in f.readlines():
            arr = line.strip().split("\t")
            if arr[0] in entity2id:
                data[entity2id[arr[0]]] = arr[1];
            else:
                print('invalid entity in description file:', arr[0])

    with open(WORKSPACE + "/e_desc.txt", "w") as f:
        for i in range(e):
            if i in data:
                f.write("%d\t%s\n" % (i, data[i]))
            else:
                f.write("%d\t\n" % (i))
