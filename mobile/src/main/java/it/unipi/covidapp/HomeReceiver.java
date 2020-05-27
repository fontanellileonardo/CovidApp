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
        Intent startService = new Intent(context, HandActivityService.class);
        context.startService(startService);
    }

}
