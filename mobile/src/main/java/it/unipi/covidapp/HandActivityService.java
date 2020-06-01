/*
WearableListenerService that manages the communication between mobile phone and smartwatch.
Starts sampling on smartwatch when the user is at home and stops it when a wash hand activity is recognized or when
the timer expired or the application is closed.
Save data received from the smartwatch and sends an intent to ClassificationService in order to starts
features extraction and classification operations.
Sends the result of the activity classification in broadcast.
 */

package it.unipi.covidapp;

import android.app.Service;
import android.content.Intent;
import android.os.AsyncTask;
import android.util.Log;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.android.gms.tasks.Tasks;
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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;

public class HandActivityService extends WearableListenerService {

    private static final String TAG = "HandActivityService";

    // Name of capability listed in Wear app's wear.xml.
    private static final String CAPABILITY_WEAR_APP = "hand_activity_wear";
    private String watchNodeID;

    //Path for messages exchange
    //Sended for starting the sensing activity on smartwatch
    private static final String START_WATCH_PATH = "/startWatch";
    private static final String START_WATCH_KEY = "start";
    //Used for receiving sensors data from smartwatch
    private static final String SENSOR_DATA_PATH = "/sensorData";
    private static final String SENSOR_DATA_ACC_KEY = "accelerometer";
    private static final String SENSOR_DATA_GYR_KEY = "gyroscope";
    private static final String SENSOR_DATA_ROT_KEY = "rotation";
    private static final String SENSOR_DATA_GRAV_KEY = "gravity";
    private static final String SENSOR_DATA_LIN_KEY = "linear";

    private File storagePath;
    private int counter;

    //Save the state of the detection and send it for risk index computation
    private int detectedActivity;

    private DataClient dataClient;

    //Timer used to stop the sensing on the smartwatch
    private Timer timer;
    private TimerTask timerTask;


    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG,"onStartCommand");
        //Intent received from the ClassificationService with the result of activity recognition
        if(intent.getAction() != null && intent.getAction().compareTo("Classification_Result") == 0) {
            if(intent.getStringExtra("activity_key").compareTo("WASHING_HANDS") == 0) {
                Log.d(TAG, "WASHING_HANDS detected");
                //Stop the sampling on the smartwatch when washing hands is detected
                notifyWatch(Configuration.STOP + ","+(int)System.currentTimeMillis());
                detectedActivity = Configuration.WASHING_HANDS;
            }
            else if(intent.getStringExtra("activity_key").compareTo("OTHERS") == 0) {
                Log.d(TAG,"OTHERS detected");
                if(detectedActivity != Configuration.WASHING_HANDS)
                    detectedActivity = Configuration.OTHERS;
            }
        }
        else if(intent.getAction() != null && intent.getAction().compareTo("Start_HandActivityService") == 0){
            String command = intent.getStringExtra("Command");
            switch(command) {
                case Configuration.START:
                    counter = 0;
                    detectedActivity = -1;
                    dataClient = Wearable.getDataClient(this);
                    storagePath = getExternalFilesDir(null);
                    checkIfWatchHasApp();
                    break;
                default:
                    Log.d(TAG, "Default case");
                    break;
            }

        }
        return Service.START_STICKY;
    }

    // Verify if the corresponding app is installed in a nearby smartwatch
    private void checkIfWatchHasApp() {
        // The full set of nodes that declare the given capability will be included in the capability's
        Task<CapabilityInfo> capabilityInfoTask = Wearable.getCapabilityClient(this)
                .getCapability(CAPABILITY_WEAR_APP, CapabilityClient.FILTER_ALL);

        capabilityInfoTask.addOnCompleteListener(new OnCompleteListener<CapabilityInfo>() {
            @Override
            public void onComplete(Task<CapabilityInfo> task) {

                if (task.isSuccessful()) {
                    Log.d(TAG, "Capability request succeeded.");
                    CapabilityInfo capabilityInfo = task.getResult();
                    Log.d(TAG, "Nodes detected: " + capabilityInfo.getNodes());
                    if(!capabilityInfo.getNodes().isEmpty()) {
                        watchNodeID = ((Node) capabilityInfo.getNodes().toArray()[0]).getId();
                        Log.d(TAG, "watchId: "+watchNodeID);
                        //If a connected watch is detected the mobile asks it to start collecting data
                        notifyWatch(Configuration.START + ","+(int)System.currentTimeMillis());
                    }
                } else {
                    Log.d(TAG, "Capability request failed to return any results.");
                }
            }
        });
    }

    //This function is called in order to start or stop sampling in the smartwatch for activity recognition
    private void notifyWatch(String start) {
        if (watchNodeID != null) {
            PutDataMapRequest putDMR = PutDataMapRequest.create(START_WATCH_PATH);
            putDMR.getDataMap().putString(START_WATCH_KEY, start);
            Log.d(TAG,"Sent value: "+putDMR.getDataMap().getString(START_WATCH_KEY));
            PutDataRequest putDR = putDMR.asPutDataRequest();
            Task<DataItem> putTask = dataClient.putDataItem(putDR);
            Log.d(TAG,"Sent notification to SmartWatch");

            putTask.addOnSuccessListener(
                    new OnSuccessListener<DataItem>() {
                        @Override
                        public void onSuccess(DataItem dataItem) {
                            Log.d(TAG, "Sending notifcation was successful: " + dataItem);
                        }
                    });

            String command = start.split(",")[0];
            if(command.compareTo(Configuration.START) == 0) {
                initializeTimer();
            }
        } else {
            Log.d(TAG,"No devices connected");
        }
    }
/*
    private void selfStop() {
        Intent stopService = new Intent(this, HandActivityService.class);
        if(stopService(stopService))
            Log.d(TAG,"Service Stopped!");
        else
            Log.d(TAG,"Error in sending stop intent");
    }*/

    //Send the broadcast intent for the risk index computation with the result of classification
    private void sendDetection(int result) {
        Intent intent = new Intent();
        intent.setAction("hand_activity_detection");
        intent.putExtra("wash_hand",result);
        detectedActivity = -1;
        sendBroadcast(intent);
    }

    //Initialize the max time interval in which a user should wash his hands when he comes home
    private void initializeTimer() {
        timerTask = new TimerTask() {
            @Override
            public void run() {
                if(detectedActivity != -1){
                    //TODO:Send to smartwatch no hand
                    sendDetection(detectedActivity);
                }
                stopSelf();
            }
        };
        timer = new Timer();
        timer.schedule(timerTask, Configuration.DELAY);

    }

    //Handle the messages received from the smartwatch
    @Override
    public void onDataChanged(@NonNull DataEventBuffer dataEventBuffer) {
        Log.d(TAG, "Message Received");
        for (DataEvent event : dataEventBuffer) {
            if (event.getType() == DataEvent.TYPE_CHANGED &&
                    event.getDataItem().getUri().getPath().compareTo(SENSOR_DATA_PATH) == 0) {
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                if(dataMap.isEmpty()) {
                    Log.d(TAG, "DataMap vuoto");
                }
                else {
                    Asset accelAs = dataMap.getAsset(SENSOR_DATA_ACC_KEY);
                    Asset gyrAs = dataMap.getAsset(SENSOR_DATA_GYR_KEY);
                    Asset rotAs = dataMap.getAsset(SENSOR_DATA_ROT_KEY);
                    Asset gravAs = dataMap.getAsset(SENSOR_DATA_GRAV_KEY);
                    Asset linAs = dataMap.getAsset(SENSOR_DATA_LIN_KEY);

                    // Loads files.
                    new LoadFileTask().execute(accelAs, gyrAs, rotAs, gravAs, linAs);

                }

            }
        }
    }

    //Task involved in saving the data from the smartwatch. At the end of this operation send an Intent
    //to the ClassificationService to start feature extraction and classification operations.
    private class LoadFileTask extends AsyncTask<Asset, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Asset... params) {
            if (params.length == 5) {

                Asset[] assets = params;
                String acc = "SensorData_Acc_"+counter+".csv";
                String gyr = "SensorData_Gyr_"+counter+".csv";
                String rot = "SensorData_Rot_"+counter+".csv";
                String grav = "SensorData_Grav_"+counter+".csv";
                String lin = "SensorData_LinAcc_"+counter+".csv";

                String[] paths = {acc,gyr,rot,grav,lin};

                Log.d(TAG, "Loading the file");

                // convert asset into a file descriptor and block until it's ready
                for(int i=0; i<assets.length; i++) {
                    Task<DataClient.GetFdForAssetResponse> getFdForAssetResponseTask =
                            Wearable.getDataClient(getApplicationContext()).getFdForAsset(assets[i]);
                    InputStream assetInputStream = null;
                    try {
                        DataClient.GetFdForAssetResponse getFdForAssetResponse =
                                Tasks.await(getFdForAssetResponseTask);

                        assetInputStream = getFdForAssetResponse.getInputStream();
                        if (assetInputStream == null) {
                            Log.w(TAG, "Requested an unknown Asset.");
                            return false;
                        }
                    } catch (ExecutionException e) {
                        e.printStackTrace();
                        return false;
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                        return false;
                    }
                    // decode the stream into a file
                    byte[] buffer = new byte[0];
                    try {
                        //TODO: Controllare available
                        buffer = new byte[assetInputStream.available()];
                        assetInputStream.read(buffer);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }

                    File targetFile = new File(storagePath, paths[i]);


                    OutputStream outStream = null;
                    try {
                        outStream = new FileOutputStream(targetFile);
                        outStream.write(buffer);
                    } catch (FileNotFoundException e) {
                        e.printStackTrace();
                        return false;
                    } catch (IOException e) {
                        e.printStackTrace();
                        return false;
                    }
                    Log.d(TAG, "File Ricevuto:"+i);
                }
                return true;
            } else {
                Log.e(TAG, "Asset must be non-null");
                return false;
            }
        }

        @Override
        protected void onPostExecute(Boolean res) {

            if (res) {
                Log.d(TAG, "Successful loading "+counter+"..");
                counter += 1;
                Intent intentClassification = new Intent(getApplicationContext(),ClassificationService.class);
                intentClassification.setAction("Classify");
                intentClassification.putExtra("counter", counter-1);
                startService(intentClassification);
            }
            else {
                Log.d(TAG, "Fail loading..");
            }

        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "StopService");
        notifyWatch(Configuration.STOP + ","+(int)System.currentTimeMillis());
        timer.cancel();
        super.onDestroy();
    }
}
