/*
Creates an ARFF file needed as input for the classifier and computes the features.
Handles missing samples replacing them with signal's mean.
 */

package it.unipi.covidapp;

import android.content.Context;
import android.util.Log;

import com.opencsv.CSVReader;

import org.apache.commons.math3.stat.descriptive.moment.Mean;
import org.apache.commons.math3.stat.descriptive.moment.StandardDeviation;
import org.apache.commons.math3.stat.descriptive.moment.Kurtosis;
import org.apache.commons.math3.stat.descriptive.moment.Skewness;
import org.apache.commons.math3.stat.descriptive.moment.Variance;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;

public class FeatureExtraction {

    private static final String TAG = "FeatureExtraction";
    private Context ctx;

    private File featureFile;


    private FileWriter featureFileWriter;

    private Mean mn;
    private Variance var;
    private StandardDeviation stDv;
    private Kurtosis kurtosis;
    private Skewness skewness;

    private HashMap<Integer, CSVReader> csvMap = new HashMap<Integer, CSVReader>();
    private HashMap<Integer, String[]> rowMap = new HashMap<Integer, String[]>();
    private HashMap<Integer, Double> timeLastMap = new HashMap<Integer, Double>();

    private boolean status;

    public FeatureExtraction(Context ctx) {
        this.ctx = ctx;
        mn = new Mean();
        var = new Variance();
        stDv = new StandardDeviation();
        kurtosis = new Kurtosis();
        skewness = new Skewness();

        status = true;

        featureFile = new File(ctx.getExternalFilesDir(null), "unlabeledData.arff");
        try {
            // The file doesn't exists -> The header of the arff file has to be created
            featureFileWriter = new FileWriter(featureFile);
            headerBuild(featureFileWriter, Configuration.WINDOW_SIZE);
        }catch(IOException e) {
            Log.d(TAG, "Errore nel chiudere 0");
            e.printStackTrace();
            try {
                featureFileWriter.close();
            } catch (IOException ex) {
                Log.d(TAG, "Errore nel chiudere 1");
                ex.printStackTrace();
            }
            status = false;
            return;
        }
    }

    //Create the header of the ARFF file used from weka module with all the attributes extracted
     private void headerBuild(FileWriter file, int windowsSize) throws IOException{
        file.write("@RELATION trainingSet \n \n");

         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++)
             file.append("@ATTRIBUTE AccX_win" + i + "_mean REAL\n@ATTRIBUTE AccX_win" + i + "_stDv REAL\n@ATTRIBUTE AccX_win" + i + "_kurtosis REAL\n");
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++)
             file.append("@ATTRIBUTE AccY_win" + i + "_mean REAL\n@ATTRIBUTE AccY_win" + i + "_stDv REAL\n");
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++){
             file.append("@ATTRIBUTE AccZ_win" + i + "_mean REAL\n@ATTRIBUTE AccZ_win" + i + "_stDv REAL\n@ATTRIBUTE AccZ_win" + i + "_kurtosis REAL\n");
             if(i == 1)
                 file.append("@ATTRIBUTE AccZ_win" + i + "_skewness REAL\n");
         }
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++){
             file.append("@ATTRIBUTE GyrX_win" + i + "_stDv REAL\n");
             if(i == 1)
                 file.append("@ATTRIBUTE GyrX_win" + i + "_kurtosis REAL\n");
         }
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++)
             file.append("@ATTRIBUTE GyrY_win" + i + "_mean REAL\n@ATTRIBUTE GyrY_win" + i + "_stDv REAL\n@ATTRIBUTE GyrY_win" + i + "_skewness REAL\n");

         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++) {
             file.append("@ATTRIBUTE GyrZ_win" + i + "_mean REAL\n@ATTRIBUTE GyrZ_win" + i + "_stDv REAL\n");
             if(i == 1)
                 file.append("@ATTRIBUTE GyrZ_win" + i + "_kurtosis REAL\n");
             file.append("@ATTRIBUTE GyrZ_win" + i + "_skewness REAL\n");
         }
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++)
             file.append("@ATTRIBUTE LinAccX_win" + i + "_mean REAL\n@ATTRIBUTE LinAccX_win" + i + "_stDv REAL\n");
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++){
             if(i == 1)
                 file.append("@ATTRIBUTE LinAccY_win" + i + "_mean REAL\n");
             file.append("@ATTRIBUTE LinAccY_win" + i + "_stDv REAL\n");
             if(i == 1)
                 file.append("@ATTRIBUTE LinAccY_win" + i + "_kurtosis REAL\n");
         }
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++) {
             file.append("@ATTRIBUTE LinAccZ_win" + i + "_mean REAL\n@ATTRIBUTE LinAccZ_win" + i + "_stDv REAL\n");
             if(i == 2)
                 file.append("@ATTRIBUTE LinAccZ_win" + i + "_skewness REAL\n");
         }
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++) {
             file.append("@ATTRIBUTE GravX_win" + i + "_mean REAL\n@ATTRIBUTE GravX_win" + i + "_stDv REAL\n");
             if(i == 1)
                 file.append("@ATTRIBUTE GravX_win" + i + "_kurtosis REAL\n");
         }
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++)
             file.append("@ATTRIBUTE GravY_win" + i + "_mean REAL\n@ATTRIBUTE GravY_win" + i + "_stDv REAL\n@ATTRIBUTE GravY_win" + i + "_skewness REAL\n");
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++)
             file.append("@ATTRIBUTE GravZ_win" + i + "_mean REAL\n@ATTRIBUTE GravZ_win" + i + "_stDv REAL\n@ATTRIBUTE GravZ_win" + i + "_skewness REAL\n");
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++)
             file.append("@ATTRIBUTE RotPitch_win" + i + "_mean REAL\n@ATTRIBUTE RotPitch_win" + i + "_stDv REAL\n@ATTRIBUTE RotPitch_win" + i + "_skewness REAL\n");
         for (int i = 1; i <= Configuration.FRAGMENT_LENGTH/windowsSize; i++){
             file.append("@ATTRIBUTE RotRoll_win" + i + "_mean REAL\n");
             if(i == 1)
                 file.append("@ATTRIBUTE RotRoll_win" + i + "_skewness REAL\n");
             if(i == 2)
                 file.append("@ATTRIBUTE RotRoll_win" + i + "_stDv REAL\n@ATTRIBUTE RotRoll_win" + i + "_kurtosis REAL\n");
         }

        file.append("@ATTRIBUTE class {Others, Washing_Hands}\n" + "\n" + "@DATA\n");
        file.flush();
    }

    public Boolean calculateFeatures(int counter) {
        Log.d(TAG, "calculateFeatures called");
        Log.d(TAG, "COUNTER VALUE: "+counter);
        if(!status) // an error occurs in the constructor so this method can't be executed
            return false;

        File file_acc = new File(ctx.getExternalFilesDir(null), "SensorData_Acc_"+counter+".csv");
        File file_gyr = new File(ctx.getExternalFilesDir(null), "SensorData_Gyr_"+counter+".csv");
        File file_rot = new File(ctx.getExternalFilesDir(null), "SensorData_Rot_"+counter+".csv");
        File file_grav = new File(ctx.getExternalFilesDir(null), "SensorData_Grav_"+counter+".csv");
        File file_linAcc = new File(ctx.getExternalFilesDir(null), "SensorData_LinAcc_"+counter+".csv");

        Boolean result = true;
        try {
            // Initialize the CSVReaders and put them in a HashMap
            csvMap.put(0,new CSVReader(new FileReader(file_acc.getAbsolutePath())));
            csvMap.put(1,new CSVReader(new FileReader(file_gyr.getAbsolutePath())));
            csvMap.put(2,new CSVReader(new FileReader(file_linAcc.getAbsolutePath())));
            csvMap.put(3,new CSVReader(new FileReader(file_grav.getAbsolutePath())));
            csvMap.put(4,new CSVReader(new FileReader(file_rot.getAbsolutePath())));

            //Keep the last row seen
            rowMap.put(0,(csvMap.get(0)).readNext());
            rowMap.put(1,(csvMap.get(1)).readNext());
            rowMap.put(2,(csvMap.get(2)).readNext());
            rowMap.put(3,(csvMap.get(3)).readNext());
            rowMap.put(4,(csvMap.get(4)).readNext());

            //Keep the last timestamp of the last row seen
            timeLastMap.put(0, Double.parseDouble((rowMap.get(0))[3]));
            timeLastMap.put(1, Double.parseDouble((rowMap.get(1))[3]));
            timeLastMap.put(2, Double.parseDouble((rowMap.get(2))[3]));
            timeLastMap.put(3, Double.parseDouble((rowMap.get(3))[3]));
            timeLastMap.put(4, Double.parseDouble((rowMap.get(4))[2]));

            // Scrolls the whole file containing samples, taking fragment by fragment into consideration.
            // The whole signal is subdivided into fragments, and in each fragment there are the windows.
            for(int i = 0; i < Configuration.SIGNAL_LENGTH / Configuration.FRAGMENT_LENGTH; i++) {
                // extract the features for each window in each fragment

                result = (extractFeature(0) && extractFeature(1) && extractFeature(2) &&
                        extractFeature(3) && extractFeatureRotation(4));
                if(!result) {
                    return result;
                }
            }
        } catch (FileNotFoundException e) {
            Log.d(TAG, "Errore nel chiudere 2");
            e.printStackTrace();
            return false;
        }catch(IOException e) {
            Log.d(TAG, "Errore nel chiudere 3");
            e.printStackTrace();
            return false;
        }
        Log.d(TAG, "Features extracted");
        if (result) {
            try {
                featureFileWriter.flush();
                (csvMap.get(0)).close();
                (csvMap.get(1)).close();
                (csvMap.get(2)).close();
                (csvMap.get(3)).close();
                (csvMap.get(4)).close();
            } catch (IOException e) {
                Log.d(TAG, "Errore nel chiudere 4");
                e.printStackTrace();
                closeFiles();
                return false;
            }
        }
        closeFiles();
        file_acc.delete();
        file_grav.delete();
        file_gyr.delete();
        file_linAcc.delete();
        file_rot.delete();

        return result;
    }

    public Boolean extractFeature(Integer key) {
        double[] x_axis = new double[Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE];
        double[] y_axis = new double[Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE];
        double[] z_axis = new double[Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE];
        int count = 0;
        try {
            //Retrieve last inspected row for the previous fragment
            String[] row = rowMap.get(key);
            Double tsPrec = timeLastMap.get(key);

            // Inserts in each coordinate all the values of a fragment
            for (; count < Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE && row != null; count++) {
                //For the subsequent read lines, check if there are missing values
                Double tsNow = (Double.parseDouble(row[3])/1000000000);  //timestamp in seconds
                if(tsNow <= tsPrec + Configuration.DELTA) {
                    //There's no missing value, the sample can be added as it is
                    x_axis[count] = Double.parseDouble(row[0]);
                    y_axis[count] = Double.parseDouble(row[1]);
                    z_axis[count] = Double.parseDouble(row[2]);
                    tsPrec = tsNow;
                    row = (csvMap.get(key)).readNext();
                }
                else {
                    //there is a missing value, the current sample has not been collected at the rate expected
                    //The missing value is replaced by the mean between the current read value
                    //and the previous one
                    //The previous timestamp is updated referring to the sampling rate
                    if (count == 0) {
                        //It's the first line
                        x_axis[count] = Double.parseDouble(row[0]);
                        y_axis[count] = Double.parseDouble(row[1]);
                        z_axis[count] = Double.parseDouble(row[2]);
                    } else {
                        x_axis[count] = (Double.parseDouble(row[0]) + x_axis[count - 1]) / 2;
                        y_axis[count] = (Double.parseDouble(row[1]) + y_axis[count - 1]) / 2;
                        z_axis[count] = (Double.parseDouble(row[2]) + z_axis[count - 1]) / 2;
                    }
                    tsPrec = (tsPrec + (1.0 / Configuration.SAMPLING_RATE));
                }

            }

            //The received file has not enough data in order to continue for the classification
            if (count < Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE) {
                Log.d(TAG, "Not enough data in file of sensor with key "+key);
                Log.d(TAG, "Count value: "+count);
                //TODO: decidere se riempire gli utlimi valori che mancano, in base ad una threshold su quanti valori mancano
                return false;
            }
            rowMap.put(key, row);
            timeLastMap.put(key, tsPrec);

            computeFeature(x_axis, key, Configuration.axis.X);
            computeFeature(y_axis, key, Configuration.axis.Y);
            computeFeature(z_axis, key, Configuration.axis.Z);

        } catch (IOException e) {
            Log.d(TAG, "Errore nel chiudere 5");
            e.printStackTrace();
            return false;
        } catch (IndexOutOfBoundsException ie) {
            Log.d(TAG, "Errore nel chiudere 6");
            Log.d(TAG, "Count: "+count);
            Log.d(TAG, "KEY: "+key);
            return false;
        }
        return true;
    }

    public Boolean extractFeatureRotation(Integer key){
        double[] pitch = new double[Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE];
        double[] roll = new double[Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE];
        int count = 0;
        try {
            //Retrieve last inspected row for the previous fragment
            String[] row = rowMap.get(key);
            Double tsPrec = timeLastMap.get(key);;  //timestamp in seconds;

            // Insert in each coordinate all the values of a fragment
            for (; count < Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE && row != null; count++) {

                //For the subsequent read lines, check if there are missing values
                Double tsNow = (Double.parseDouble(row[2])/1000000000);
                if(tsNow <= tsPrec + Configuration.DELTA) {
                    //There's no missing value, the sample can be added as it is
                    pitch[count] = Double.parseDouble(row[0]);
                    roll[count] = Double.parseDouble(row[1]);
                    tsPrec = tsNow;
                    row = (csvMap.get(key)).readNext();
                }
                else {
                    //there is a missing value, the current sample has not been collected at the rate expected
                    //The missing value is replaced by the mean between the current read value
                    //and the previous one
                    //The previous timestamp is updated referring to the sampling rate
                    if (count == 0) {
                        //It's the first line
                        pitch[count] = Double.parseDouble(row[0]);
                        roll[count] = Double.parseDouble(row[1]);
                    } else {
                        pitch[count] = (Double.parseDouble(row[0]) + pitch[count - 1]) / 2;
                        roll[count] = (Double.parseDouble(row[1]) + roll[count - 1]) / 2;
                    }
                    tsPrec = (tsPrec + (1.0 / Configuration.SAMPLING_RATE));
                }

            }

            if(count < Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE) {
                return false;
            }

            rowMap.put(key, row);
            timeLastMap.put(key, tsPrec);

            computeFeature(pitch, key, Configuration.axis.PITCH);
            computeFeature(roll, key, Configuration.axis.ROLL);

        }catch(IOException e) {
            Log.d(TAG, "Errore nel chiudere 7");
            e.printStackTrace();
            return false;
        }
        return true;
    }

    /*
    // compute mean and standard deviation and skewness in a fragment
    public void computeSharedFeature(double[] data, int key, Configuration.axis ax) throws IOException {
        for (int i = 0;
             i < Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE && i < data.length;
             i += (Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE)) {
            double mean = mn.evaluate(data, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE);
            featureFileWriter.append(mean + ",");
            featureFileWriter.append(stDv.evaluate(data, mean, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE) + ",");
            if((key == 1 && ax==Configuration.axis.Z && i==0) || (key == 2 && ax==Configuration.axis.Z && i!=0) ||
                    (key==4 && ax==Configuration.axis.PITCH && i==0)) {
                double skew = skewness.evaluate(data, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE);
                if(Double.isNaN(skew))
                    featureFileWriter.append(0.0 + ",");
                else
                    featureFileWriter.append(skew + ",");
                if(key==4 && ax==Configuration.axis.PITCH && i==0)
                    return;
            }
        }
    }

    //Compute mean and standard deviation for gravity (x and y axis)
    public void computeFeature(double[] data, Configuration.axis ax) throws IOException {
        for (int i = 0;
             i < Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE && i < data.length;
             i += (Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE)) {
            double mean = mn.evaluate(data, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE);
            featureFileWriter.append(mean + ",");
            if(ax==Configuration.axis.X && i!=0)
                featureFileWriter.append(stDv.evaluate(data, mean, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE) + ",");
            if(ax==Configuration.axis.Y && i==0)
                return;
        }
    }
*/
    //TODO: Check private and public methods

    private void computeSkewness(double[] data, int i) throws IOException{
        double skew = skewness.evaluate(data, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE);
        if(Double.isNaN(skew))
            featureFileWriter.append(0.0 + ",");
        else
            featureFileWriter.append(skew + ",");
    }

    private void computeKurtosis(double[] data, int i) throws IOException{
        double kurt = kurtosis.evaluate(data, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE);
        if(Double.isNaN(kurt))
            featureFileWriter.append(-2.041 + ",");
        else
            featureFileWriter.append(kurt + ",");
    }

    private void computeMeanDevStd(double[] data, int key, Configuration.axis ax) throws IOException{
        for (int i = 0;
             i < Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE && i < data.length;
             i += (Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE)) {
            double mean = mn.evaluate(data, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE);
            featureFileWriter.append(mean + ",");
            featureFileWriter.append(stDv.evaluate(data, mean, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE) + ",");
            if(key == 2 && ax==Configuration.axis.Z && i!=0) {
                computeSkewness(data, i);
            }
            if(key == 3 && ax==Configuration.axis.X && i==0) {
                computeKurtosis(data, i);
            }
        }
    }

    private void computeMeanDevStdKurt(double[] data, int key, Configuration.axis ax) throws IOException {
        for (int i = 0;
             i < Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE && i < data.length;
             i += (Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE)) {
            double mean = mn.evaluate(data, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE);
            if(key == 2 && ax==Configuration.axis.Y && i!=0) {
                featureFileWriter.append(stDv.evaluate(data, mean, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE) + ",");
                return;
            }
            featureFileWriter.append(mean + ",");
            featureFileWriter.append(stDv.evaluate(data, mean, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE) + ",");
            computeKurtosis(data, i);
            if(key == 0 && ax==Configuration.axis.Z && i==0 ) {
                computeSkewness(data, i);
            }
        }
    }

    private void computeGyrXStat(double[] data) throws IOException{
        for (int i = 0;
             i < Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE && i < data.length;
             i += (Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE)) {
            double mean = mn.evaluate(data, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE);
            featureFileWriter.append(stDv.evaluate(data, mean, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE) + ",");
            if(i == 0)
                computeKurtosis(data, i);
        }
    }

    private void computeRotRollStat(double[] data) throws IOException{
        for (int i = 0;
             i < Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE && i < data.length;
             i += (Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE)) {
            double mean = mn.evaluate(data, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE);
            featureFileWriter.append(mean + ",");
            if(i == 0)
                computeSkewness(data, i);
            else {
                featureFileWriter.append(stDv.evaluate(data, mean, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE) + ",");
                computeKurtosis(data, i);
            }
        }
    }

    private void computeMeanDevStdSkew(double[] data, int key, Configuration.axis ax) throws IOException{
        for (int i = 0;
             i < Configuration.FRAGMENT_LENGTH * Configuration.SAMPLING_RATE && i < data.length;
             i += (Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE)) {
            double mean = mn.evaluate(data, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE);
            featureFileWriter.append(mean + ",");
            featureFileWriter.append(stDv.evaluate(data, mean, i, Configuration.WINDOW_SIZE * Configuration.SAMPLING_RATE) + ",");
            if(key == 1 && ax==Configuration.axis.Z && i==0)
                computeKurtosis(data, i);
            computeSkewness(data, i);
        }
    }

    public void computeFeature(double[] data, int key, Configuration.axis ax) throws IOException{
        switch(key){
            case 0:
                if(ax == Configuration.axis.Y)
                    computeMeanDevStd(data, key, ax);
                else
                    computeMeanDevStdKurt(data, key, ax);
                break;
            case 1:
                if(ax == Configuration.axis.X)
                    computeGyrXStat(data);
                else
                    computeMeanDevStdSkew(data, key, ax);
                break;
            case 2:
                if(ax == Configuration.axis.Y)
                    computeMeanDevStdKurt(data, key, ax);
                else
                    computeMeanDevStd(data, key, ax);
                break;
            case 3:
                if(ax == Configuration.axis.X)
                    computeMeanDevStd(data, key, ax);
                else
                    computeMeanDevStdSkew(data, key, ax);
                break;
            case 4:
                if(ax == Configuration.axis.PITCH)
                    computeMeanDevStdSkew(data, key, ax);
                else
                    computeRotRollStat(data);
                break;
            default:
                break;
        }
        if(key == 4 && ax==Configuration.axis.ROLL) {
            featureFileWriter.append("?\n");
        }
    }

    public void closeFiles(){
        try {
            if(status) {
                featureFileWriter.close();
            }
        } catch (IOException e) {
            Log.d(TAG, "Errore nel chiudere 8");
            e.printStackTrace();
        }
    }
}