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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.ExifInterface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
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

	private static final String KEY_IMAGE_URI = "IMAGE_URI";

	private ImageView mImagePreview;
	private MenuItem mMenuShare;

	private Uri mImageUri = null;

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
			setImageUri((Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM));
			intent.setAction("");
		} else if (savedInstanceState != null) {
			setImageUri((Uri) savedInstanceState.getParcelable(KEY_IMAGE_URI));
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
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.mlpactivity, menu);

		mMenuShare = menu.findItem(R.id.action_share);

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

	@Override
	protected void onSaveInstanceState(Bundle outState) {
		super.onSaveInstanceState(outState);
		if (mImageUri != null) {
			outState.putParcelable(KEY_IMAGE_URI, mImageUri);
		} else {
			outState.remove(KEY_IMAGE_URI);
		}
	}

	public void clear(View v) {
		setImageUri(null);
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

	private void setImageUri(Uri uri) {
		mImageUri = uri;

		final int maxHeight = mImagePreview.getHeight();
		final int maxWidth = mImagePreview.getWidth();

		if (maxHeight == 0 || maxWidth == 0) {
			// Layout not loaded yet, delay
			mImagePreview.getViewTreeObserver().addOnGlobalLayoutListener(this);
			return;
		}

		// Cache image locally
		LoadImageTask task = new LoadImageTask();
		task.execute(mImageUri);
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
			setImageUri(mImageUri);
		}
	}

	private void configureShareItem(MenuItem item, Uri uri, String mimeType) {
		if (uri != null) {
			item.setVisible(true);
			ShareCompat.IntentBuilder shareIntent = ShareCompat.IntentBuilder
					.from(MLPActivity.this);
			shareIntent.setStream(uri);
			shareIntent.setType(mimeType);
			ShareCompat.configureMenuItem(item, shareIntent);
		} else {
			item.setVisible(false);
		}
	}

	private class LoadImageTask extends AsyncTask<Uri, Void, Boolean> {
		private Uri mUri = null;
		private int mMaxWidth = 0;
		private int mMaxHeight = 0;
		private Bitmap mPreview = null;
		private String mMimeType = null;

		public LoadImageTask() {
			if (mImagePreview != null) {
				mMaxWidth = mImagePreview.getWidth();
				mMaxHeight = mImagePreview.getHeight();
			}
		}

		@Override
		protected Boolean doInBackground(Uri... params) {
			if (params.length == 0)
				return false;

			mUri = params[0];
			if (mUri == null)
				return false;

			Context context = getApplicationContext();

			InputStream is;
			OutputStream os;
			File temp = null;
			try {
				temp = File.createTempFile("tmp", null, context.getCacheDir());
				temp.getParentFile().mkdirs();
				is = context.getContentResolver().openInputStream(mUri);
				try {
					os = new FileOutputStream(temp);
					try {
						byte[] buffer = new byte[1024];
						int read;

						while ((read = is.read(buffer)) != -1) {
							os.write(buffer, 0, read);
						}

						os.flush();
					} finally {
						os.close();
					}

					setMockLocation(temp.getAbsolutePath());
					mPreview = createPreview(temp.getAbsolutePath());
				} finally {
					is.close();
				}
			} catch (FileNotFoundException e) {
				Log.wtf(TAG, e);
				return false;
			} catch (IOException e) {
				Log.e(TAG, e.getMessage(), e);
				return false;
			} finally {
				if (temp != null && temp.exists()) {
					temp.delete();
				}
			}

			return true;
		}

		@Override
		protected void onPostExecute(Boolean result) {
			if (result) {
				if (mImagePreview != null) {
					mImagePreview.setImageBitmap(mPreview);
				}

				if (mMenuShare != null) {
					configureShareItem(mMenuShare, mUri, mMimeType);
				}
			}
		}

		static final int MAX_BITMAP_DIM = 2048;

		private Bitmap createPreview(String path) {
			BitmapFactory.Options options = new BitmapFactory.Options();
			options.inJustDecodeBounds = true;
			BitmapFactory.decodeFile(path, options);

			int height = options.outHeight;
			int width = options.outWidth;
			mMimeType = options.outMimeType;
			int inSampleSize = 1;

			while (true) {
				if (width <= MAX_BITMAP_DIM && height <= MAX_BITMAP_DIM) {
					if (width <= mMaxWidth && height <= mMaxHeight) {
						break;
					}
				}

				width /= 2;
				height /= 2;
				inSampleSize *= 2;
			}
			options.inSampleSize = inSampleSize;
			options.inJustDecodeBounds = false;

			return BitmapFactory.decodeFile(path, options);
		}

		private void setMockLocation(String path) {
			if (isMockLocationEnabled()) {

				try {
					ExifInterface exif = new ExifInterface(path);

					float[] latlong = new float[2];
					if (exif.getLatLong(latlong)) {
						Intent intent = new Intent(getApplicationContext(),
								MockLocationService.class);
						intent.putExtra(MockLocationService.EXTRA_LATITUDE,
								latlong[0]);
						intent.putExtra(MockLocationService.EXTRA_LONGITUDE,
								latlong[1]);
						startService(intent);
					} else {
						Toast.makeText(getApplicationContext(),
								R.string.exif_no_latlong, Toast.LENGTH_SHORT)
								.show();
					}
				} catch (IOException e) {
					Log.e(TAG, "Failed get location from image", e);
				}
			}
		}
	}
}
