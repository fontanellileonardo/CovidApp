package it.unipi.covidapp;

public class Configuration {

    //value used to start smartwatch.
    //    //0) start hand activity detection on smartwatch
    //    //1) stop hand activity detection on smartwatch
    static final int START = 0;
    static final int STOP = 1;

    static final long DETECTION_DELAY = 180000;     //Timer delay for whole detection period, of 6 minutes
    static final long FAST_SAMPLING_DELAY = 10000;  //Timer delay for fast sampling period, of 10 seconds

    //Range values for accelerometer in hand washing
    static final double X_LOWER_BOUND = -7.5;
    static final double X_UPPER_BOUND = 2.5;
    static final double Y_LOWER_BOUND = -12.0;
    static final double Y_UPPER_BOUND = -1.0;
    static final double Z_LOWER_BOUND = -10.0;
    static final double Z_UPPER_BOUND = 10.0;

}
