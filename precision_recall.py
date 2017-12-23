import matplotlib.pyplot as plt
import sys

from sklearn.metrics import precision_recall_curve, average_precision_score

# args: <workspace>
INPUT = sys.argv[1] + "/pr_rc_input"
# change here
PR_THRESHOLD = 0.95

if __name__ == '__main__':
    # read data
    classify = []
    score = []
    with open(INPUT, 'r') as fin:
        for line in fin:
            nm = line.strip().split('\t')
            classify.append(int(nm[0]))
            score.append(-int(nm[1]))

    # lambda positive
    average_precision = average_precision_score(classify, score)

    print('(Lamda+) Average precision-recall score: {0:0.2f}'.format(
        average_precision))

    precision, recall, threshold = precision_recall_curve(classify, score)

    plt.step(recall, precision, color='b', alpha=0.2,
             where='post')
    plt.fill_between(recall, precision, alpha=0.2,
                     color='b')

    plt.xlabel('Recall')
    plt.ylabel('Precision')
    plt.ylim([0.0, 1.05])
    plt.xlim([0.0, 1.0])
    plt.title('(Lamda+) Precision-Recall curve: AUC={0:0.2f}'.format(
        average_precision))

    plt.show()

    for iter in range(len(precision)):
        if (precision[iter] >= PR_THRESHOLD):
            print("lamda+ threshold:", -threshold[iter])
            break

    # lambda negative
    for i in range(len(classify)):
        classify[i] = 1 - classify[i];
        score[i] *= -1;

    average_precision = average_precision_score(classify, score)

    print('(Lamda-) Average precision-recall score: {0:0.2f}'.format(
        average_precision))

    precision, recall, threshold = precision_recall_curve(classify, score)

    plt.step(recall, precision, color='b', alpha=0.2,
             where='post')
    plt.fill_between(recall, precision, alpha=0.2,
                     color='b')

    plt.xlabel('Recall')
    plt.ylabel('Precision')
    plt.ylim([0.0, 1.05])
    plt.xlim([0.0, 1.0])
    plt.title('(Lamda-) Precision-Recall curve: AUC={0:0.2f}'.format(
        average_precision))

    plt.show()

    for iter in range(len(precision)):
        if (precision[iter] >= PR_THRESHOLD):
            print("lamda- threshold:", threshold[iter])
            break
