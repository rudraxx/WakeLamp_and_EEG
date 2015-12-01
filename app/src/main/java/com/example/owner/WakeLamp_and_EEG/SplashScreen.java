package com.example.owner.WakeLamp_and_EEG;

import android.content.Intent;
import android.os.Bundle;
import android.support.v7.app.ActionBarActivity;


public class SplashScreen extends ActionBarActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.initialscreen);
        Thread timer = new Thread()
        {
          public void run()
          {
              try{
                  sleep(3000); // Sleep for 3 seconds. Show the splash screen for 3 seconds.

              } catch (InterruptedException e) {
                  e.printStackTrace();

              } finally{
                  Intent openWakeLampActivity = new Intent("android.intent.action.WakeLamp_and_EEG");
                  startActivity(openWakeLampActivity);

              }
          }
        };
        timer.start();
    }

    @Override
    protected void onPause() {
        super.onPause();
        finish();

    }
}
