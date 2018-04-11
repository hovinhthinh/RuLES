import sys

workspace = sys.argv[1]

pred_map = {}

with open(workspace + "/ideal.data.txt") as f:
    for line in f.readlines():
        pred = line.strip().split("\t")[1]
        if pred == "<type>" or pred == "<subClassOf>":
            continue
        if pred not in pred_map:
            pred_map[pred] = {"ideal": 0, "train": 0}
        cnt = pred_map[pred]
        cnt["ideal"] = cnt["ideal"] + 1

with open(workspace + "/training.data.txt") as f:
    for line in f.readlines():
        pred = line.strip().split("\t")[1]
        if pred == "<type>" or pred == "<subClassOf>":
            continue
        if pred not in pred_map:
            pred_map[pred] = {"ideal": 0, "train": 0}
        cnt = pred_map[pred]
        cnt["train"] = cnt["train"] + 1

arr = [(k, v) for (k, v) in pred_map.items()]

arr.sort(key=lambda e: e[1]["ideal"], reverse=True)

with open(workspace + "/predicate_distribution.txt", "w") as f:
    for e in arr:
        f.write('%s\t%s\t%s\n' % (e[0], e[1]["ideal"], e[1]["train"]))
