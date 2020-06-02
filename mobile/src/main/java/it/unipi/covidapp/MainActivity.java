/*
- Instantiates the Homereceiver and emulates the intent sent when the user has return to his house
- Stop the HandActivityService when the application is closed by the user.
 */


package it.unipi.covidapp;

import androidx.appcompat.app.AppCompatActivity;
import androidx.constraintlayout.widget.ConstraintLayout;

import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.View;
import android.widget.TextView;

public class MainActivity extends AppCompatActivity implements ServiceCallbacks {

    private static final String TAG = "MainHandActivity";

    private ClassificationService classificationService;
    private boolean bound = false;
    private HomeReceiver hr;
    private FeatureExtraction fe;

    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //TODO: Mettere questa parte nel Main del Master app vera (Receiver)
        //Broadcast receiver for user at home. It will start the Service for hand activity detection
        hr = new HomeReceiver();

        Intent intentClassification = new Intent(this, ClassificationService.class);
        bindService(intentClassification, serviceConnection, Context.BIND_AUTO_CREATE);

    }

    public void sendUserHome(View v) {
        ConstraintLayout cl = (ConstraintLayout) findViewById(R.id.backGround);
        cl.setBackgroundColor(Color.WHITE);
        tv = (TextView) findViewById(R.id.textView);
        tv.setText("User comes back home");
        Intent br = new Intent();
        br.setAction("UserInHome");
        sendBroadcast(br);
    }

    /**
     * Callbacks for service binding, passed to bindService()
     */
    private ServiceConnection serviceConnection = new ServiceConnection() {

        @Override
        public void onServiceConnected(ComponentName name, IBinder service) {
            // cast the IBinder and get MyService instance
            ClassificationService.LocalBinder binder = (ClassificationService.LocalBinder) service;
            classificationService = binder.getService();
            bound = true;
            classificationService.setCallbacks(MainActivity.this); // register
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
                ConstraintLayout cl = (ConstraintLayout) findViewById(R.id.backGround);
                tv = (TextView) findViewById(R.id.textView);
                switch (col) {
                    case "GREEN":
                        Log.d(TAG, "GREEN");
                        cl.setBackgroundColor(Color.GREEN);
                        tv.setText("Washing Hands activity detected");
                        break;
                    case "RED":
                        Log.d(TAG, "RED");
                        cl.setBackgroundColor(Color.RED);
                        tv.setText("No washing hands activity detected");
                        break;
                    default:
                        break;
                }

            }
        });
    }

    //TODO: Aggiungere questa on destroy anche nell'app vera per stoppare il servizio
    @Override
    protected void onDestroy() {
        //Unbind from service
        if(bound) {
            classificationService.setCallbacks(null);
            unbindService(serviceConnection);
            bound = false;
        }
        Intent stopService = new Intent(this, HandActivityService.class);
        /*stopService.setAction("Start_HandActivityService");
        stopService.putExtra("Command", it.unipi.covidapp.Configuration.STOP);*/
        stopService(stopService);
        Log.d(TAG, "Service Stopped");
        super.onDestroy();
    }
}

