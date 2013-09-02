/*
 * Mock Locations from Picture
 * Copyright (C) 2013 Daniel Triendl <daniel@pew.cc>
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package com.trellmor.mocklocationpicture;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;

import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.location.Criteria;
import android.location.Location;
import android.location.LocationManager;
import android.location.LocationProvider;
import android.os.IBinder;
import android.util.Log;

public class MockLocationService extends Service {
	private static final String TAG = MockLocationService.class.getName();

	private LocationManager mLocationManager;
	private MockGpsProvider mThread = null;

	public static final String MOCK_LOCATION_PROVIDER_NAME = "MLfPProvider";
	public static final String EXTRA_LATITUDE = "EXTRA_LONGITUDE";
	public static final String EXTRA_LONGITUDE = "EXTRA_LATITUDE";

	@Override
	public void onCreate() {
		mLocationManager = (LocationManager) getSystemService(Context.LOCATION_SERVICE);
		createLocationProvider(mLocationManager);
	}

	@Override
	public void onDestroy() {
		if (mThread != null) {
			mThread.stopThread();
		}
		removeLocationProvider(mLocationManager);
	}

	@Override
	public IBinder onBind(Intent intent) {
		return null;
	}

	@Override
	public int onStartCommand(Intent intent, int flags, int startId) {
		if (intent != null) {
			float latitiude = intent.getFloatExtra(EXTRA_LATITUDE, 0F);
			float longitude = intent.getFloatExtra(EXTRA_LONGITUDE, 0F);

			// Start task to inject locations
			if (mThread != null) {
				mThread.stopThread();
			}
			mThread = new MockGpsProvider(mLocationManager, latitiude,
					longitude);
			mThread.start();
		}
		return START_NOT_STICKY;
	}

	private void createLocationProvider(LocationManager lm) {
		removeLocationProvider(lm);
		try {
			lm.addTestProvider(MOCK_LOCATION_PROVIDER_NAME, true, false, false,
					false, true, true, true, Criteria.POWER_LOW,
					Criteria.ACCURACY_FINE);
			lm.setTestProviderEnabled(MOCK_LOCATION_PROVIDER_NAME, true);
			lm.setTestProviderStatus(MOCK_LOCATION_PROVIDER_NAME,
					LocationProvider.AVAILABLE, null,
					System.currentTimeMillis());
		} catch (SecurityException e) {
			// Mock locations are disabled
		}
	}

	private void removeLocationProvider(LocationManager lm) {
		if (lm.getProvider(MOCK_LOCATION_PROVIDER_NAME) != null) {
			try {
				lm.removeTestProvider(MOCK_LOCATION_PROVIDER_NAME);
			} catch (SecurityException e) {
			}
		}
	}

	/**
	 * MLP: FiM
	 * 
	 * Mock Location Provider: Fake GPS is Magic
	 * 
	 * (Did you think I was talking about little pastel horses?)
	 * 
	 * @author Daniel
	 * 
	 */
	private class MockGpsProvider extends Thread {
		private LocationManager mLocationManager;
		private float mLatitude;
		private float mLongitude;
		private volatile boolean mRunning;

		public MockGpsProvider(LocationManager locationManager, float latitude,
				float longitude) {
			mThread = this;
			mLocationManager = locationManager;
			mLatitude = latitude;
			mLongitude = longitude;
		}

		public void stopThread() {
			mRunning = false;
			interrupt();
		}

		@Override
		public void run() {
			mRunning = true;
			for (int i = 0; i < 60 * 4; i++) {
				if (!mRunning)
					break;
				
				// Use GPS_PROVIDER to override read gps
				Location loc = new Location(LocationManager.GPS_PROVIDER);
				loc.setLatitude(mLatitude);
				loc.setLongitude(mLongitude);
				loc.setAccuracy(1F);
				loc.setTime(System.currentTimeMillis());

				// Fix JellyBean locations
				try {
					Method makeComplete;
					makeComplete = Location.class.getMethod("makeComplete");
					if (makeComplete != null) {
						makeComplete.invoke(loc);
					}
				} catch (NoSuchMethodException e) {
					Log.w(TAG, e);
				} catch (IllegalArgumentException e) {
					Log.w(TAG, e);
				} catch (IllegalAccessException e) {
					Log.w(TAG, e);
				} catch (InvocationTargetException e) {
					Log.w(TAG, e);
				}

				try {
					mLocationManager.setTestProviderLocation(
							MOCK_LOCATION_PROVIDER_NAME, loc);
				} catch (SecurityException e) {
					// Mock Locations have been disabled, stop service
					stopSelf();
					break;
				}
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
			
			if (mRunning) {
				// Thread was not interrupted, stop the service
				mRunning = false;
				stopSelf();
			}
		}
	}
}
