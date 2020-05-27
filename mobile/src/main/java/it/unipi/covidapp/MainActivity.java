package it.unipi.covidapp;

import androidx.appcompat.app.AppCompatActivity;
import weka.classifiers.trees.RandomForest;

import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "HandActivity";

    private HomeReceiver hr;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //TODO: Mettere questa parte nel Main del Master app vera (Receiver)
        //Broadcast receiver for user at home. It will start the Service for hand activity detection
        hr = new HomeReceiver();
        Intent br = new Intent();
        br.setAction("UserInHome");
        sendBroadcast(br);
    }

    //TODO: Aggiungere questa on destroy anche nell'app vera per stoppare il servizio
    @Override
    protected void onDestroy() {
        super.onDestroy();
        Intent stopService = new Intent(this, HandActivityService.class);
        boolean status = stopService(stopService);
        if(status)
            Log.d(TAG, "Service Stopped");
        else
            Log.d(TAG,"Error in sending stop intent");

    }
}

