package com.android.srt;

import java.util.ArrayList;
import java.util.Calendar;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;


import android.os.Bundle;
import android.preference.PreferenceManager;
import android.provider.Settings.Secure;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.widget.ArrayAdapter;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import com.android.srt.database.SensorsDataSource;

public class MainActivity extends ListActivity {

	/************************************************************************/
    /*    VARIABLES							                                */   
    /************************************************************************/
	
	private String 									mDeviceID;	
	private String 									Host;	
	private String 									Topic;
	private String									Environment;
	
	private AlertDialog			 					infoDialog = null;
	
	public static SensorsDataSource 				datasource;
	
	public static boolean							isConnected;
	public static boolean							activityPaused;
	// Preferences instance 
	private SharedPreferences 						mPrefs;
	
	
	/************************************************************************/
    /*    METHODS - lifecycle				                                */   
    /************************************************************************/
	
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Open DB with Application context
        datasource = new SensorsDataSource(getApplicationContext());
        datasource.open();        
    }
    
    @Override
    protected void onResume() {  
        // Register to receive messages.
        // We are registering an observer (mMessageReceiver) to receive Intents
        // with actions named "event-connection", "event-publisharrived", "event-finishactivity".
        LocalBroadcastManager.getInstance(this).registerReceiver(rConnectionReceiver,
            new IntentFilter("event-connection"));
        LocalBroadcastManager.getInstance(this).registerReceiver(rPublishReceiver,
            new IntentFilter("event-publisharrived"));
        
    	// Make fresh start of service if necessary
    	PushService.actionStart(getApplicationContext());
    	
    	// Initialize data
        initializeValues();
        
        super.onResume();
    }
	 
    @Override
    protected void onPause() {    	
    	activityPaused = true;
    	// avoid crash if dialog was open
    	if(infoDialog != null){
    		infoDialog.dismiss();
    	}
    	
    	LocalBroadcastManager.getInstance(this).unregisterReceiver(rConnectionReceiver);
    	LocalBroadcastManager.getInstance(this).unregisterReceiver(rPublishReceiver);
    	
    	super.onPause();
    }
    
    @Override
    protected void onDestroy() {
    	datasource.close();
    	// Change action to stop the service 
   	 	PushService.actionStop(getApplicationContext());
    	super.onDestroy();
    }

	/************************************************************************/
    /*    METHODS - functionality			                                */   
    /************************************************************************/
    	    
    	private void initializeValues(){
            isConnected = PushService.mConnected;
    		activityPaused = false;
    		
            mDeviceID = Secure.getString(this.getContentResolver(), Secure.ANDROID_ID);
            
            //Initialize prefs to default
            PreferenceManager.setDefaultValues(this, R.layout.activity_preferences, false);
    		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
            
            // check if there are already preferences
            if(mPrefs.contains("Server")){
            	Host = mPrefs.getString("Server","");
            }else{
            	Host = "No server selected";
            }
            if(mPrefs.contains("Topic")){
            	Topic = mPrefs.getString("Topic","");
            }else{
            	Topic = "No topic selected";
            }
            if(mPrefs.contains("Environment")){
            	Environment = mPrefs.getString("Environment","");
            }else{
            	Environment = "No environment selected";
            }
            
            SharedPreferences.Editor editor = mPrefs.edit();
            editor.putString(PushService.PREF_DEVICE_ID, mDeviceID);
            editor.commit(); 	
        	
            // Update icon and list
        	updateConnectionIcon(isConnected);
        	updateList();
            
            // Set info icon clickable
    		ImageView infoImage = (ImageView)findViewById(R.id.ImageView_info);	
    		infoImage.setOnClickListener(new OnClickListener() {
    			@Override
    			public void onClick(View v) {
    				AlertDialog.Builder infoDialogBuilder = new AlertDialog.Builder(MainActivity.this);
    				infoDialogBuilder.setTitle("Net Info");
    				infoDialogBuilder.setMessage("Server IP: " + Host + "\n" +
    						"Port number: " + PushService.MQTT_BROKER_PORT_NUM + "\n" +
    						"Topic: " + Topic + "\n" +
							"Is connected: " + String.valueOf(isConnected));
    				infoDialogBuilder.setIcon(R.drawable.mobile_net);
    				infoDialogBuilder.setNeutralButton("Close", null);
    				
    				infoDialog = infoDialogBuilder.create();
    				infoDialog.show();
    			}
    		});
    	}
    	
	   	 private void updateConnectionIcon(boolean success){	 	
			if(success){
				TextView connected = (TextView) findViewById(R.id.TextView_connected);
				connected.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.circle_green, 0);
			}else{
				TextView connected = (TextView) findViewById(R.id.TextView_connected);
				connected.setCompoundDrawablesWithIntrinsicBounds(0, 0, R.drawable.circle_red, 0);
			}
		 }
    	
    	private void updateList(){
    		ArrayList<Sensor> sensors = new ArrayList<Sensor>();
    		// Filter by environment   	 
    		if(mPrefs.contains("Environment")){
            	sensors = datasource.getAllSensors(Environment);
            }else{
            	sensors = datasource.getAllSensors();
            }
    		
		    
            // Check if results are empty
            if(sensors.isEmpty()){
            	TextView noElements = (TextView) findViewById(R.id.TextView_noElements);
            	if(noElements != null){
            		noElements.setText(R.string.no_elements);
            	}
            	// disable list view
            	getListView().setVisibility(View.INVISIBLE);
            }else{
            	// Use the SensorItemAdapter to show the
                // elements in a ListView
            	getListView().setVisibility(View.VISIBLE);
                ArrayAdapter<Sensor> adapter = new SensorItemAdapter(this,
                	R.layout.list_sensor, sensors);
                ListView list = (ListView) findViewById(android.R.id.list);
                list.setAdapter(adapter);
            	// disable no elements
                TextView noElements = (TextView) findViewById(R.id.TextView_noElements);
                
                // Show environment name
            	if(noElements != null){
            		noElements.setText("Environment: " + Environment);
            	}
                
            }
    	}
    
    	// Determines if the sensor was activated in some time intervals
    	private int[] checkActivations(int dots, int interval, ArrayList<Sensor> sensors, Calendar lastDate){
        	int[] activationArray = new int[dots];
        	long bottomTime = lastDate.getTime().getTime();
        	long topDate, activationDate = 0;
        	
    		lastDate.add(Calendar.MINUTE, interval);
    		topDate = lastDate.getTime().getTime();
    		
        	// check if there are activations for the sensor
        	if (!sensors.isEmpty()){
        		for (int i = 0; i < dots; i++) {
    				// For each dot check their interval activation
    	    		// e.g. (60*5) 300 min -> 300-240 | 240-180 | 180-120 | 120-60 | 60-now
    	    		if(i>0){
						// update references for next iteration
						bottomTime = topDate;
		    			lastDate.add(Calendar.MINUTE, interval);
			    		topDate = lastDate.getTime().getTime();
			    		// check if we are at last interval, to not lose recent activations
			    		// to do that, increment threshold by 1 min
			    		if((i+1) == dots){
			    			lastDate.add(Calendar.MINUTE, 1);
				    		topDate = lastDate.getTime().getTime();	    				
			    		}
    	    		}
        			
    	    		// search
    	    		for (int j = 0; j < sensors.size(); j++) {
    	    			activationDate = sensors.get(j).getDateLong();
    	    			// Check if there is an activation inside de interval
    					if(activationDate >= bottomTime && activationDate < topDate){
    						// there is an activation
    						activationArray[i] = 1;

    						break;
    					}
    				}
    			}
        	}
        	return activationArray;
    	}
	/************************************************************************/
	/*    METHODS - handler					                                */   
    /************************************************************************/

	 // Our handler for received Intents. This will be called whenever an Intent
	 // with an action named "event-connection" is broadcasted.
	 private BroadcastReceiver rConnectionReceiver = new BroadcastReceiver() {
	   @Override
	   public void onReceive(Context context, Intent intent) {
	     // Get extra data included in the Intent
	     isConnected = intent.getBooleanExtra("com.android.srt.IsConnected", false);
	     // Update connection icon
	     updateConnectionIcon(isConnected);
	     
	     Log.i(PushService.TAG, "Got change of connection: " + isConnected);
	   }
	 };
	 
	 // Our handler for received Intents. This will be called whenever an Intent
	 // with an action named "event-publisharrived" is broadcasted.
	 private BroadcastReceiver rPublishReceiver = new BroadcastReceiver() {
	   @Override
	   public void onReceive(Context context, Intent intent) {
	     // Update sensor list
		   updateList();
	     
	     Log.i(PushService.TAG, "Got change of list");
	   }
	 };
	 
	 @Override
	 protected void onListItemClick(ListView l, View v, int position, long id) {
		 // process click on item #position
		 Sensor sensor = (Sensor) l.getItemAtPosition(position);
		 String hardwareID = sensor.getId();
		 
		 int interval, dots;
         if(mPrefs.contains("GraphInterval")){
         	interval = Integer.parseInt(mPrefs.getString("GraphInterval",""));
         }else{
         	interval = -1;
         }
         if(mPrefs.contains("GraphResolution")){
         	dots = Integer.parseInt(mPrefs.getString("GraphResolution",""));
         }else{
         	dots = -1;
         }
         
         if(interval == -1 || dots == -1){
         	return;
         }else{
		    	int lastMinutes = interval * dots;

		    	Calendar lastDate = Calendar.getInstance();
		    	lastDate.add(Calendar.MINUTE, -lastMinutes);
		    	
		    	long bottomTime = lastDate.getTime().getTime();
		    	
		    	SensorsDataSource datasource = MainActivity.datasource;
		    	// retrieve all the activations for the sensor in the lastMinutes
		    	ArrayList<Sensor> sensors = datasource.getAllSensors(hardwareID, bottomTime);
		    	// activation array
		    	int[] activationArray = checkActivations(dots, interval, sensors, lastDate);
		    	
		    	// call the graph and pass values
		    	SensorGraph line = new SensorGraph(activationArray, hardwareID, dots);
		    	Intent lineIntent = line.getIntent(this);

		    	startActivity(lineIntent);
		        
         }
		 
    }
	/************************************************************************/
	/*    METHODS - menu purpose			                                */   
    /************************************************************************/
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
    	
    	getMenuInflater().inflate(R.menu.menu, menu);
      	     
        return true;
    }
    
	 @Override
	 public boolean onOptionsItemSelected(MenuItem item) {
	     // Handle item selection
	     switch (item.getItemId()) {
		     case R.id.exit:
		    	 // Change action to stop the service 
		    	 PushService.actionStop(getApplicationContext());
		    	 finish();  
		         return true;
		     case R.id.preferences:
		    	  startActivity(new Intent(MainActivity.this, PreferencesActivity.class));

		    	 break;
			default:
		         return super.onOptionsItemSelected(item);
	     }
		return false;
	 }
}
