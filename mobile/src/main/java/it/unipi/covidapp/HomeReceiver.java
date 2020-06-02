/*
Broadcast receiver that filters the intent addressed to our module when the user is at home and
starts hands activity detection.
Filters also intent containing results when an activity is recognized by the classifier.
 */


package it.unipi.covidapp;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class HomeReceiver extends BroadcastReceiver {
    private String action;

    @Override
    public void onReceive(Context context, Intent intent) {
        action = intent.getAction();
        System.out.println("Action received: " + action);
        if(intent.getAction() != null && action.compareTo("hand_activity_detection") == 0) {
            System.out.println("Activity detected: "+intent.getIntExtra("wash_hand", -1));
        }
        else if(intent.getAction() != null && action.compareTo("UserInHome") == 0){
            Intent startService = new Intent(context, HandActivityService.class);
            startService.setAction("Start_HandActivityService");
            startService.putExtra("Command", Configuration.START);
            context.startService(startService);
        }
    }

}
