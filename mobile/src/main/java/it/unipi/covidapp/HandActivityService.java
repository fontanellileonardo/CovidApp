package it.unipi.covidapp;

import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
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

    private static final String TAG = "HandService";

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
    private boolean status;

    //Save the state of the detection and send it for risk index computation
    private int detectedActivity;

    private DataClient dataClient;

    //Timer used to stop the sensing on the smartwatch
    private Timer timer;
    private TimerTask timerTask;


    private RandomForestClassifier classifier;
    private FeatureExtraction fe;

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG,"Service started");
        detectedActivity = -1;
        dataClient = Wearable.getDataClient(this);
        fe = new FeatureExtraction(getApplicationContext());
        storagePath = getExternalFilesDir(null);
        checkIfWatchHasApp();
        return Service.START_STICKY;
    }

    // Verify if the corresponding app is installed in the mobile phone
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
                        notifyWatch(Configuration.START);
                    }
                } else {
                    Log.d(TAG, "Capability request failed to return any results.");
                }
            }
        });
    }

    //This function is called in order to start or stop sampling in the smartwatch for activity recognition
    private void notifyWatch(int start) {
        if (watchNodeID != null) {
            PutDataMapRequest putDMR = PutDataMapRequest.create(START_WATCH_PATH);
            putDMR.getDataMap().putInt(START_WATCH_KEY, start);
            Log.d(TAG,"Sent value: "+putDMR.getDataMap().getInt(START_WATCH_KEY));
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

            if(start == Configuration.START)
                initializeTimer();
        } else {
            Log.d(TAG,"No devices connected");
        }
    }

    private void selfStop() {
        Intent stopService = new Intent(this, HandActivityService.class);
        if(stopService(stopService))
            Log.d(TAG,"Service Stopped!");
        else
            Log.d(TAG,"Error in sending stop intent");
    }

    private void sendDetection(int result) {
        //Send the intent for the risk index computation
        Intent intent = new Intent();
        intent.setAction("hand_activity_detection");
        intent.putExtra("wash_hand",result);
        detectedActivity = -1;
    }

    private void initializeTimer() {
        timerTask = new TimerTask() {
            @Override
            public void run() {
                if(detectedActivity == Configuration.OTHERS){
                    //TODO:Send to smartwatch no hand
                    sendDetection(Configuration.OTHERS);
                }
                selfStop();
            }
        };
        timer = new Timer();
        timer.schedule(timerTask, Configuration.DELAY);

    }

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
                    if(status) {
                        status = fe.calculateFeatures();
                        if (status) {
                            classifier = new RandomForestClassifier(this);
                            double activity = classifier.classify();
                            //The classifier can return 0.0 for "Others" activity, 1.0 for "Washing_Hands"
                            // activity or -1.0 in case of errors.
                            if(activity == 1.0) {
                                sendDetection(Configuration.WASHING_HANDS);
                                selfStop();
                            }
                            else if(activity == 0.0) {
                                detectedActivity = Configuration.OTHERS;
                            }
                        }
                    }
                }

            }
        }
    }

    private class LoadFileTask extends AsyncTask<Asset, Void, Boolean> {
        @Override
        protected Boolean doInBackground(Asset... params) {
            if (params.length == 5) {

                Asset[] assets = params;
                String acc = "SensorData_Acc.csv";
                String gyr = "SensorData_Gyr.csv";
                String rot = "SensorData_Rot.csv";
                String grav = "SensorData_Grav.csv";
                String lin = "SensorData_LinAcc.csv";

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
                Log.d(TAG, "Successful loading..");
                status = true;
            }
            else {
                Log.d(TAG, "Fail loading..");
                status = false;
            }

        }
    }

    @Override
    public boolean stopService(Intent name) {
        notifyWatch(Configuration.STOP);
        timer.cancel();
        return super.stopService(name);
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        //TODO: send adv to smartwatch
    }
}
