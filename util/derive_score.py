with open("data/fb15k/amie.minc0.1hc0.01.mmr.ssp") as f:
    for line in f:
        line = line.strip()
        arr = line.split("\t")
        conf = float(arr[2])
        pca = float(arr[3])
        mrr = float(arr[4])
        print("%s\t%.9f\t%.9f\t%.9f" % (line, conf * 0.5 + mrr * 0.5, conf * 0.2 + mrr * 0.8, conf * 0.8 + mrr * 0.2))
