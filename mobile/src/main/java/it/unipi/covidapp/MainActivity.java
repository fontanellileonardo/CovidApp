/*
- Instantiates the Homereceiver and emulates the intent sent when the user has return to his house
- Stop the HandActivityService when the application is closed by the user.
 */


package it.unipi.covidapp;

import androidx.appcompat.app.AppCompatActivity;
import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Log;

public class MainActivity extends AppCompatActivity {

    private static final String TAG = "MainHandActivity";

    private HomeReceiver hr;
    private FeatureExtraction fe;

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
        Intent stopService = new Intent(this, HandActivityService.class);
        /*stopService.setAction("Start_HandActivityService");
        stopService.putExtra("Command", it.unipi.covidapp.Configuration.STOP);*/
        stopService(stopService);
        Log.d(TAG, "Service Stopped");
        super.onDestroy();
    }
}

