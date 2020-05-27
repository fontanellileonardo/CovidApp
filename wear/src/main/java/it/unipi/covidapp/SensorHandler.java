package it.unipi.covidapp;

import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;

import com.google.android.gms.wearable.Asset;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import static android.content.Context.SENSOR_SERVICE;

public class SensorHandler implements SensorEventListener{

    private SensorManager sm;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor rotation;
    private Sensor gravity;
    private Sensor linear;
    private Context ctx;

    final float[] rotationMatrix = new float[9];
    final float[] orientationAngles = new float[3];

    private File storagePath;
    private File accel;
    private File gyr;
    private File rot;
    private File grav;
    private File linearAcc;

    private FileWriter writerAcc;
    private FileWriter writerGyr;
    private FileWriter writerRot;
    private FileWriter writerGrav;
    private FileWriter writerLin;

    private Boolean started;

    //Timer used to start and stop the sampling with higher rate on the smartwatch
    private Timer timer;
    private TimerTask timerTask;


    private static final String TAG = "SensorHandler";

    public SensorHandler(Context ctx) {
        Log.d(TAG, "Sono nel sensor handler");
        this.ctx = ctx;
        started = false;

        sm = (SensorManager) this.ctx.getSystemService(SENSOR_SERVICE);

        gyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotation = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
        linear = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        storagePath = this.ctx.getExternalFilesDir(null);
        Log.d(TAG, "[STORAGE_PATH]: "+storagePath);
    }

    private void initializeTimer() {
        //TODO: Cancellare log
        Log.d(TAG,"Sono nel delay game timer");
        timerTask = new TimerTask() {
            @Override
            public void run() {
                //Send to smartphone collected data and decrease the sampling rate
                if(stopListener()) {
                    Log.d(TAG, "Timer expired, sending files");
                    sendFile();
                }
                else
                    Log.d(TAG, "Errors in storing collected data");
                startListener();
                Log.d(TAG, "Sampling rate decreased");
            }
        };
        timer = new Timer();
        timer.schedule(timerTask, Configuration.FAST_SAMPLING_DELAY);

    }

    protected Boolean startListener() {
        Log.d(TAG,"startListener");
        return startListener(SensorManager.SENSOR_DELAY_NORMAL);
    }

    protected Boolean startListener(int rate){
        if(rate == SensorManager.SENSOR_DELAY_NORMAL){
            Log.d(TAG, "Delay normal activated");
            return sm.registerListener(this, accelerometer, rate);
        }


        if(rate == SensorManager.SENSOR_DELAY_GAME &&
            sm.registerListener(this, accelerometer, rate) &&
            sm.registerListener(this, rotation, rate) &&
            sm.registerListener(this, gyroscope, rate) &&
            sm.registerListener(this, gravity, rate) &&
            sm.registerListener(this, linear, rate) ) {

            //When a possible hand wash in progress is detected, the sampling rate is incremented for
            // a period of 10 seconds and data collected in this time are stored in files,
            // one for each sensor involved

            accel = new File(storagePath, "SensorData_Acc.csv");
            gyr = new File(storagePath, "SensorData_Gyr.csv");
            rot = new File(storagePath, "SensorData_Rot.csv");
            grav = new File(storagePath, "SensorData_Grav.csv");
            linearAcc = new File(storagePath, "SensorData_LinAcc.csv");
            try {
                //TODO: Cancelare log
                Log.d(TAG, "Riapro i file");
                writerAcc = new FileWriter(accel);
                writerGyr = new FileWriter(gyr);
                writerRot = new FileWriter(rot);
                writerGrav = new FileWriter(grav);
                writerLin = new FileWriter(linearAcc);
            } catch (IOException e) {
                e.printStackTrace();
                //FileWriter creation could be failed so the rate must be reset on low frequency rate
                Log.d(TAG,"Some writer is failed");
                stopListener();
                sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
                return false;
            }
            started = true;
            initializeTimer();
            Log.d(TAG,"Delay game activated");
            return true;
        } else {
            //registerListener on some sensor could be failed so the rate must be reset on low frequency rate
            stopListener();
            sm.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);
            Log.d(TAG,"Some registration is failed");
            return false;
        }

    }

    //Called when detection period of 5 minutes is finished or when changing the sampling period
    protected Boolean stopListener(){
        sm.unregisterListener(this);
        //Listener could be stopped both from the Service or when changing sampling rate.
        //When fast sampling has been activated we need also to handle file writers used to register coolected data
        if(started) {
            try {
                writerAcc.flush();
                writerAcc.close();
                writerGyr.flush();
                writerGyr.close();
                writerRot.flush();
                writerRot.close();
                writerGrav.flush();
                writerGrav.close();
                writerLin.flush();
                writerLin.close();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
            started = false;
        }
        return true;
    }

    private void sendFile() {
        Intent intent= new Intent(ctx, WearActivityService.class);
        intent.setAction("sendFile");
        ctx.startService(intent);
    }

/*
    private Asset toAsset(String name) {
        File file = new File(storagePath + name);
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
        if (phoneNodeId != null) {

            Asset accelAs = toAsset("/SensorData_Acc" + counter + ".csv");
            Asset gyrAs = toAsset("/SensorData_Gyr" + counter + ".csv");
            Asset rotAs = toAsset("/SensorData_Rot" + counter + ".csv");
            Asset gravAs = toAsset("/SensorData_Grav" + counter + ".csv");
            Asset linAs = toAsset("/SensorData_LinAcc" + counter + ".csv");

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
                mTextView.setText("Sended");

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
            mTextView.setText("No devices connected");
        }
    }
*/
    public boolean isInRange(SensorEvent event) {
        return ((event.values[0] >= Configuration.X_LOWER_BOUND && event.values[0] <= Configuration.X_UPPER_BOUND) &&
                (event.values[1] >= Configuration.Y_LOWER_BOUND && event.values[1] <= Configuration.Y_UPPER_BOUND) &&
                (event.values[2] >= Configuration.Z_LOWER_BOUND && event.values[2] <= Configuration.Z_UPPER_BOUND));
    }

    public void onSensorChanged(SensorEvent event){
        // Collect data from sensors
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            //Check before if no fast sampling is already started
            if((!started) && isInRange(event)) {
                Log.d(TAG, "Hands in washing position detected");
                stopListener();
                startListener(SensorManager.SENSOR_DELAY_GAME);
            }
            //When the sampling rate is high sensed data are stored in the relative files
            else if(started){
                String temp = event.values[0] + "," + event.values[1] + "," + event.values[2] + "," + event.timestamp + ",\n";
                try {
                    writerAcc.append(temp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        else if(started) {
            if (event.sensor.getType() == Sensor.TYPE_LINEAR_ACCELERATION) {
                String temp = event.values[0] + "," + event.values[1] + "," + event.values[2] + "," + event.timestamp + ",\n";
                try {
                    writerLin.append(temp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE) {
                String temp = event.values[0] + "," + event.values[1] + "," + event.values[2] + "," + event.timestamp + ",\n";
                try {
                    writerGyr.append(temp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (event.sensor.getType() == Sensor.TYPE_GAME_ROTATION_VECTOR) {
                SensorManager.getRotationMatrixFromVector(rotationMatrix, event.values);
                SensorManager.getOrientation(rotationMatrix, orientationAngles);
                String temp = (Math.toDegrees(orientationAngles[1])) + "," + (Math.toDegrees(orientationAngles[2])) + "," + event.timestamp + ",\n";
                try {
                    writerRot.append(temp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            if (event.sensor.getType() == Sensor.TYPE_GRAVITY) {
                String temp = event.values[0] + "," + event.values[1] + "," + event.values[2] + "," + event.timestamp + ",\n";
                try {
                    writerGrav.append(temp);
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }

    public void onAccuracyChanged(Sensor sensor, int accuracy){

    }
}
