package com.android.srt;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.MotionEvent;

public class SplashScreen extends Activity {

    // how much time until next activity
    protected int _waitTime = 5000; 

    private Thread thread;

    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_splashscreen);

        final SplashScreen sPlashScreen = this; 

        // thread to show the Welcome Screen
        thread = new Thread() {
            @Override
            public void run() {
                try {
                    synchronized(this){

                            //wait 5 sec
                            wait(_waitTime);
                    }

                } catch(InterruptedException e) {}
                finally {
                    finish();

                    // starts new activity
                    Intent i = new Intent();
                    i.setClass(sPlashScreen, MainActivity.class);
                            startActivity(i);
                                          	
                }
            }
        };

        thread.start();
    }

    // Manages the screen touch event
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (event.getAction() == MotionEvent.ACTION_DOWN) {
            synchronized(thread){
                    thread.notifyAll();
            }
        }
        return true;
    }

}