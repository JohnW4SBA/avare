/*
Copyright (c) 2012, Zubair Khan (governer@gmail.com) 
All rights reserved.

Redistribution and use in source and binary forms, with or without modification, are permitted provided that the following conditions are met:

    * Redistributions of source code must retain the above copyright notice, this list of conditions and the following disclaimer.
    *     * Redistributions in binary form must reproduce the above copyright notice, this list of conditions and the following disclaimer in the documentation and/or other materials provided with the distribution.
    *
    *     THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
*/

package com.ds.avare.place;

import java.io.File;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Locale;

import java.util.Observable;
import com.ds.avare.StorageService;
import com.ds.avare.gps.GpsParams;
import com.ds.avare.position.Projection;
import com.ds.avare.storage.DataBaseHelper;
import com.ds.avare.storage.DataSource;
import com.ds.avare.storage.Preferences;
import com.ds.avare.storage.StringPreference;
import com.ds.avare.utils.BitmapHolder;
import com.ds.avare.utils.Helper;

import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.os.AsyncTask;

/**
 * @author zkhan
 * Stores everything about destination, its name (ICAO code)
 * Does databse query to find the destination as well.
 */
public class Destination extends Observable {

    /**
     * 
     */
    private String mName;
    /**
     * Cache it for database query from async task
     */
    private DataSource mDataSource;
    /**
     * 
     */
    private double mDistance;
    /**
     * 
     */
    private double mBearing;
    /**
     * If a destination is found?
     */
    private boolean mFound;
    /**
     * ETA to destination
     */
    private String mEta;
        
    /*
     * Its lon/lat
     */
    private double mLond;
    private double mLatd;

    private String mDiagramFound;
    
    private Preferences mPref;
    
    private StorageService mService;
    
    private boolean mLooking;
    
    private String mDestType;
    private String mDbType;
    private LinkedList<Runway> mRunways;
    
    public static final String GPS = "GPS";
    public static final String MAPS = "Maps";
    public static final String BASE = "Base";
    public static final String FIX = "Fix";
    public static final String NAVAID = "Navaid";
    
    /**
     * Contains all info in a hash map for the destination
     * Dozens of parameters in a linked map because simple map would rearrange the importance
     */
    private LinkedHashMap <String, String>mParams;
    
	/**
	 * @param name
	 * @param DataSource
	 */
	public Destination(String name, String type, Preferences pref, StorageService service) {
        mDbType = "";
        mFound = mLooking = false;
        mRunways = new LinkedList<Runway>();
        mService = service;
        mDataSource = mService.getDBResource(); 
        mPref = pref;
        mEta = new String("--:--");
        mParams = new LinkedHashMap<String, String>();
        mDiagramFound = null;
        /*
         * GPS
         * GPS coordinates are either x&y (user), or addr@x&y (google maps)
         * get the x&y part, then parse them to lon=y lat=x
         */
	    if(name.contains("&")) {
	        String token[] = new String[2];
	        token[1] = token[0] = name;
	        if(name.contains("@")) {
	            /*
	             * This could be the geo point from maps
	             */
	            token = name.split("@");
	        }
	        /*
	         * This is lon/lat destination
	         */
	        String tokens[] = token[1].split("&");
	        
	        try {
    	        mLond = Double.parseDouble(tokens[1]);
    	        mLatd = Double.parseDouble(tokens[0]);
	        }
	        catch (Exception e) {
	            /*
	             * Bad input from user on GPS
	             */
	            mName = "";
	            mDestType = "";
	            return;
	        }
	        if((mLond > 0) || (mLond < -179.99) || (mLatd < 0) || (mLatd > 89.99)) {
	            /*
	             * Sane input
	             */
                mName = "";
                mDestType = "";
                return;	            
	        }
	        mName = token[0];
	        mDestType = type;
	        return;
	    }
	    mName = name.toUpperCase(Locale.getDefault());
	    mDestType = type;
    	mLond = mLatd = 0;
	}

	/**
	 * 
	 * @return
	 */
	public String getStorageName() {
	    StringPreference s = new StringPreference(mDestType, mDbType, getFacilityName(), getID());
	    return s.getHashedName();
	}
	
	/**
     * Update the current speed, lat, lon, that will update
     * ETA, distance and bearing to the destination
	 * @param params
	 */
	public void updateTo(GpsParams params) {
	    
	    /*
	     */
        double mLon = params.getLongitude();
        double mLat = params.getLatitude();
        double speed = params.getSpeed();

		if(!mFound) {
			return;
		}		

		/*
		 * Project and find distance
		 */
		Projection p = new Projection(mLon, mLat, mLond, mLatd);
		
    	mDistance = p.getDistance();

    	mBearing = p.getBearing();
    	
    	/*
    	 * ETA when speed != 0
    	 */
    	if(speed != 0) {
	    	int etahr = (int)(mDistance / speed);
	    	int etamin =  (int)Math.round((mDistance / speed - (double)etahr) * 60);
	    	if(etahr > 99) {
	    	    mEta = "XX:XX";
	    	}
	    	else {
    	    	String hr = String.format(Locale.getDefault(), "%02d", etahr);
    	    	String min = String.format(Locale.getDefault(), "%02d", etamin);
            	mEta = new String(hr + ":" + min);
	    	}
    	}
    	else {
    	    /*
    	     * NaN avoid
    	     */
    		mEta = new String("--:--");
    	}
	}
	
	/* (non-Javadoc)
	 * @see java.lang.Object#toString()
	 */
	@Override
	public String toString() {
	    /*
	     * For display purpose
	     */
		if(!mFound) {
			return(mName + "? ");
		}
		else {
			return Helper.makeLine(mDistance, Preferences.distanceConversionUnit, mEta, mBearing); 
		}
	}
	
	/**
	 * Database  query to find destination
	 */
	public void find() {
	    /*
	     * Do in background as database queries are disruptive
	     */
	    if(mDestType.equals(GPS)) {
	        /*
	         * For GPS coordinates, simply put parsed lon/lat in params
	         * No need to query database
	         */
            mParams = new LinkedHashMap<String, String>();
            mParams.put(DataBaseHelper.LONGITUDE, "" + mLond);
            mParams.put(DataBaseHelper.LATITUDE, "" + mLatd);
            mParams.put(DataBaseHelper.FACILITY_NAME, GPS);
            mDiagramFound = null;
            mFound = true;
            mLooking = false;
            mDbType = GPS;
            setChanged();
            notifyObservers(true);
	    }
	    else { 
            mLooking = true;
            DataBaseLocationTask locmDataBaseTask = new DataBaseLocationTask();
            locmDataBaseTask.execute();
	    }
	}
	
    /**
     * @author zkhan
     * Query for destination task
     */
    private class DataBaseLocationTask extends AsyncTask<Object, Void, Boolean> {

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPreExecute()
         */
        @Override
        protected void onPreExecute() {        	
        }

        /* (non-Javadoc)
         * @see android.os.AsyncTask#doInBackground(Params[])
         */
        @Override
        protected Boolean doInBackground(Object... vals) {

	        if(null == mDataSource) {
	        	return false;
        	}
        	
	        /*
	         * For Google maps address, if we have already geo decoded it using internet,
	         * then no need to do again because internet may not be available on flight.
	         * It could be coming from storage and not google maps.
	         */
	        if(mDestType.equals(MAPS)) {

	            if(mLond == 0 && mLatd == 0) {
	                /*
	                 * We have already decomposed it?
	                 * No.
	                 */
	                String strAddress = mName;
	                
	                Geocoder coder = new Geocoder(mService);
	                Address location = null;

	                /*
	                 * Decompose
	                 */
	                try {
	                    List<Address> address = coder.getFromLocationName(strAddress, 1);
	                    if (address != null) {
	                        location = address.get(0);
	                    }
	                }
	                catch (Exception e) {
	                    return false;
	                }
	                
	                if(null == location) {
	                    return false;
	                }
	                                        
	                /*
	                 * Decomposed it
	                 * 
	                 */
	                mLond = Helper.truncGeo(location.getLongitude());
	                mLatd = Helper.truncGeo(location.getLatitude());
	            }
                /*
                 * Common stuff
                 */
                mParams = new LinkedHashMap<String, String>();
                mDiagramFound = null;
                mDbType = mDestType;
                mParams.put(DataBaseHelper.TYPE, mDestType);
                mParams.put(DataBaseHelper.FACILITY_NAME, mName);
                mParams.put(DataBaseHelper.LONGITUDE, "" + mLond);
                mParams.put(DataBaseHelper.LATITUDE, "" + mLatd);
                mName += "@" + mLatd + "&" + mLond;
                return true;                    
	        }
	        
	        boolean ret = mDataSource.findDestination(mName, mDestType, mParams, mRunways);

	        if(ret && mDestType.equals(BASE)) {
	            /*
	             * Found destination extract its airport diagram
	             */
	            String file = mPref.mapsFolder() + "/plates/" + mName + ".jpg";
	            File f = new File(file);
	            if(f.exists()) {
	                mDiagramFound = file;
	                mService.loadDiagram(Destination.this.getDiagram());
	            }
	            else {
                    mService.loadDiagram(null);
	                mDiagramFound = null;
	            }
	        }
	        else {
                mService.loadDiagram(null);
                mDiagramFound = null;
	        }
			return(ret);
        }
        

        /* (non-Javadoc)
         * @see android.os.AsyncTask#onPostExecute(java.lang.Object)
         */
        @Override
        protected void onPostExecute(Boolean result) {
        	/*
        	 * This runs on UI
        	 */
			mFound = result;
			if(mFound) {
                mDbType = mParams.get(DataBaseHelper.TYPE);
    		    mLond = Double.parseDouble(mParams.get(DataBaseHelper.LONGITUDE));
    		    mLatd = Double.parseDouble(mParams.get(DataBaseHelper.LATITUDE));
			}
			/*
			 * Anyone watching if destination found?
			 */
			Destination.this.setChanged();
            Destination.this.notifyObservers(Boolean.valueOf(mFound));
            mLooking = false;
	    }
    }
    
    /**
     * @return
     */
    public boolean isFound() {
    	return(mFound);
    }

    /**
     * @return
     */
    public boolean isLooking() {
        return(mLooking);
    }

    /**
     * @return
     */
    public String getDiagram() {
        return(mDiagramFound);
    }

    /**
     * @return
     */
    public String getFacilityName() {
    	return(mParams.get(DataBaseHelper.FACILITY_NAME));
    }

    /**
     * @return
     */
    public String getID() {
        return(mName);
    }

    /**
     * @return
     */
    public LinkedList<Runway> getRunways() {
        return(mRunways);
    }

    /**
     * @return
     */
    public BitmapHolder getBitmap() {
        return(mService.getDiagram());
    }

    /**
     * @return
     */
    public LinkedHashMap<String, String> getParams() {
    	return(mParams);
    }
    
    /**
     * @return
     */
    public double getBearing() {
        return mBearing;
    }

    /**
     * @return
     */
    public double getDistance() {
        return mDistance;
    }

    /**
     * 
     * @return
     */
    public Location getLocation() {
        Location l = new Location("");
        l.setLatitude(mLatd);
        l.setLongitude(mLond);
        return l;
    }    
}