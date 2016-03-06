package com.android.srt.database;

import java.util.ArrayList;
import java.util.Calendar;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.SQLException;
import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.android.srt.PushService;
import com.android.srt.Sensor;


public class SensorsDataSource {

	// Database fields
	private SQLiteDatabase database;
	private SrtDatabaseHelper dbHelper;
	private String[] allColumnsSensorTable = { SensorTable.COLUMN_HARDWAREID,
			SensorTable.COLUMN_PLACE, SensorTable.COLUMN_TYPE,
			SensorTable.COLUMN_ENVIRONMENT};
	private String columnsRetrieve = 
					ActivationTable.COLUMN_HARDWAREID 
			+ "," + SensorTable.COLUMN_PLACE 
			+ "," + SensorTable.COLUMN_TYPE 
			+ "," + SensorTable.COLUMN_ENVIRONMENT
			+ "," + ActivationTable.COLUMN_VALUE
			+ "," + ActivationTable.COLUMN_DATE
			+ "," + ActivationTable.COLUMN_DATELONG;
	
	public SensorsDataSource(Context context) {
		dbHelper = new SrtDatabaseHelper(context);
	}

	public void open() throws SQLException {
		database = dbHelper.getWritableDatabase();
	}

	public void close() {
		dbHelper.close();
	}

	public boolean createSensor(Sensor sensor) {
		boolean creationSuccess = false;
		
	    ContentValues values = new ContentValues();
	    values.put(SensorTable.COLUMN_HARDWAREID, sensor.getId());
	    values.put(SensorTable.COLUMN_PLACE, sensor.getPlace());
	    values.put(SensorTable.COLUMN_TYPE, sensor.getType());
	    values.put(SensorTable.COLUMN_ENVIRONMENT, sensor.getEnvironment());
	    
	    long insertId = database.insert(SensorTable.TABLE_SENSORS, null,
	        values);
	    
	    // check db insert was correct
	    if(insertId != -1){
	    	creationSuccess = true;
		    Log.i(PushService.TAG, "Sensor inserted with hardwareId: " + sensor.getId());
	    }
	    return creationSuccess;
	}

	public void deleteSensor(Sensor sensor) {
	    String id = sensor.getId();

	    database.delete(SensorTable.TABLE_SENSORS, SensorTable.COLUMN_HARDWAREID
	        + " =?", new String[] {id});
	    Log.i(PushService.TAG, "Sensor deleted with hardwareId: " + id);
	}

	public boolean existSensor(Sensor sensor){
		boolean exist = false;
		
		Cursor cursor = database.query(SensorTable.TABLE_SENSORS,
		        allColumnsSensorTable, SensorTable.COLUMN_HARDWAREID + " =?", new String[] {sensor.getId()}, null,
		        null, null, null);
		
		if(cursor.getCount() == 1){
			exist = true;
		}
		cursor.close();
		
		return exist;
	}
	
	public boolean createActivation(Sensor sensor) {
		boolean creationSuccess = false;
		
		// Parse string to date
	    int[] valuesDate = stringDatetoInt(sensor.getDate()); //e.g. 22/8/12 14:33:53:265
	    // Parse values to get long
	    long dateLong = dateIntToLong(valuesDate);
		
	    ContentValues values = new ContentValues();
	    values.put(ActivationTable.COLUMN_HARDWAREID, sensor.getId());
	    values.put(ActivationTable.COLUMN_VALUE, sensor.getValue());
	    values.put(ActivationTable.COLUMN_DATE, sensor.getDate());
	    values.put(ActivationTable.COLUMN_DATELONG, dateLong);
	    
	    long insertId = database.insert(ActivationTable.TABLE_ACTIVATIONS, null,
	        values);
	    
	    // check db insert was correct
	    if(insertId != -1){
	    	creationSuccess = true;
		    Log.i(PushService.TAG, "Activation inserted with id | hardwareId: " + insertId +
		    		" | " + sensor.getId());
	    }
	    return creationSuccess;
	}
	
	// returns all the sensors from db
	public ArrayList<Sensor> getAllSensors() {
	    ArrayList<Sensor> sensors = new ArrayList<Sensor>();

	    Cursor cursor = database.query(SensorTable.TABLE_SENSORS,
		        allColumnsSensorTable, null, null, null, null, null);
	    
	    cursor.moveToFirst();
	    while (!cursor.isAfterLast()) {
	    	Sensor sensor = cursorToSensor(cursor);
	    	sensor = getLastActivationByID(sensor.getId());
	    	sensors.add(sensor);
	    	cursor.moveToNext();
	    }
	    // Make sure to close the cursor
	    cursor.close();
	    	    
	    return sensors;
	}
	
	// returns all the sensors from db filter by environment
	public ArrayList<Sensor> getAllSensors(String environment) {
	    ArrayList<Sensor> sensors = new ArrayList<Sensor>();
	    
	    Cursor cursor = database.query(SensorTable.TABLE_SENSORS,
		        allColumnsSensorTable, SensorTable.COLUMN_ENVIRONMENT + " =?", new String[] {environment}, null,
		        null, null, null);
	    
	    cursor.moveToFirst();
	    while (!cursor.isAfterLast()) {
	    	Sensor sensor = cursorToSensor(cursor);
	    	sensor = getLastActivationByID(sensor.getId());
	    	sensors.add(sensor);
	    	cursor.moveToNext();
	    }
	    // Make sure to close the cursor
	    cursor.close();
	    	    
	    return sensors;
	}

	// returns all the activations from db filter by id and by lastime
	public ArrayList<Sensor> getAllSensors(String hardwareID, long lastTime) {
	    ArrayList<Sensor> sensors = new ArrayList<Sensor>();
		Sensor sensor = null;
		
	    // Inner join between both tables, by hardware id
	    String MY_QUERY = "SELECT " + columnsRetrieve + " FROM " + SensorTable.TABLE_SENSORS + " a INNER JOIN " 
	    + ActivationTable.TABLE_ACTIVATIONS + " b ON a."+SensorTable.COLUMN_HARDWAREID+"=b." 
	    + ActivationTable.COLUMN_HARDWAREID + " WHERE b." + ActivationTable.COLUMN_HARDWAREID + "=? " +
	    		"AND " + ActivationTable.COLUMN_DATELONG +" >= ? ORDER BY " + ActivationTable.COLUMN_DATELONG + " DESC";

	    Cursor cursor = database.rawQuery(MY_QUERY, new String[]{hardwareID, String.valueOf(lastTime)});
	
	    cursor.moveToFirst();
	    while (!cursor.isAfterLast()) {
	    	sensor = cursorToSensor(cursor);
	    	sensors.add(sensor);
	    	cursor.moveToNext();
	    }
	    // Make sure to close the cursor
	    cursor.close();
	    	    
	    return sensors;
	}
	
	// returns the last activation by a sensor ID
	public Sensor getLastActivationByID(String id) {
		Sensor sensor = null;
		
	    // Inner join between both tables, by hardware id
	    String MY_QUERY = "SELECT " + columnsRetrieve + " FROM " + SensorTable.TABLE_SENSORS + " a INNER JOIN " 
	    + ActivationTable.TABLE_ACTIVATIONS + " b ON a."+SensorTable.COLUMN_HARDWAREID+"=b." 
	    + ActivationTable.COLUMN_HARDWAREID + " WHERE b." + ActivationTable.COLUMN_HARDWAREID + "=? ORDER BY " + ActivationTable.COLUMN_DATELONG + " DESC";

	    Cursor cursor = database.rawQuery(MY_QUERY, new String[]{id});
	
	    if(cursor.getCount() > 0){
		    cursor.moveToFirst();
		    sensor = cursorToSensor(cursor);
	    }
	    // Make sure to close the cursor
	    cursor.close();
	    
	    return sensor;
	}
	
	// returns the last sensor to be activated
	public Sensor getLastSensor() {
		Sensor sensor = null;
		
	    // Inner join between both tables, by hardware id
	    String MY_QUERY = "SELECT " + columnsRetrieve + " FROM " + SensorTable.TABLE_SENSORS + " a INNER JOIN " 
	    + ActivationTable.TABLE_ACTIVATIONS + " b ON a."+SensorTable.COLUMN_HARDWAREID+"=b." 
	    + ActivationTable.COLUMN_HARDWAREID + " ORDER BY " + ActivationTable.COLUMN_DATELONG + " DESC";

	    Cursor cursor = database.rawQuery(MY_QUERY, null);
	
	    if(cursor.getCount() > 0){
		    cursor.moveToFirst();
		    sensor = cursorToSensor(cursor);
	    }
	    // Make sure to close the cursor
	    cursor.close();
	    
	    return sensor;
	}
	
	// returns the last sensor to be activated filter by environment
	public Sensor getLastSensor(String environment) {
		Sensor sensor = null;
		
	    // Inner join between both tables, by hardware id
	    String MY_QUERY = "SELECT " + columnsRetrieve + " FROM " + SensorTable.TABLE_SENSORS + " a INNER JOIN " 
	    + ActivationTable.TABLE_ACTIVATIONS + " b ON a."+SensorTable.COLUMN_HARDWAREID+"=b." 
	    + ActivationTable.COLUMN_HARDWAREID + " WHERE a." + SensorTable.COLUMN_ENVIRONMENT + "=? ORDER BY " + ActivationTable.COLUMN_DATELONG + " DESC";

	    Cursor cursor = database.rawQuery(MY_QUERY, new String[]{environment});
	
	    if(cursor.getCount() > 0){
		    cursor.moveToFirst();
		    sensor = cursorToSensor(cursor);
	    }
	    // Make sure to close the cursor
	    cursor.close();
	    
	    return sensor;
	}
	
	private Sensor cursorToSensor(Cursor cursor) {
		Sensor sensor = new Sensor();
		sensor.setId(cursor.getString(0));
		sensor.setPlace(cursor.getString(1));
		sensor.setType(cursor.getString(2));
		sensor.setEnvironment(cursor.getString(3));
	
		if(cursor.getColumnCount() > 4){
			sensor.setValue(cursor.getString(4));
			sensor.setDate(cursor.getString(5));
			sensor.setDateLong(Long.parseLong(cursor.getString(6)));
		}
		return sensor;
	}
	
	private int[] stringDatetoInt(String date){
		int[] dateInt = new int[7];
		
		// 22/8/12 14:33:53:265
		String[] separated = date.split(" ");
		// 0 index: 22/8/12 - 1 index: 14:33:53:265
		String[] separated1 = separated[0].split("/");
		String[] separated2 = separated[1].split(":");
		try {
		    dateInt[0] = Integer.parseInt(separated1[0]);
		    dateInt[1] = Integer.parseInt(separated1[1]);
		    dateInt[2] = Integer.parseInt(separated1[2]);
		    dateInt[3] = Integer.parseInt(separated2[0]);
		    dateInt[4] = Integer.parseInt(separated2[1]);
		    dateInt[5] = Integer.parseInt(separated2[2]);
		    dateInt[6] = Integer.parseInt(separated2[3]);
		    
		} catch(NumberFormatException nfe) {
		   System.out.println("Could not parse " + nfe);
		} 
		
		
		return dateInt;
	}
	
	private long dateIntToLong(int[] valuesDate){
		Calendar c = Calendar.getInstance();
	    c.set(Calendar.DAY_OF_MONTH, valuesDate[0]);
	    c.set(Calendar.MONTH, valuesDate[1]-1);
	    c.set(Calendar.YEAR, 2000+valuesDate[2]);
	    c.set(Calendar.HOUR_OF_DAY, valuesDate[3]);
	    c.set(Calendar.MINUTE, valuesDate[4]);
	    c.set(Calendar.SECOND, valuesDate[5]);
	    c.set(Calendar.MILLISECOND, valuesDate[6]);
	    long time = c.getTime().getTime();
	    // Parse date to long
	    return time;
	}
	
} 