/*
- Handles data sent from the mobile phone. It will start the sensing or stop it,
  according tothe requesto from the phone
- Send collected data to the mobile phone
*/

package it.unipi.covidapp;

import android.app.Service;
import android.content.Intent;
import android.util.Log;

import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;

import androidx.annotation.NonNull;

public class WearActivityService extends WearableListenerService {

    private static final String TAG = "WearActivityService";

    //Path for messages exchange
    //Path for starting the sampling
    private static final String START_WATCH_PATH = "/startWatch";
    private static final String START_WATCH_KEY = "start";

    //Path for sending messages
    private static final String SENSOR_DATA_PATH = "/sensorData";
    private static final String SENSOR_DATA_ACC_KEY = "accelerometer";
    private static final String SENSOR_DATA_GYR_KEY = "gyroscope";
    private static final String SENSOR_DATA_ROT_KEY = "rotation";
    private static final String SENSOR_DATA_GRAV_KEY = "gravity";
    private static final String SENSOR_DATA_LIN_KEY = "linear";

    private DataClient dataClient;

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.println("Service started");
        dataClient = Wearable.getDataClient(this);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        //Send files to the mobile phone when sensors data are collected by SensorHandler
        if (intent.getAction() != null && intent.getAction().compareTo("sendFile") == 0) {
            int numberFile = intent.getIntExtra("counter", -1);
            if(numberFile != -1)
                sendFile(numberFile);
        }
        return Service.START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"Service stopped");
    }

    private Asset toAsset(String name) {
        File file = new File(this.getExternalFilesDir(null) + name);
        int size = (int) file.length();
        byte[] bytes = new byte[size];
        try {
            BufferedInputStream buf = new BufferedInputStream(new FileInputStream(file));
            buf.read(bytes, 0, bytes.length);
            buf.close();
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        } catch (IOException e) {
            e.printStackTrace();
            return null;
        }

        Asset as = Asset.createFromBytes(bytes);
        //file.delete();
        return as;
    }
    public void sendFile(int counter) {

        Asset accelAs = toAsset("/SensorData_Acc_"+counter+".csv");
        Asset gyrAs = toAsset("/SensorData_Gyr_"+counter+".csv");
        Asset rotAs = toAsset("/SensorData_Rot_"+counter+".csv");
        Asset gravAs = toAsset("/SensorData_Grav_"+counter+".csv");
        Asset linAs = toAsset("/SensorData_LinAcc_"+counter+".csv");

        if(accelAs!=null && gyrAs!=null && rotAs!=null && gravAs!=null && linAs!=null ) {
            PutDataMapRequest putDMR = PutDataMapRequest.create(SENSOR_DATA_PATH);
            putDMR.getDataMap().putAsset(SENSOR_DATA_ACC_KEY, accelAs);
            putDMR.getDataMap().putAsset(SENSOR_DATA_GYR_KEY, gyrAs);
            putDMR.getDataMap().putAsset(SENSOR_DATA_ROT_KEY, rotAs);
            putDMR.getDataMap().putAsset(SENSOR_DATA_GRAV_KEY, gravAs);
            putDMR.getDataMap().putAsset(SENSOR_DATA_LIN_KEY, linAs);
            PutDataRequest putDR = putDMR.asPutDataRequest();
            Task<DataItem> putTask = dataClient.putDataItem(putDR);
            Log.d(TAG,"Data sent");

            putTask.addOnSuccessListener(
                    new OnSuccessListener<DataItem>() {
                        @Override
                        public void onSuccess(DataItem dataItem) {
                            Log.d(TAG, "Sending sensors data was successful: " + dataItem);
                        }
                    });
        } else
            Log.d(TAG, "Some assets can't be created. Repeat the operations");

    }

    //Handles data sent from the mobile phone. It will start the sensing or stop it,
    // according tothe requesto from the phone
    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEventBuffer) {
        Log.d(TAG, "Message Received");
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().compareTo(START_WATCH_PATH) == 0) {
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                if(dataMap.isEmpty()) {
                    Log.d(TAG, "DataMap vuoto");
                }
                else {
                    String command = (dataMap.getString(START_WATCH_KEY)).split(",")[0];
                    if(command.compareTo(Configuration.START) == 0) {
                        Log.d(TAG, "Start Smartwatch sensing");
                        Intent startIntent = new Intent(this, SensorHandler.class);
                        startIntent.setAction("Command");
                        startIntent.putExtra("command_key", Configuration.START);
                        startService(startIntent);
                    } else if(command.compareTo(Configuration.STOP) == 0) {
                        Log.d(TAG, "Stop sensing");
                        Intent stopIntent = new Intent(this, SensorHandler.class);
                        stopIntent.setAction("Command");
                        stopIntent.putExtra("command_key", Configuration.STOP);
                        startService(stopIntent);
                    }
                    else {
                        Log.d(TAG, String.valueOf(command));
                    }
                }

            }
        }
    }


}
