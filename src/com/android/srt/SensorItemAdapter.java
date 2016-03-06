package com.android.srt;

import java.util.ArrayList;

import com.android.srt.database.SensorsDataSource;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.drawable.Drawable;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.TextView;
	
public class SensorItemAdapter extends ArrayAdapter<Sensor> {
	private ArrayList<Sensor> sensors;
	
	private static final int TYPE_ITEM_COLORED = 1;
	private static final int TYPE_ITEM_NORMAL = 0;
	
    public SensorItemAdapter(Context context, int textViewResourceId, ArrayList<Sensor> sensors) {
        super(context, textViewResourceId, sensors);
        this.sensors = sensors;
    }
    
    @Override
    public int getItemViewType(int position) {

        Sensor item = getItem(position);

        SharedPreferences mPrefs = PreferenceManager.getDefaultSharedPreferences(getContext());
        
        
        // Check if this sensor was the last one to be activated
        SensorsDataSource datasource = MainActivity.datasource;
        Sensor lastSensor = new Sensor();
        if(mPrefs.contains("Environment")){
        	String environment = mPrefs.getString("Environment","");
        	lastSensor = datasource.getLastSensor(environment);
        }else{
        	lastSensor = datasource.getLastSensor();
        }
        // check to avoid error
        if(lastSensor == null){
        	return -1;
        }
        
        
        if(item.getId().equals(lastSensor.getId())){
        	return TYPE_ITEM_COLORED;
        }else{
        	return TYPE_ITEM_NORMAL;
        }
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View v = convertView;
        if (v == null) {
            LayoutInflater vi = (LayoutInflater) getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            v = vi.inflate(R.layout.list_sensor, null);
	 	}
	      
        Sensor sensor = sensors.get(position);
        if (sensor != null) {
            TextView id = (TextView) v.findViewById(R.id.sensor_hardwareId);
            TextView place = (TextView) v.findViewById(R.id.sensor_place);
            TextView type = (TextView) v.findViewById(R.id.sensor_type);
            TextView date = (TextView) v.findViewById(R.id.sensor_date);
            
            if(id != null) {
                id.setText("ID: " + sensor.getId());
            }

            if(place != null) {
            	place.setText("Place: " + sensor.getPlace() );
            }
            
            
            if(type != null) {
            	type.setText("Type: " + sensor.getType() );
            }
            
            if(date != null) {
            	date.setText(sensor.getDate() );
            }
            
            // Check if this sensor needs to be highlighted
            switch (getItemViewType(position)) {
            case TYPE_ITEM_COLORED:
            	Drawable highlight = getContext().getResources().getDrawable(R.drawable.list_selector_last);
                v.setBackgroundDrawable(highlight);
                break;
            case TYPE_ITEM_NORMAL:
            	Drawable normal = getContext().getResources().getDrawable(R.drawable.list_selector);
            	v.setBackgroundDrawable(normal);
                break;
            }

        }
        
        return v;
    }
}