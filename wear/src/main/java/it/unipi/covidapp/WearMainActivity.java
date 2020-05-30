

package it.unipi.covidapp;


import android.content.Intent;
import android.os.Bundle;
import android.support.wearable.activity.WearableActivity;

import android.util.Log;

public class WearMainActivity extends WearableActivity {

    private static final String TAG = "WearMainActivity";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Enables Always-on
        //setAmbientEnabled();

        Intent intentWearable = new Intent(this, WearActivityService.class);
        intentWearable.setAction("start_listener");
        startService(intentWearable);



    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"Activity stopped");
    }
}
