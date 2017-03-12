package org.deeplearning4j.arbiter.scoring.graph;

import org.deeplearning4j.arbiter.optimize.api.data.DataProvider;
import org.deeplearning4j.arbiter.optimize.api.score.ScoreFunction;
import org.deeplearning4j.arbiter.scoring.RegressionValue;
import org.deeplearning4j.arbiter.scoring.graph.util.ScoreUtil;
import org.deeplearning4j.eval.RegressionEvaluation;
import org.deeplearning4j.nn.graph.ComputationGraph;
import org.nd4j.linalg.api.ndarray.INDArray;
import org.nd4j.linalg.dataset.DataSet;
import org.nd4j.linalg.dataset.api.MultiDataSet;
import org.nd4j.linalg.dataset.api.iterator.DataSetIterator;
import org.nd4j.linalg.dataset.api.iterator.MultiDataSetIterator;

import java.util.Map;

/**
 * Score function for regression (including multi-label regression)
 * for a {@link ComputationGraph} on a
 * {@link DataSetIterator}
 *
 * @author Alex Black
 */
public class GraphTestSetRegressionScoreFunctionDataSet implements ScoreFunction<ComputationGraph, DataSetIterator> {

    private final RegressionValue regressionValue;

    /**
     * @param regressionValue The type of evaluation to
     *                        do: MSE, MAE, RMSE, etc
     */
    public GraphTestSetRegressionScoreFunctionDataSet(RegressionValue regressionValue) {
        this.regressionValue = regressionValue;
    }

    @Override
    public double score(ComputationGraph model, DataProvider<DataSetIterator> dataProvider, Map<String, Object> dataParameters) {
        DataSetIterator testSet = dataProvider.testData(dataParameters);
        return ScoreUtil.score(model,testSet,regressionValue);
    }

    @Override
    public boolean minimize() {
        return regressionValue != RegressionValue.CorrCoeff;    //Maximize correlation coefficient, minimize the remaining ones
    }

    @Override
    public String toString() {
        return "GraphTestSetRegressionScoreFunctionDataSet(type=" + regressionValue + ")";
    }
}
