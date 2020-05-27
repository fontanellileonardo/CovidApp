package it.unipi.covidapp;

import android.app.Service;
import android.content.Intent;
import android.util.Log;
import android.widget.TextView;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.CapabilityClient;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.NonNull;
import androidx.core.provider.SelfDestructiveThread;

public class WearActivityService extends WearableListenerService {

    private static final String TAG = "HandService";

    // Name of capability listed in Phone app's wear.xml.
    private static final String CAPABILITY_PHONE_APP = "hand_activity_phone";

    //Path for messages exchange
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

    //Timer used to stop the sensing on the smartwatch
    private Timer timer;
    private TimerTask timerTask;

    private SensorHandler sh;
    private String phoneNodeID;

    @Override
    public void onCreate() {
        super.onCreate();
        System.out.println("Service started");
        dataClient = Wearable.getDataClient(this);
        sh = new SensorHandler(this);

        checkIfPhoneHasApp();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        if (intent.getAction().compareTo("sendFile") == 0) {
            sendFile();
        }
        return Service.START_STICKY;
    }

    private void initializeTimer() {
        Log.d(TAG, "Timer 5 minutes started");
        timerTask = new TimerTask() {
            @Override
            public void run() {
                if(sh.stopListener())
                    Log.d(TAG, "Detection stopped");
            }
        };
        timer = new Timer();
        timer.schedule(timerTask, Configuration.DETECTION_DELAY);

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Log.d(TAG,"Service stopped");
    }

    // Verify if the corresponding app is installed in the mobile phone
    private void checkIfPhoneHasApp() {
        Log.d(TAG, "checkIfPhoneHasApp()");
        // The full set of nodes that declare the given capability will be included in the capability's
        Task<CapabilityInfo> capabilityInfoTask = Wearable.getCapabilityClient(this)
                .getCapability(CAPABILITY_PHONE_APP, CapabilityClient.FILTER_ALL);

        capabilityInfoTask.addOnCompleteListener(new OnCompleteListener<CapabilityInfo>() {
            @Override
            public void onComplete(Task<CapabilityInfo> task) {

                if (task.isSuccessful()) {
                    Log.d(TAG, "Capability request succeeded.");
                    CapabilityInfo capabilityInfo = task.getResult();
                    Log.d(TAG, "nodes detected: " + capabilityInfo.getNodes());
                    if(!capabilityInfo.getNodes().isEmpty()) {
                        phoneNodeID = ((Node) capabilityInfo.getNodes().toArray()[0]).getId();
                        Log.d(TAG, "phoneId: "+phoneNodeID);
                    }
                } else {
                    Log.d(TAG, "Capability request failed to return any results.");
                }
            }
        });
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
    public void sendFile() {
        if (phoneNodeID != null) {

            Asset accelAs = toAsset("/SensorData_Acc.csv");
            Asset gyrAs = toAsset("/SensorData_Gyr.csv");
            Asset rotAs = toAsset("/SensorData_Rot.csv");
            Asset gravAs = toAsset("/SensorData_Grav.csv");
            Asset linAs = toAsset("/SensorData_LinAcc.csv");

            if(accelAs!=null && gyrAs!=null && rotAs!=null && gravAs!=null && linAs!=null ) {
                PutDataMapRequest putDMR = PutDataMapRequest.create(SENSOR_DATA_PATH);
                putDMR.getDataMap().putAsset(SENSOR_DATA_ACC_KEY, accelAs);
                putDMR.getDataMap().putAsset(SENSOR_DATA_GYR_KEY, gyrAs);
                putDMR.getDataMap().putAsset(SENSOR_DATA_ROT_KEY, rotAs);
                putDMR.getDataMap().putAsset(SENSOR_DATA_GRAV_KEY, gravAs);
                putDMR.getDataMap().putAsset(SENSOR_DATA_LIN_KEY, linAs);
                //Log.d(TAG, "Valore nella request: "+putDMR.getDataMap().getAsset(SENSOR_DATA_ACC_KEY));
                PutDataRequest putDR = putDMR.asPutDataRequest();
                Log.d(TAG, "Generating DataItem: " + putDR);
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
        } else {
           Log.d(TAG,"No devices connected");
        }
    }

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
                    int command = dataMap.getInt(START_WATCH_KEY);
                    if(command == Configuration.START) {
                        Log.d(TAG, "Smartwatch sensing");
                        if(sh.startListener()) {
                            initializeTimer();
                            Log.d(TAG,"Detection activated");
                        }
                        else
                            Log.d(TAG,"Error in starting sensors listeners");
                    } else {
                        Log.d(TAG, "Stop sensing");
                        sh.stopListener();
                        timer.cancel();
                    }
                }

            }
        }
    }
}
