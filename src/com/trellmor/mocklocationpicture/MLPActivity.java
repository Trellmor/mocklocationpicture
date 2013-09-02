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

import java.io.IOException;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.MediaStore;
import android.provider.Settings;
import android.support.v4.app.ShareCompat;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewTreeObserver;
import android.widget.ImageView;
import android.widget.Toast;

public class MLPActivity extends Activity implements
		ViewTreeObserver.OnGlobalLayoutListener {
	private static final String TAG = MLPActivity.class.getName();
	
	private ImageView mImagePreview;
	private MenuItem mMenuShare;
	
	private Uri mImageUri = null;
	private String mMimeType;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mlpactivity);

		mImagePreview = (ImageView) findViewById(R.id.image_preview);

		Intent intent = getIntent();
		String action = intent.getAction();
		String type = intent.getType();

		if (Intent.ACTION_SEND.equals(action) && type != null
				&& type.startsWith("image/")) {
			// Started by share intent, display preview image
			setImageUri((Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM), type);
			if (mImageUri != null) {
				showPreview(convertMediaUriToPath(mImageUri));
			}
		}
	}

	@Override
	protected void onStart() {
		super.onStart();

		if (!isMockLocationEnabled()) {
			AlertDialog.Builder dialog = new AlertDialog.Builder(this);
			dialog.setTitle(R.string.mock_locations_disabled);
			dialog.setMessage(R.string.enable_mock_locations);
			dialog.setPositiveButton(android.R.string.ok,
					new DialogInterface.OnClickListener() {

						@Override
						public void onClick(DialogInterface dialog, int which) {
							startDevelopmentSettings();
						}
					});
			dialog.setNegativeButton(android.R.string.cancel, null);
			dialog.create().show();
		} else {
			if (mImageUri != null) {
				String filename = convertMediaUriToPath(mImageUri);
				if (filename != null) {
					try {
						ExifInterface exif = new ExifInterface(filename);

						float[] latlong = new float[2];
						if (exif.getLatLong(latlong)) {
							setMockLocation(latlong[0], latlong[1]);
						} else {
							Toast.makeText(this, R.string.exif_no_latlong,
									Toast.LENGTH_SHORT).show();
						}
					} catch (IOException e) {
						Log.e(TAG, "Failed get location from image", e);
					}
				}
			}
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.mlpactivity, menu);

		mMenuShare = menu.findItem(R.id.action_share);
		configureShareItem(mMenuShare);
		
		// Return true to display menu
		return true;
	}

	@Override
	public boolean onOptionsItemSelected(MenuItem item) {
		switch (item.getItemId()) {
		case R.id.action_settings:
			startDevelopmentSettings();
			return true;
		}
		return super.onOptionsItemSelected(item);
	}

	public void clear(View v) {
		setImageUri(null, null);
		stopService(new Intent(this, MockLocationService.class));
		mImagePreview.setImageBitmap(null);
	}

	private boolean isMockLocationEnabled() {
		return !Settings.Secure.getString(getContentResolver(),
				Settings.Secure.ALLOW_MOCK_LOCATION).equals("0");
	}

	private void startDevelopmentSettings() {
		Intent intent = new Intent();
		intent.setClassName("com.android.settings",
				"com.android.settings.DevelopmentSettings");
		startActivity(intent);
	}

	private void setMockLocation(float latitude, float longitude) {
		Intent intent = new Intent(this, MockLocationService.class);
		intent.putExtra(MockLocationService.EXTRA_LATITUDE, latitude);
		intent.putExtra(MockLocationService.EXTRA_LONGITUDE, longitude);
		startService(intent);
	}

	private String convertMediaUriToPath(Uri uri) {
		String path = null;

		String[] proj = { MediaStore.Images.Media.DATA };
		Cursor cursor = getContentResolver().query(uri, proj, null, null, null);
		if (cursor.getCount() > 0) {
			cursor.moveToFirst();
			path = cursor.getString(cursor
					.getColumnIndexOrThrow(MediaStore.Images.Media.DATA));
			cursor.close();
		}
		return path;
	}

	static final int MAX_BITMAP_DIM = 2048;

	private void showPreview(String path) {
		final int maxHeight = mImagePreview.getHeight();
		final int maxWidth = mImagePreview.getWidth();

		if (maxHeight == 0 || maxWidth == 0) {
			// Layout not loaded yet, delay
			mImagePreview.getViewTreeObserver().addOnGlobalLayoutListener(this);
			return;
		}

		BitmapFactory.Options options = new BitmapFactory.Options();
		options.inJustDecodeBounds = true;
		BitmapFactory.decodeFile(path, options);

		int height = options.outHeight;
		int width = options.outWidth;
		int inSampleSize = 1;

		while (true) {
			if (width <= MAX_BITMAP_DIM && height <= MAX_BITMAP_DIM) {
				if (width <= maxWidth && height <= maxHeight) {
					break;
				}
			}

			width /= 2;
			height /= 2;
			inSampleSize *= 2;
		}
		options.inSampleSize = inSampleSize;
		options.inJustDecodeBounds = false;

		mImagePreview.setImageBitmap(BitmapFactory.decodeFile(path, options));
	}
	
	private void configureShareItem(MenuItem item) {
		if (mImageUri != null) {
			item.setVisible(true);
			ShareCompat.IntentBuilder shareIntent = ShareCompat.IntentBuilder.from(this);
			shareIntent.setStream(mImageUri);
			shareIntent.setType(mMimeType);
			ShareCompat.configureMenuItem(item, shareIntent);
		} else {
			item.setVisible(false);
		}
	}
	

	private void setImageUri(Uri uri, String mimeType) {
		mImageUri = uri;
		mMimeType = mimeType;
		if (mMenuShare != null) {
			configureShareItem(mMenuShare);
		}
	}

	@SuppressWarnings("deprecation")
	@SuppressLint("NewApi")
	@Override
	public void onGlobalLayout() {
		ViewTreeObserver obs = mImagePreview.getViewTreeObserver();
		if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
			obs.removeOnGlobalLayoutListener(this);
		} else {
			obs.removeGlobalOnLayoutListener(this);
		}

		if (mImageUri != null) {
			showPreview(convertMediaUriToPath(mImageUri));
		}
	}
}
