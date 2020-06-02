/*
- Emulates the intent sent when the user came back to his home.
- Stop the HandActivityService when the application is closed by the user.
- Changes the background color of the application according to the result of activity classification
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

    private TextView tv;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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
            // cast the IBinder and get ClassificationService instance
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

    //Changes the background color of the application according to the result of activity classification
    @Override
    public void setBackground(String color) {
        final String col = color;
        new Handler(Looper.getMainLooper()).post(new Runnable() {
            @Override
            public void run() {
                ConstraintLayout cl = (ConstraintLayout) findViewById(R.id.backGround);
                tv = (TextView) findViewById(R.id.textView);
                switch (col) {
                    case "GREEN":
                        cl.setBackgroundColor(Color.GREEN);
                        tv.setText("Washing Hands activity detected");
                        break;
                    case "RED":
                        cl.setBackgroundColor(Color.RED);
                        tv.setText("No washing hands activity detected");
                        break;
                    default:
                        break;
                }

            }
        });
    }

    @Override
    protected void onDestroy() {
        //Unbind from service
        if(bound) {
            classificationService.setCallbacks(null);
            unbindService(serviceConnection);
            bound = false;
        }
        Intent stopService = new Intent(this, HandActivityService.class);
        stopService(stopService);
        Log.d(TAG, "Service Stopped");
        super.onDestroy();
    }
}

