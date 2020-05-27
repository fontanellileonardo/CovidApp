package it.unipi.covidapp;

import android.content.Context;

import weka.classifiers.trees.RandomForest;
import weka.core.Instances;
import weka.core.SerializationHelper;
import weka.core.converters.ConverterUtils;

public class RandomForestClassifier {

    private RandomForest randomForest;
    private Context ctx;

    public RandomForestClassifier(Context ctx) {
        this.ctx = ctx;
        this.randomForest = new RandomForest();
        try {
            randomForest = (RandomForest) SerializationHelper.read(ctx.getAssets().open("washHandClassifier.model"));
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public double classify(){
        try {
            // Load unlabeled data
            Instances unlabeled = ConverterUtils.DataSource.read(ctx.getExternalFilesDir(null) + "/unlabeledData.arff");
            // Set class attribute
            unlabeled.setClassIndex(unlabeled.numAttributes() - 1);

            // Label instances. Return 0.0 if the activity is "Others" or 1.0 if the activity is "Washing_Hands"
            return( randomForest.classifyInstance(unlabeled.instance(0)) );
        } catch (Exception e) {
            e.printStackTrace();
            return -1.0;
        }

    }

}
