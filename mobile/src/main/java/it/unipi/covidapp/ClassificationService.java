/*
Performs the features extraction and the classification and sends an Intent to the HandActivityService
with the result of the classification
 */

package it.unipi.covidapp;

import android.app.IntentService;
import android.content.Intent;
import android.util.Log;

import androidx.annotation.Nullable;


public class ClassificationService extends IntentService {

    private String TAG = "ClassificationService";
    private boolean status;
    private FeatureExtraction fe;
    private RandomForestClassifier rfc;

    private Intent intentResult;

    public ClassificationService() {
        super("ClassificationService");

    }

    @Override
    public int onStartCommand(@Nullable Intent intent, int flags, int startId) {
        Log.d(TAG, "Started");
        fe = new FeatureExtraction(this);
        rfc = new RandomForestClassifier(this);
        intentResult = new Intent(this, HandActivityService.class);
        intentResult.setAction("Classification_Result");
        status = false;
        return super.onStartCommand(intent, flags, startId);
    }

    //Performs the features extraction and the classification and sends an intent to the HandActivityService
    //with the result of the classification
    private void handleClassification(int counter) {
        status = fe.calculateFeatures(counter);
        if(status) {
            double activity = rfc.classify();
            //The classifier can return 0.0 for "Others" activity, 1.0 for "Washing_Hands"
            // activity or -1.0 in case of errors.
            if(activity == 1.0) {
                Log.d(TAG, "WASHING_HANDS");
                intentResult.putExtra("activity_key","WASHING_HANDS");

            }
            else if(activity == 0.0) {
                Log.d(TAG,"OTHERS");
                intentResult.putExtra("activity_key","OTHERS");
            }
            startService(intentResult);
        }
    }

    @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        if(intent.getAction() != null && intent.getAction().compareTo("Classify")==0) {
            int counter = intent.getIntExtra("counter", -1);
            if(counter != -1)
                handleClassification(counter);
            else
                Log.d(TAG, "Counter value not correct");

        }


    }
}
