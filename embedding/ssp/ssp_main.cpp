#include "Import.hpp"
#include "DetailedConfig.hpp"
#include "LatentModel.hpp"
#include "SemanticModel.hpp"
#include "OrbitModel.hpp"
#include "Task.hpp"
#include <omp.h>
#include <iostream>
#include <sstream>

// args: <workspace> <embedding_dimensions> <learning_rate> <margin>
// <balance_factor> <joint_weight>
int main(int argc, char* argv[]) {
  std::stringstream ss;
  for (auto i = 1; i < argc; ++i) {
    ss << argv[i] << " ";
  }
  string workspace;
  int dim;
  double learning_rate, margin, balance, joint_weight;
  ss >> workspace >> dim >> learning_rate >> margin >> balance >> joint_weight;

  Dataset FB15K(argv[1], argv[1], "/train.txt", "/valid.txt", "/test.txt",
                true);
  string report_path = "/tmp/";
  string semantic_tfile_FB15K = workspace + "/e_desc.txt";

  srand(time(nullptr));

  Model* model = nullptr;

  model = new SemanticModel_Joint(FB15K, LinkPredictionHeadTail, report_path,
                                  semantic_tfile_FB15K, dim, learning_rate,
                                  margin, balance, joint_weight);
  model->run(10000);
  model->test();

  delete model;

  return 0;
}
