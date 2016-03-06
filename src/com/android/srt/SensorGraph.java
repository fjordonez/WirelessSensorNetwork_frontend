package com.android.srt;

import org.achartengine.ChartFactory;
import org.achartengine.chart.PointStyle;
import org.achartengine.model.TimeSeries;
import org.achartengine.model.XYMultipleSeriesDataset;
import org.achartengine.renderer.XYMultipleSeriesRenderer;
import org.achartengine.renderer.XYSeriesRenderer;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Paint.Align;

public class SensorGraph{

	private int[]			valuesArray;
	private String			hardwareID;
	private int				dots;
	
	public SensorGraph (int[] activationArray, String hardwareID, int dots){
		this.valuesArray = activationArray;
		this.hardwareID = hardwareID;
		this.dots = dots;
	}
	
	public Intent getIntent(Context context) {
		
		// Initialize data first
		int[] x = new int [dots]; // x values!
		for (int i = 0; i < x.length; i++) {
			x[i] = i+1;
		}

		int[] y =  valuesArray; // y values!
		TimeSeries series = new TimeSeries("Sensor ID: " + hardwareID); 
		for( int i = 0; i < x.length; i++)
		{
			series.add(x[i], y[i]);
		}
				
		XYMultipleSeriesDataset dataset = new XYMultipleSeriesDataset();
		dataset.addSeries(series);
		
		XYMultipleSeriesRenderer mRenderer = new XYMultipleSeriesRenderer(); // Holds a collection of XYSeriesRenderer and customizes the graph
		XYSeriesRenderer renderer = new XYSeriesRenderer(); // This will be used to customize line 1
		
		 int rendererLength = mRenderer.getSeriesRendererCount();
		    for (int i = 0; i < rendererLength; i++) 
		    {
		      ((XYSeriesRenderer) mRenderer.getSeriesRendererAt(i)).setFillPoints(true);
		    } 
		
		renderer.setFillPoints(true);
	    renderer.setColor(Color.parseColor("#2fd29e"));
	    renderer.setPointStyle(PointStyle.CIRCLE);    
		    
		mRenderer.addSeriesRenderer(renderer);
		// change colors to match the style
		mRenderer.setAxisTitleTextSize(15);
		mRenderer.setChartTitleTextSize(15);
		mRenderer.setLabelsTextSize(15);
		mRenderer.setLegendTextSize(15);
	    mRenderer.setPointSize(8f);

	    mRenderer.setApplyBackgroundColor(true);
	    mRenderer.setBackgroundColor(Color.parseColor("#F5F5F5"));
	    mRenderer.setMarginsColor(Color.parseColor("#F5F5F5"));

	    mRenderer.setChartTitle("Activations Graph: " + hardwareID);
	    mRenderer.setXTitle("Graph resolution (# of dots)");  
	    mRenderer.setYTitle("Activation", 0);
	    
	    // add 2 extra columns to view it correctly
	    mRenderer.setXLabels(dots+2);
	    mRenderer.setXAxisMin(0);
	    mRenderer.setXAxisMax(dots+1);
	    mRenderer.setXLabelsAlign(Align.RIGHT);
 
	    // add 2 extra rows (-1 & 2) to view it correctly
	    mRenderer.setYLabels(3);  
	    mRenderer.setYAxisMin(-1, 0);
	    mRenderer.setYAxisMax(2, 0);
	    mRenderer.setYAxisAlign(Align.LEFT, 0);
	    mRenderer.setYLabelsAlign(Align.LEFT, 0);

	    // Axes, grid
	    mRenderer.setAxesColor(Color.LTGRAY);
	    mRenderer.setLabelsColor(Color.parseColor("#5f5f5f"));     
	    mRenderer.setShowGrid(true);
	    mRenderer.setGridColor(Color.GRAY);

	    // block graph and delete margins
	    mRenderer.setPanEnabled(false, false);
	    mRenderer.setShowLegend(false);
		
	    String appTitle = context.getResources().getString(R.string.app_name);
		Intent intent = ChartFactory.getScatterChartIntent(context, dataset, mRenderer, appTitle+": Activation Graph");
		return intent;
		
	}

}
