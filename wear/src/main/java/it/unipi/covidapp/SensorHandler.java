/*
Starts or stop the sensing according to the command received by the WearActivityService
There are two timers:
- A longer one used to specify the maximum sensing period.
- A smaller one related to the period in which samples are collected with a faster rate, when values of the
accelerometer corresponing to a possible in progress washing hands action is detected.

 */

package it.unipi.covidapp;

import android.app.IntentService;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Binder;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.IBinder;
import android.os.PowerManager;
import android.util.Log;

import java.io.File;

import java.io.FileWriter;
import java.io.IOException;
import java.util.Timer;
import java.util.TimerTask;

import androidx.annotation.Nullable;


public class SensorHandler extends Service implements SensorEventListener{

    private final IBinder binder = new LocalBinder();
    private ServiceCallbacks serviceCallbacks;

    //Class used for the client Binder
    public class LocalBinder extends Binder {
        SensorHandler getService() {
            return SensorHandler.this;
        }
    }

    private PowerManager.WakeLock wakeLock;

    private SensorManager sm;
    private Sensor accelerometer;
    private Sensor gyroscope;
    private Sensor rotation;
    private Sensor gravity;
    private Sensor linear;

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

    //Used to find out if the fast sampling is in progress
    private boolean started;
    private int counter;

    //Timer used to start and stop the sampling. If no STOP command arrives from the mobile phone,
    //this timer will stop the sampling after a given period.
    private HandlerThread detectionThread;
    private Handler detectionHandler;
    //Timer used to start and stop the sampling with higher rate on the smartwatch
    private HandlerThread fastSamplingThread;
    private Handler fastSamplingHandler;

    private static final String TAG = "SensorHandler";

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, flags, startId);
        Log.d(TAG, "OnStartCommand SensorHandler");
        if(intent.getAction() != null && intent.getAction().compareTo("Command") == 0) {
            String command = intent.getStringExtra("command_key");
            switch(command) {
                case Configuration.START:
                    Log.d(TAG, "Start case");
                    counter = 0;
                    initializeSensorHandler();
                    //Start the sensorListener with a low sampling frequency and initialize the detection timer
                    if(startListener(SensorManager.SENSOR_DELAY_NORMAL)) {
                        initializeDetectionTimer();
                        if(serviceCallbacks != null) {
                            Log.d(TAG, "setBackground");
                            serviceCallbacks.setBackground("BLUE");
                        }
                        Log.d(TAG, "Detection Activated");
                    }
                    else
                        Log.d(TAG,"Error in starting sensors listeners");
                    break;
                case Configuration.STOP:
                    Log.d(TAG, "SensorHandlerService Stopped");
                    //When FastSampling is active the related timer must be cancelled before to stop the service
                    if(started) {
                        wakeLock.release();
                        fastSamplingThread.quit();
                        fastSamplingThread = null;
                        fastSamplingHandler = null;
                    }
                    if(sm != null) {
                        stopListener();
                        if(detectionThread != null) {
                            Log.d(TAG, "DetectionThread is not null");
                            detectionThread.quit();
                            detectionThread = null;
                            detectionHandler = null;
                        }
                        if(serviceCallbacks != null) {
                            Log.d(TAG, "setBackground");
                            serviceCallbacks.setBackground("BLACK");
                        }
                    }
                    else
                        Log.d(TAG, "SensorManager null");
                    stopSelf();
                    break;
                default:
                    Log.d(TAG, "Default Case");
                    break;
            }
        } else {
            Log.d(TAG, "SensorHandler activated");
        }
        return Service.START_STICKY;
    }

   /* @Override
    protected void onHandleIntent(@Nullable Intent intent) {
        Log.d(TAG, "OnHandleIntent SensorHandler");
        if(intent.getAction() != null && intent.getAction().compareTo("Command") == 0) {
            int command = intent.getIntExtra("command_key", -1);
            switch(command) {
                case Configuration.START:
                    Log.d(TAG, "Start case");
                    counter = 0;
                    initializeSensorHandler();
                    //Start the sensorListener with a low sampling frequency and initialize the detection timer
                    if(startListener(SensorManager.SENSOR_DELAY_NORMAL)) {
                        initializeDetectionTimer();
                        Log.d(TAG, "Detection Activated");
                    }
                    else
                        Log.d(TAG,"Error in starting sensors listeners");
                    break;
                case Configuration.STOP:
                    Log.d(TAG, "SensorHandlerService Stopped");
                    //When FastSampling is active the related timer must be cancelled before to stop the service
                    if(started)
                        timerFastSampling.cancel();
                    Log.d(TAG, "SensorManager: "+sm);
                    if(sm != null) {
                        stopListener();
                        timerDetection.cancel();
                    }
                    break;
                default:
                    Log.d(TAG, "Default Case");
                    break;
            }
        }
    }
*/
    private void initializeSensorHandler() {
        Log.d(TAG, "Initialize sensor handler");
        started = false;

        PowerManager powerManager = (PowerManager) getApplicationContext().getSystemService(Context.POWER_SERVICE);
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,
                "HandActivitySignal::WakelockTag");

        sm = (SensorManager) getApplicationContext().getSystemService(SENSOR_SERVICE);

        gyroscope = sm.getDefaultSensor(Sensor.TYPE_GYROSCOPE);
        accelerometer = sm.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        rotation = sm.getDefaultSensor(Sensor.TYPE_GAME_ROTATION_VECTOR);
        gravity = sm.getDefaultSensor(Sensor.TYPE_GRAVITY);
        linear = sm.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION);

        storagePath = getApplicationContext().getExternalFilesDir(null);
        Log.d(TAG, "[STORAGE_PATH]: "+storagePath);
    }

    //Initialize the Detection Timer. When it will expire the sampling operations will be stopped
    private void initializeDetectionTimer() {
        Log.d(TAG, "Timer "+Configuration.DETECTION_DELAY/60000+"  minutes started");
        detectionThread = new HandlerThread("SensorHandler");
        detectionThread.start();
        detectionHandler = new Handler(detectionThread.getLooper());
        detectionHandler.postDelayed(new Runnable() {
            public void run() {
                Log.d(TAG, "run del thread");
                if(started) {
                    wakeLock.release();
                    Log.d(TAG, "wakeLock released");
                    //timerFastSampling.cancel();
                    fastSamplingThread.quit();
                    fastSamplingThread = null;
                    fastSamplingHandler = null;
                }
                if(stopListener())
                    Log.d(TAG, "Detection stopped");
                stopSelf();
            }
        },Configuration.DETECTION_DELAY);

    }

    //Initialize the Fast Sampling Timer. When it will expire the sampling rate will be decreased and
    //an Intent will be sent to the WearActitvitySerivce in order to notify that new data are ready to be sent
    private void initializeTimerFastSampling() {
        Log.d(TAG,"FastSampling timer started at: " + System.nanoTime());
        wakeLock.acquire(Configuration.FAST_SAMPLING_DELAY);
        fastSamplingThread = new HandlerThread("SensorHandler");
        fastSamplingThread.start();
        fastSamplingHandler = new Handler(fastSamplingThread.getLooper());
        fastSamplingHandler.postDelayed(new Runnable() {
            public void run() {
                wakeLock.release();
                //Send to smartphone collected data and decrease the sampling rate
                if(stopListener()) {
                    Log.d(TAG, "FastSampling timer stopped at: "+System.nanoTime());
                    Log.d(TAG, "Timer expired, sending files");
                    sendFile();
                }
                else
                    Log.d(TAG, "Errors in storing collected data");
                startListener(SensorManager.SENSOR_DELAY_NORMAL);
                Log.d(TAG, "Sampling rate decreased");
            }
        },Configuration.FAST_SAMPLING_DELAY);
    }

    //Registers the sensor listener with the specified rate
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

            accel = new File(storagePath, "SensorData_Acc_"+counter+".csv");
            gyr = new File(storagePath, "SensorData_Gyr_"+counter+".csv");
            rot = new File(storagePath, "SensorData_Rot_"+counter+".csv");
            grav = new File(storagePath, "SensorData_Grav_"+counter+".csv");
            linearAcc = new File(storagePath, "SensorData_LinAcc_"+counter+".csv");
            try {
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
            initializeTimerFastSampling();
            Log.d(TAG,"Fast Sampling activated");
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
        if(sm != null)
            sm.unregisterListener(this);
        //Listener could be stopped both from the Service or when changing sampling rate.
        //When fast sampling has been activated we need also to handle file writers used to register collected data
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

    //Send an Intent to the WearActivityService in order to notify that there are new data to send to the phone
    private void sendFile() {
        counter +=1;
        Intent intent= new Intent(this, WearActivityService.class);
        intent.setAction("sendFile");
        intent.putExtra("counter",counter-1);
        this.startService(intent);
    }

    //Check if accelerometer axis data are in the range of values related to a possible hands washing action
    public boolean isInRange(SensorEvent event) {
        if((event.values[0] >= Configuration.X_LOWER_BOUND && event.values[0] <= Configuration.X_UPPER_BOUND) &&
                (event.values[1] >= Configuration.Y_LOWER_BOUND && event.values[1] <= Configuration.Y_UPPER_BOUND) &&
                (event.values[2] >= Configuration.Z_LOWER_BOUND && event.values[2] <= Configuration.Z_UPPER_BOUND)) {
            Log.d(TAG, "ACC_X: "+event.values[0]+", ACC_Y: "+event.values[1]+", ACC_Z: "+event.values[2]+", TIMESTAMP: "+event.timestamp);
            return true;
        }
        else  return false;
    }

    //Handle data sent by the sensors, storing them into files, one for each type of sensor
    public void onSensorChanged(SensorEvent event){
        // Collect data from sensors
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER){
            //Check before if no fast sampling is already started and the values are the desired ones
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
        //When Fast Sampling rate is activated also the other sensors data are stored
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

    @Override
    public void onDestroy() {
        if(started)
            wakeLock.release();
        super.onDestroy();
        Log.d(TAG, "MI SO DISTRUTTO");
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return binder;
    }

    public void setCallbacks(ServiceCallbacks callbacks) {
        serviceCallbacks = callbacks;
    }

}
