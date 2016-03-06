package com.android.srt;

import android.text.TextUtils;

public class Sensor {
	 private String hardwareId;
	 private String place;
	 private String type;
	 private String value;
	 private String date;
	 private long dateLong;
	 private String environment = "environment1"; // TODO: should be changed in the future
	 
	 // Create Sensor by default
	 public Sensor (){
		 hardwareId = "";
		 place = "";
		 type = "";
		 value = "";
		 date = "";
		 dateLong = 0;
	 }
	 
	 // Create Sensor by a codified string
	 public Sensor (String codeMessage){		 
		// Decode message to get sensor values
		String[] valuesSensor = TextUtils.split(codeMessage, ";");
		
		// Code message should have 4 values
		if(valuesSensor.length == 5){
			 hardwareId = valuesSensor[0];
			 place = valuesSensor[1];
			 type = valuesSensor[2];
			 value = valuesSensor[3];
			 date = valuesSensor[4];
		}else{
			 hardwareId = "";
			 place = "";
			 type = "";
			 value = "";
			 date = "";
			 dateLong = 0;
		}
	 }
	 
	 public void setId(String id) {
		 this.hardwareId = id;
	 }
	
	 public String getId() {
		 return hardwareId;
	 }
	 
	 public void setPlace(String place) {
		  this.place = place;
	 }
	 
	 public String getPlace() {
		  return place;
	 }
	 
	 public void setType(String type) {
		  this.type = type;
	 }
	
	public String getType() {
		  return type;
	 }
	
	 public void setValue(String value) {
		 this.value = value;
	 }
	
	 public String getValue() {
		 return value;
	 }
	
	 public void setDate(String date) {
		 this.date = date;
	 }
	
	 public String getDate() {
		 return date;
	 }
	 public void setDateLong(long dateLong) {
		 this.dateLong = dateLong;
	 }
	
	 public long getDateLong() {
		 return dateLong;
	 }
	 public void setEnvironment(String environment) {
		 this.environment = environment;
	 }
	
	 public String getEnvironment() {
		 return environment;
	 }
	 
	 // Could be used by an ArrayAdapter in the ListView
	  @Override
	  public String toString() {
	    return hardwareId + " | " + place + " | " + type;
	  }
}