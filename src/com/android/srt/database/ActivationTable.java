package com.android.srt.database;

import android.database.sqlite.SQLiteDatabase;
import android.util.Log;

import com.android.srt.PushService;

public class ActivationTable {
	
	public static final String TABLE_ACTIVATIONS = "activations";
	public static final String COLUMN_ID = "_id";
	public static final String COLUMN_HARDWAREID = "hardwareid";
	public static final String COLUMN_VALUE = "value";
	public static final String COLUMN_DATE = "date";
	public static final String COLUMN_DATELONG = "datelong";
	
	// Database creation sql statement
	private static final String DATABASE_CREATE = "create table " 
	+ TABLE_ACTIVATIONS + "(" + COLUMN_ID 
	+ " integer PRIMARY KEY autoincrement, " + COLUMN_HARDWAREID
	+ " text NOT NULL, " + COLUMN_VALUE 
	+ " integer NOT NULL, " + COLUMN_DATE 
	+ " text NOT NULL, " + COLUMN_DATELONG
	+ " integer NOT NULL, "
	+ " FOREIGN KEY ("+ COLUMN_HARDWAREID +") REFERENCES "
	+ SensorTable.TABLE_SENSORS + " ("+ SensorTable.COLUMN_HARDWAREID +"));";
	
	public static  void onCreate(SQLiteDatabase database) {
		database.execSQL(DATABASE_CREATE);
	}
	
	public static  void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		Log.w(PushService.TAG, 
				"Upgrading database from version " + oldVersion + " to " 
		+ newVersion + ", which will destroy all old data");
		
		db.execSQL("DROP TABLE IF EXISTS " + TABLE_ACTIVATIONS);
		onCreate(db);
	}
}
