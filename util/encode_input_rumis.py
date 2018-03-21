import sys

for line in open(sys.argv[1]):
    arr = line.strip().split("\t")
    if (arr[1] == "<type>"):
        print("<%s>\t<type>\t<%s>" % (arr[0], arr[2]))
    else:
        print("<%s>\t<%s>\t<%s>" % (arr[0], arr[1], arr[2]))
