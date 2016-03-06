package com.android.srt.database;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.android.srt.PushService;

public class SensorTable {

	public static final String TABLE_SENSORS = "sensors";
	public static final String COLUMN_HARDWAREID = "_id";
	public static final String COLUMN_PLACE = "place";
	public static final String COLUMN_TYPE = "type";
	public static final String COLUMN_ENVIRONMENT = "environment";
	
	// Database creation sql statement
	private static final String DATABASE_CREATE = "create table " 
	+ TABLE_SENSORS + "(" + COLUMN_HARDWAREID 
	+ " text PRIMARY KEY, " + COLUMN_PLACE 
	+ " text NOT NULL, " + COLUMN_TYPE 
	+ " text NOT NULL, " + COLUMN_ENVIRONMENT 
	+ " text NOT NULL);";
	
	public static  void onCreate(SQLiteDatabase database) {
		database.execSQL(DATABASE_CREATE);
	}
	
	public static  void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.w(PushService.TAG, 
				"Upgrading database from version " + oldVersion + " to " 
		+ newVersion + ", which will destroy all old data");
		
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_SENSORS);
		onCreate(db);
	}
}
