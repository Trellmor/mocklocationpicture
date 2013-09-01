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
	private static final String TAG = MLPActivity.class.getName();

	private LocationManager mLocationManager;
	private MockGpsProvider mThread = null;

	public static final String MOCK_LOCATION_PROVIDER_NAME = "MLPProvider";
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
		lm.addTestProvider(MOCK_LOCATION_PROVIDER_NAME, true, false, false,
				false, true, true, true, Criteria.POWER_LOW,
				Criteria.ACCURACY_FINE);
		lm.setTestProviderEnabled(MOCK_LOCATION_PROVIDER_NAME, true);
		lm.setTestProviderStatus(MOCK_LOCATION_PROVIDER_NAME,
				LocationProvider.AVAILABLE, null, System.currentTimeMillis());
	}

	private void removeLocationProvider(LocationManager lm) {
		if (lm.getProvider(MOCK_LOCATION_PROVIDER_NAME) != null) {
			lm.removeTestProvider(MOCK_LOCATION_PROVIDER_NAME);
		}
	}

	private class MockGpsProvider extends Thread {
		private LocationManager mLocationManager;
		private float mLatitude;
		private float mLongitude;
		private volatile Thread mThread;

		public MockGpsProvider(LocationManager locationManager, float latitude,
				float longitude) {
			mThread = this;
			mLocationManager = locationManager;
			mLatitude = latitude;
			mLongitude = longitude;
		}

		public void stopThread() {
			Thread thread = mThread;
			mThread = null;
			thread.interrupt();
		}

		@Override
		public void run() {
			Thread thisThread = Thread.currentThread();
			while (mThread == thisThread) {
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

				mLocationManager.setTestProviderLocation(
						MOCK_LOCATION_PROVIDER_NAME, loc);
				try {
					Thread.sleep(250);
				} catch (InterruptedException e) {
					Thread.currentThread().interrupt();
					break;
				}
			}
		}
	}
}
