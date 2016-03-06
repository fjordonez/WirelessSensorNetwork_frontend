package com.android.srt;

import android.content.SharedPreferences;
import android.content.SharedPreferences.OnSharedPreferenceChangeListener;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.preference.PreferenceManager;

public class PreferencesActivity extends PreferenceActivity implements SharedPreferences.OnSharedPreferenceChangeListener{

	OnSharedPreferenceChangeListener 		listener;
	SharedPreferences						prefs;
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		
		super.onCreate(savedInstanceState);
		addPreferencesFromResource(R.layout.activity_preferences);
		
		prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());
		prefs.registerOnSharedPreferenceChangeListener(this);
	}
	
    @Override
    public void onSharedPreferenceChanged(SharedPreferences sharedPreferences, String key) {
        // check if the server or topic was changed to stop the service
    	// do so to be able to refresh the connection
    	if(key.equals("Server") || key.equals("Topic")){
    		PushService.actionStop(getApplicationContext());
    	}
    	
    }
}