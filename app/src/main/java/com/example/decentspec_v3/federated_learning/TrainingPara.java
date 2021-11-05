package com.example.decentspec_v3.federated_learning;

import org.deeplearning4j.nn.api.OptimizationAlgorithm;
import org.deeplearning4j.nn.conf.MultiLayerConfiguration;
import org.deeplearning4j.nn.conf.NeuralNetConfiguration;
import org.deeplearning4j.nn.conf.layers.DenseLayer;
import org.deeplearning4j.nn.conf.layers.OutputLayer;
import org.deeplearning4j.nn.multilayer.MultiLayerNetwork;
import org.nd4j.linalg.activations.Activation;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.learning.config.Adam;
import org.nd4j.linalg.lossfunctions.LossFunctions;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class TrainingPara {
    // ML paras
    public int EPOCH_NUM;
    public int BATCH_SIZE;
    public double LEARNING_RATE;
    public List<Integer> MODEL_STRUCTURE;
    public Map<String, INDArray> GLOBAL_WEIGHT;

    // std paras
    public List<Float> DATASET_AVG;
    public List<Float>  DATASET_STD;

    public Integer DATASET_SIZE;
    public String DATASET_NAME;
    public ArrayList<String> MINER_LIST;
    public Integer BASE_GENERATION;
    public String SEED_NAME;

    public MultiLayerNetwork buildModel() {
        return new MultiLayerNetwork(LayerConfigBuilder(MODEL_STRUCTURE));
    }

    private MultiLayerConfiguration LayerConfigBuilder(List<Integer> structure) {
        NeuralNetConfiguration.ListBuilder confBuilder = new NeuralNetConfiguration.Builder()
                .optimizationAlgo(OptimizationAlgorithm.STOCHASTIC_GRADIENT_DESCENT)
                .updater(new Adam(LEARNING_RATE))
                .list();
        int layerNum = structure.size();

        // hidden layer build
        for (int i = 0; i < layerNum - 2; i++ ) {
            int sizeIn = structure.get(i);
            int sizeOut = structure.get(i+1);
            confBuilder.layer(i, new DenseLayer.Builder()
                    .nIn(sizeIn).nOut(sizeOut)
                    .activation(Activation.RELU)
                    .build());
        }
        // output layer build
        confBuilder.layer(layerNum - 2, new OutputLayer.Builder()
                .nIn(structure.get(layerNum - 2)).nOut(structure.get(layerNum - 1))
                .activation(Activation.IDENTITY)
                .lossFunction(LossFunctions.LossFunction.MSE)
                .build());
        return confBuilder.build();
    }
}
