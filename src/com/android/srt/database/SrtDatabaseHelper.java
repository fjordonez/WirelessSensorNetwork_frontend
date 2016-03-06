package com.android.srt.database;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
 
public class SrtDatabaseHelper extends SQLiteOpenHelper {
 	
	private static final String DATABASE_NAME = "sensors.db";
	private static final int DATABASE_VERSION = 1;
		
	public SrtDatabaseHelper(Context context) {
		super(context, DATABASE_NAME, null, DATABASE_VERSION);
	}
	
	// Method is called during creation of the database
	@Override
	public void onCreate(SQLiteDatabase database) {
		SensorTable.onCreate(database);
		ActivationTable.onCreate(database);
	}
	
	// Method is called during an upgrade of the database,
	// e.g. if increase of database version
	@Override
	public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
		SensorTable.onUpgrade(db, oldVersion, newVersion);
		ActivationTable.onUpgrade(db, oldVersion, newVersion);
	}
	
}