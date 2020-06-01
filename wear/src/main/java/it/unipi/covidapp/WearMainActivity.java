

package it.unipi.covidapp;


import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.support.wearable.activity.WearableActivity;

import android.util.Log;

import androidx.wear.widget.BoxInsetLayout;

public class WearMainActivity extends WearableActivity implements ServiceCallbacks{

    private static final String TAG = "WearMainActivity";
    private SensorHandler sensorHandlerService;
    private boolean bound = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // Enables Always-on
        setAmbientEnabled();

        Intent intentSensorHandler = new Intent(this, SensorHandler.class);
        bindService(intentSensorHandler, serviceConnection, Context.BIND_AUTO_CREATE);


    }

    /**
     * Callbacks for service binding, passed to bindService()
     */
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // cast the IBinder and get MyService instance
            SensorHandler.LocalBinder binder = (SensorHandler.LocalBinder) service;
            sensorHandlerService = binder.getService();
            bound = true;
            sensorHandlerService.setCallbacks(WearMainActivity.this); // register
        }

        @Override
        public void onServiceDisconnected(ComponentName arg0) {
            bound = false;
        }
    };

    @Override
    public void setBackground(String color) {
        final String col = color;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG, "setBackground");
                BoxInsetLayout bil = (BoxInsetLayout) findViewById(R.id.background);
                switch (col) {
                    case "BLUE":
                        bil.setBackgroundColor(0xFF1075C5);
                        break;
                    case "BLACK":
                        bil.setBackgroundColor(Color.BLACK);
                        break;
                    default:
                        break;
                }

            }
        });
    }

    @Override
    public void onDestroy() {
        //Unbind from service
        if(bound) {
            sensorHandlerService.setCallbacks(null);
            unbindService(serviceConnection);
            bound = false;
        }
        super.onDestroy();
        Log.d(TAG,"Activity stopped");
    }


}
