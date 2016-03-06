package com.android.srt;


import java.io.IOException;
import java.util.Calendar;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.AsyncTask;
import android.os.IBinder;
import android.preference.PreferenceManager;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;
import android.widget.Toast;

import com.android.srt.database.SensorsDataSource;
import com.ibm.mqtt.IMqttClient;
import com.ibm.mqtt.MqttClient;
import com.ibm.mqtt.MqttException;
import com.ibm.mqtt.MqttPersistence;
import com.ibm.mqtt.MqttPersistenceException;
import com.ibm.mqtt.MqttSimpleCallback;


public class PushService extends Service
{
	/************************************************************************/
    /*    VARIABLES							                                */   
    /************************************************************************/
	
	// this is the log tag
	public static final String		TAG = "SRTLog";
	// the port at which the broker is running. 
	public static int				MQTT_BROKER_PORT_NUM      = 1883;
	// the MQTT persistence.
	private static MqttPersistence	MQTT_PERSISTENCE          = null;
	//don't need to remember any state between the connections, so we use a clean start. 
	private static boolean			MQTT_CLEAN_START          = true;
	// Let's set the internal keep alive for MQTT to 15 mins.
	private static short			MQTT_KEEP_ALIVE           = 60 * 15;
	// Set quality of services to 0 (at most once delivery), since we don't want push notifications 
	// arrive more than once. However, this means that some messages might get lost (delivery is not guaranteed)
	private static int[]			MQTT_QUALITIES_OF_SERVICE = { 0 } ;
	private static int				MQTT_QUALITY_OF_SERVICE   = 0;
	// The broker should not retain any messages.
	private static boolean			MQTT_RETAINED_PUBLISH     = false;
		
	// MQTT client ID, which is given the broker. In this example, I also use this for the topic header. 
	// You can use this to run push notifications for multiple apps with one MQTT broker. 
	public static String			MQTT_CLIENT_ID = "SRT";
	// These are the actions for the service (name are descriptive enough)
	private static final String		ACTION_START = MQTT_CLIENT_ID + ".START";
	private static final String		ACTION_STOP = MQTT_CLIENT_ID + ".STOP";
	private static final String		ACTION_KEEPALIVE = MQTT_CLIENT_ID + ".KEEP_ALIVE";
	private static final String		ACTION_RECONNECT = MQTT_CLIENT_ID + ".RECONNECT";
	
	// Connection log for the push service. Good for debugging.
	private ConnectionLog 			mLog;
	
	// Connectivity manager to determining, when the phone loses connection
	private ConnectivityManager		mConnMan;
	// Notification manager to displaying arrived push notifications 
	private NotificationManager		mNotifMan;

	// Whether or not the service has been started.	
	public static boolean 			mStarted;
	public static boolean			mConnected = false;
	
	// This the application level keep-alive interval, that is used by the AlarmManager
	// to keep the connection active, even when the device goes to sleep.
	private static final long		KEEP_ALIVE_INTERVAL = 1000 * 60 * 28;

	// Retry intervals, when the connection is lost.
	private static final long		INITIAL_RETRY_INTERVAL = 1000 * 10;
	private static final long		MAXIMUM_RETRY_INTERVAL = 1000 * 60 * 30;

	// Preferences instance 
	private SharedPreferences 		mPrefs;
	// We store in the preferences, whether or not the service has been started
	public static final String		PREF_STARTED = "isStarted";
	// We also store the deviceID (target)
	public static final String		PREF_DEVICE_ID = "deviceID";
	
	// We store the last retry interval
	public static final String		PREF_RETRY = "retryInterval";

	// Notification title
	public static String			NOTIF_TITLE = "Sensor Real Time"; 	
	// Notification id
	private static final int		NOTIF_ARRIVED = 0;
		
	// This is the instance of an MQTT connection.
	private MQTTConnection			mConnection;
	private long					mStartTime;
	private boolean 				asynctaskInUse;
	private ConnectionTask 			task; //AsyncTask
	
	// Toast messages
//	private static final int TOAST_DURATION = Toast.LENGTH_LONG;

	
	/************************************************************************/
    /*    METHODS							                                */   
    /************************************************************************/

	// Static method to start the service
	public static void actionStart(Context ctx) {
		Intent i = new Intent(ctx, PushService.class);
		i.setAction(ACTION_START);
		ctx.startService(i);
	}
	

	// Static method to stop the service
	public static void actionStop(Context ctx) {
		Intent i = new Intent(ctx, PushService.class);
		i.setAction(ACTION_STOP);
		ctx.startService(i);
	}
	
	// Static method to send a keep alive message
	public static void actionPing(Context ctx) {
		Intent i = new Intent(ctx, PushService.class);
		i.setAction(ACTION_KEEPALIVE);
		ctx.startService(i);
	}

	@Override
	public void onCreate() {
		super.onCreate();
		
		log("Creating service");
		mStartTime = System.currentTimeMillis();

		// Initialize log for debug
		try {
			mLog = new ConnectionLog();
			Log.i(TAG, "Opened log at " + mLog.getPath());
		} catch (IOException e) {
			Log.e(TAG, "Failed to open log", e);
		}

		// Get instances of preferences, connectivity manager and notification manager
		mPrefs = PreferenceManager.getDefaultSharedPreferences(this);
		mConnMan = (ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);
		mNotifMan = (NotificationManager)getSystemService(NOTIFICATION_SERVICE);
	
		/* If our process was reaped by the system for any reason we need
		 * to restore our state with merely a call to onCreate.  We record
		 * the last "started" value and restore it here if necessary. */
		handleCrashedService();
	}
	
	// This method does any necessary clean-up need in case the server has been destroyed by the system
	// and then restarted
	private void handleCrashedService() {
		if (wasStarted() == true) {
			log("Handling crashed service...");
			 // stop the keep alives
			stopKeepAlives(); 
				
			// Do a clean start
			start();
		}
	}
	
	@Override
	public void onDestroy() {
		log("Service destroyed (started=" + mStarted + ")");
		
		// Stop the services, if it has been started
		if (mStarted == true) {
			stop();
		}
		
		// Close log
		try {
			if (mLog != null)
				mLog.close();
		} catch (IOException e) {}		
	}
	
	
	@Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        super.onStartCommand(intent, startId, startId);
        log("Service started with id and intent = " + startId + ": " + intent);

        if(intent != null){
			// Do an appropriate action based on the intent.
			if (intent.getAction().equals(ACTION_STOP) == true) {
				stop();
				// stop service
				stopSelf();
			} else if (intent.getAction().equals(ACTION_START) == true) {
				start();
			} else if (intent.getAction().equals(ACTION_KEEPALIVE) == true) {
				keepAlive();
			} else if (intent.getAction().equals(ACTION_RECONNECT) == true) {
				if (isNetworkAvailable()) {
					reconnectIfNecessary();
				}
			}
        }
        return START_STICKY;
    }
		
	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	// log helper function
	private void log(String message) {
		log(message, null);
	}
	private void log(String message, Throwable e) {
		if (e != null) {
			Log.e(TAG, message, e);
			
		} else {
			Log.i(TAG, message);			
		}
		
		if (mLog != null)
		{
			try {
				mLog.writelog(message);
			} catch (IOException ex) {}
		}		
	}
	
	// Reads the Host from SharedPreferences
	private String readServer(){
		String Host = "";
		//Read Server from preferences
		if(mPrefs.contains("Server")){
			Host =  mPrefs.getString("Server", null);
		}
				
		return Host;
	}
	
	// Reads the Topic from SharedPreferences
	private String readTopic(){
		String Topic = "";
		//Read Topic from preferences
		if(mPrefs.contains("Topic")){
			Topic =  mPrefs.getString("Topic", null);
		}
				
		return Topic;
	}
	
	// Make a popup message with the desired String
	private void showToastMessage(String message){
		Context context = getApplicationContext();
        
        Toast toast = Toast.makeText(context, message, Toast.LENGTH_LONG);
        toast.show();
	}
	
	// Reads whether or not the service has been started from the preferences
	private boolean wasStarted() {
		return mPrefs.getBoolean(PREF_STARTED, false);
	}

	// Sets whether or not the services has been started in the preferences.
	private void setStarted(boolean started) {
		mPrefs.edit().putBoolean(PREF_STARTED, started).commit();		
		mStarted = started;
	}
	
	// Sets whether or not the connection was successful in the preferences.
	private void setConnected(boolean connected) {
		mConnected = connected;
	
		//check if its the same status
		// if its the same, do not update
		if(MainActivity.isConnected != connected){

		// show message if false
		//if(!connected){
		//	String Host =  readServer();
	        
			// make popup message
		//	showToastMessage("Disconnect from server: " + Host);
		//}
		
			log(Thread.currentThread().getName()+ " - Broadcasting connection status");
		 	Intent intent = new Intent("event-connection");
			// Includes the boolean-status as extra data
			intent.putExtra("com.android.srt.IsConnected", connected);
			LocalBroadcastManager.getInstance(this).sendBroadcast(intent);
		}
	}

	private synchronized void start() {
		log("Starting service...");
		
		// Do nothing, if the service is already running.
		if (mStarted == true) {
			Log.w(TAG, "Attempt to start service that is already active");
			return;
		}
		
		// Establish an MQTT connection if there is no one trying to do it
		if(!asynctaskInUse){
			connect();
		}
		// Register a connectivity listener based on a change in NET connectivity
		registerReceiver(mConnectivityChanged, new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION));		
	}

	private synchronized void stop() {
		// Do nothing, if the service is not running.
		if (mStarted == false) {
			Log.w(TAG, "Attempt to stop connection not active.");
			return;
		}

		// Save stopped state in the preferences
		setStarted(false);
		// Save connection state in the preferences
		setConnected(false);

		// if the service stops, then cancel all possible threads that are running
		// if true, onPostExecute wont execute
		boolean cancelledPossible = task.cancel(true);
		log(Thread.currentThread().getName()+"- Cancel AsyncTask was possible: " + cancelledPossible);
		
		// Remove the connectivity receiver
		unregisterReceiver(mConnectivityChanged);
		// Any existing reconnect timers should be removed, since we explicitly stopping the service.
		cancelReconnect();

		// Destroy the MQTT connection if there is one
		if (mConnection != null) {
			mConnection.disconnect();
			mConnection = null;
		}
	}
	
	private synchronized void connect() {		
		log("Connecting...by: " + Thread.currentThread().getName());
		// fetch the device ID from the preferences.
		String deviceID = mPrefs.getString(PREF_DEVICE_ID, null);
		
		// Create a new connection only if the device id is not NULL
		if (deviceID == null) {
			log("Device ID not found.");
		} else {
			setStarted(true);
			
			//Read Server from preferences
			String Host =  readServer();
			//Make connection task
			log("Begin of AsyncTask in use by thread: " + Thread.currentThread().getName());
			asynctaskInUse = true;
			task = new ConnectionTask();
	        task.execute(Host, deviceID);		
									
		}
	}
	
	private synchronized void keepAlive() {
		try {
			// Send a keep alive, if there is a connection.
			if (mStarted == true && mConnection != null) {
				mConnection.sendKeepAlive();
			}
		} catch (MqttException e) {
			log("KeepAlive MqttException: " + (e.getMessage() != null? e.getMessage(): "NULL"), e);
			
			mConnection.disconnect();
			mConnection = null;
			cancelReconnect();
		}
	}

	// Schedule application level keep-alives using the AlarmManager
	private void startKeepAlives() {
		Intent i = new Intent();
		i.setClass(this, PushService.class);
		i.setAction(ACTION_KEEPALIVE);
		PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
		AlarmManager alarmMgr = (AlarmManager)getSystemService(ALARM_SERVICE);
		alarmMgr.setRepeating(AlarmManager.RTC_WAKEUP,
		  System.currentTimeMillis() + KEEP_ALIVE_INTERVAL,
		  KEEP_ALIVE_INTERVAL, pi);
	}

	// Remove all scheduled keep alives
	private void stopKeepAlives() {
		Intent i = new Intent();
		i.setClass(this, PushService.class);
		i.setAction(ACTION_KEEPALIVE);
		PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
		AlarmManager alarmMgr = (AlarmManager)getSystemService(ALARM_SERVICE);
		alarmMgr.cancel(pi);
	}

	// We schedule a reconnect based on the starttime of the service
	public void scheduleReconnect(long startTime) {
		// the last keep-alive interval
		long interval = mPrefs.getLong(PREF_RETRY, INITIAL_RETRY_INTERVAL);

		// Calculate the elapsed time since the start
		long now = System.currentTimeMillis();
		long elapsed = now - startTime;


		// Set an appropriate interval based on the elapsed time since start 
		if (elapsed < interval) {
			interval = Math.min(interval * 4, MAXIMUM_RETRY_INTERVAL);
		} else {
			interval = INITIAL_RETRY_INTERVAL;
		}
		
		log("Rescheduling connection in " + interval + "ms.");

		// Save the new internval
		mPrefs.edit().putLong(PREF_RETRY, interval).commit();

		// Schedule a reconnect using the alarm manager.
		Intent i = new Intent();
		i.setClass(this, PushService.class);
		i.setAction(ACTION_RECONNECT);
		PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
		AlarmManager alarmMgr = (AlarmManager)getSystemService(ALARM_SERVICE);
		alarmMgr.set(AlarmManager.RTC_WAKEUP, now + interval, pi);
	}
	
	// Remove the scheduled reconnect
	public void cancelReconnect() {
		Intent i = new Intent();
		i.setClass(this, PushService.class);
		i.setAction(ACTION_RECONNECT);
		PendingIntent pi = PendingIntent.getService(this, 0, i, 0);
		AlarmManager alarmMgr = (AlarmManager)getSystemService(ALARM_SERVICE);
		alarmMgr.cancel(pi);
	}
	
	private synchronized void reconnectIfNecessary() {		
		if (mStarted == true && mConnection == null) {
			log("Reconnecting...(AsyncTask in use: " + asynctaskInUse + ")");
			if(!asynctaskInUse && Thread.currentThread().getName().equals("main")){
				connect();
			}
		}
	}

	// This receiver listeners for network changes and updates the MQTT connection
	// accordingly
	private BroadcastReceiver mConnectivityChanged = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			// Get network info
			NetworkInfo info = (NetworkInfo)intent.getParcelableExtra (ConnectivityManager.EXTRA_NETWORK_INFO);
			
			// Is there connectivity?
			boolean hasConnectivity = (info != null && info.isConnected()) ? true : false;

			log(Thread.currentThread().getName()+  " - Connectivity changed: " + hasConnectivity);

			if (hasConnectivity) {
				reconnectIfNecessary();
			} else{ 
				
				setConnected(false);
				
				if (mConnection != null) {
					// if there no connectivity, make sure MQTT connection is destroyed
					mConnection.disconnect();
					cancelReconnect();
					mConnection = null;
				}
			}
		}
	};
	
	// Display the topbar notification
	private void showNotificationAtBar(String text) {
		Notification n = new Notification();
				
		n.flags |= Notification.FLAG_SHOW_LIGHTS;
      	n.flags |= Notification.FLAG_AUTO_CANCEL;

        n.defaults = Notification.DEFAULT_ALL;
      	
		n.icon = com.android.srt.R.drawable.ic_launcher;
		n.when = System.currentTimeMillis();

		// Simply open the parent activity
		Intent intent = new Intent(this, MainActivity.class);
		intent.setFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP | Intent.FLAG_ACTIVITY_SINGLE_TOP);

		PendingIntent pi = PendingIntent.getActivity(this, 0,
		  intent, 0);
		
		// Change the name of the notification here
		n.setLatestEventInfo(this, NOTIF_TITLE, text, pi);

		mNotifMan.notify(NOTIF_ARRIVED, n);
	}
	
	// Send a sound notification
	private void showNotification() {
		Notification n = new Notification();		
        n.defaults = Notification.DEFAULT_ALL;
		mNotifMan.notify(NOTIF_ARRIVED, n);
	}
	
	// Check if we are online
	private boolean isNetworkAvailable() {
		NetworkInfo info = mConnMan.getActiveNetworkInfo();
		if (info == null) {
			return false;
		}
		return info.isConnected();
	}
	
	// Makes the connection  | AsyncTask<Parameters, Progress, Result>
	private class ConnectionTask extends AsyncTask<String, Void, Boolean>{
		
		private String brokerHostName;
		private String initTopic;
		
		// Calls the connection
		@Override
	    protected Boolean doInBackground(String... values) {
			Boolean response = Boolean.valueOf(false);
	     
	     	// check values length
			int count = values.length;
			if(count == 2){ //host + deviceId
				
				brokerHostName = values[0];
				initTopic = values[1];
				 
				try {
					mConnection = new MQTTConnection(brokerHostName, initTopic);

		        	response = Boolean.valueOf(true);
							        	
				} catch (MqttException e) {
					// Schedule a reconnect, if we failed to connect
					log("doInBackground MqttException: " + (e.getMessage() != null ? e.getMessage() : "NULL"));
		        	if (isNetworkAvailable() && !mConnected) {
		        		scheduleReconnect(mStartTime);
		        	}
				}
			}
			asynctaskInUse = false;
			log("End of AsyncTask in use: " + Thread.currentThread().getName());
			return response;
	    }
		
		// Save result of doInBackground
	    @Override
	    protected void onPostExecute(Boolean result) {
	    	boolean success = result.booleanValue();
	    	setConnected(success);
	    	
	    	if(success){
				// Show message
		        showToastMessage("Connect to server: "+ brokerHostName);   
	    	}
	    }
	}
	
	// This inner class is a wrapper on top of MQTT client.
	private class MQTTConnection implements MqttSimpleCallback  {
		IMqttClient mqttClient = null;
		
		// Creates a new connection given the broker address and initial topic
		public MQTTConnection(String brokerHostName, String initTopic) throws MqttException {
			// Create connection spec
			String mqttConnSpec = "tcp://" + brokerHostName + "@" + MQTT_BROKER_PORT_NUM;
			// Create the client and connect
			log("Create the client and connect...");
			
			mqttClient = MqttClient.createMqttClient(mqttConnSpec, MQTT_PERSISTENCE);
			
			String clientID = MQTT_CLIENT_ID + "/" + mPrefs.getString(PREF_DEVICE_ID, "");
			log("connect..." +mqttConnSpec+ clientID + MQTT_CLEAN_START + MQTT_KEEP_ALIVE);
			mqttClient.connect(clientID, MQTT_CLEAN_START, MQTT_KEEP_ALIVE);
			log("register...");
			// register this client app has being able to receive messages
			mqttClient.registerSimpleHandler(this);
			
			// Subscribe to an initial topic, which is combination of client ID and device ID.
			initTopic = MQTT_CLIENT_ID + "/+" ;
			
			log("suscribe...");
			subscribeToTopic(initTopic);
			
			// Search for topic and subscribe to it
			String Topic =  readTopic();
			
			subscribeToTopic(Topic);

			// make popup message
			//showToastMessage("Subscription to Topic: "+ Topic);
			
			log("Connection established to " + brokerHostName + " on topic " + initTopic);
			log("Subscription to topic " + Topic);
			// Save start time
			mStartTime = System.currentTimeMillis();
			// Star the keep-alives
			startKeepAlives();	
		}
	    
		// Disconnect
		public void disconnect() {
			try {		
				// Save connection state in the preferences
				setConnected(false);
				
				stopKeepAlives();
				mqttClient.disconnect();		        
				
			} catch (MqttPersistenceException e) {
				log("MQTTConnection MqttException" + (e.getMessage() != null? e.getMessage():" NULL"), e);
			}
		}
		/*
		 * Send a request to the message broker to be sent messages published with 
		 *  the specified topic name. Wildcards are allowed.	
		 */
		private void subscribeToTopic(String topicName) throws MqttException {
			
			if ((mqttClient == null) || (mqttClient.isConnected() == false)) {
				// quick sanity check - don't try and subscribe if we don't have
				//  a connection
				log("Connection error " + "No connection");	
			} else {									
				String[] topics = { topicName };
				mqttClient.subscribe(topics, MQTT_QUALITIES_OF_SERVICE);
			}
		}	
		/*
		 * Sends a message to the message broker, requesting that it be published
		 *  to the specified topic.
		 */
		private void publishToTopic(String topicName, String message) throws MqttException {		
			if ((mqttClient == null) || (mqttClient.isConnected() == false)) {
				// quick sanity check - don't try and publish if we don't have
				//  a connection				
				log("No connection to public to");		
			} else {
				mqttClient.publish(topicName, 
								   message.getBytes(),
								   MQTT_QUALITY_OF_SERVICE, 
								   MQTT_RETAINED_PUBLISH);
			}
		}		
		
		/*
		 * Called if the application loses it's connection to the message broker.
		 */
		@Override
		public void connectionLost() throws Exception {
			
			log("Loss of connection " + "connection downed");
			
			stopKeepAlives();
			
			// null itself
			mConnection = null;
						
			if (isNetworkAvailable() == true) {
				reconnectIfNecessary();	
			}
		}		
		
		/*
		 * Called when we receive a message from the message broker. 
		 */
		@Override
		public void publishArrived(String topicName, byte[] payload, int qos, boolean retained) {

			// Convert to string
			String receivedMessage = new String(payload);
			
			// Create sensor object
			Sensor sensor = new Sensor(receivedMessage);
			
			// Check if receivedMessage was correct
			if(!sensor.getId().equals("")){
				boolean insertSuccess;
				
				SensorsDataSource datasource = MainActivity.datasource;
			  			    
			    // Insert Sensor if not exist and activation into db
			    if(!datasource.existSensor(sensor)){
			    	datasource.createSensor(sensor);
			    }
				insertSuccess = datasource.createActivation(sensor);
				
				if(insertSuccess){
					// Update main UI
					log("Notify user interface with activation to change list");
				 	Intent intent = new Intent("event-publisharrived");
					LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(intent);
					
					String environment;
					if(mPrefs.contains("Environment")){
		            	environment = mPrefs.getString("Environment","");
					}else{
						environment = "";
					}
					
					if(sensor.getEnvironment().equals(environment)){
			    		// Check if by preferences we are able to show Notifications   	 
					    if (mPrefs.getBoolean("Notifications", true))
					    {
					    	// if it is on paused show message at bar
					    	if(MainActivity.activityPaused){			    		
					    		showNotificationAtBar(receivedMessage);
					    	}else{
					    		// if we are on the activity, there is no reason to show
					    		// notification at status bar
					            showNotification();
					        }
	            		}else{
					    	// notifications disabled, but...
					    	// if we are at the activity, will be nice to get sound
					    	if(!MainActivity.activityPaused){
					    		showNotification();
					    	}
					    }
					}
					// Make log
					// Get date
					Calendar calendar = Calendar.getInstance(); 
					String dateAfterDBOperation = calendar.get(Calendar.HOUR_OF_DAY) + ":" + calendar.get(Calendar.MINUTE) + ":" + calendar.get(Calendar.SECOND) + ":" + calendar.get(Calendar.MILLISECOND) + "->" + receivedMessage; 
					
					if (mLog != null)
					{
						try {
							mLog.writelog(dateAfterDBOperation);
						} catch (IOException ex) {}
					}		
				}
			}
			
		}
	
		public void sendKeepAlive() throws MqttException {
			log("Sending keep alive");
			// publish to a keep-alive topic
			publishToTopic(MQTT_CLIENT_ID + "/keepalive", mPrefs.getString(PREF_DEVICE_ID, ""));			
		}	
		
	}
}