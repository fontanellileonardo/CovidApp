package it.unipi.covidapp;

public class Configuration {

    //Valus used to send intent for risk index computation
    static final int OTHERS = 0;
    static final int WASHING_HANDS = 1;

    //value used to start smartwatch.
    //    //0) start hand activity detection on smartwatch
    //    //1) stop hand activity detection on smartwatch
    static final int START = 0;
    static final int STOP = 1;

    //TODO: Mettere un timer maggiore di quello dello smartwatch per dare tempo al task di processare
    //tutti i file che sono stati ricevuti poco prima della scadenza del timer altrimenti crasha tutto,
    //l'appicazione si chiude perciò il l'AsyncTask che carica i file da essa generato perde i permessi di accedere ai file
    static final long DELAY = 150000;  //7 minutes timer

    static final int WINDOW_SIZE = 4; //seconds
    static final int FRAGMENT_LENGTH = 8; //seconds
    static final int SIGNAL_LENGTH = 8; //seconds
    static final int SAMPLING_RATE = 50; //seconds
    static final Double DELTA = 0.03; //seconds
    protected enum axis {X,Y,Z,PITCH,ROLL};
}