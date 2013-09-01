package com.trellmor.mocklocationpicture;

import com.trellmor.mocklocationpicture.R;

import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.view.Menu;
import android.widget.ImageView;

public class MLPActivity extends Activity {
	private ImageView mImagePreview;

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mlpactivity);
		
		mImagePreview = (ImageView) findViewById(R.id.image_preview);
		
		Intent intent = getIntent();
		String action = intent.getAction();
		String type = intent.getType();
		
		if (Intent.ACTION_SEND.equals(action) && type != null && type.startsWith("image/")) {
			// Started by share intent, display preview image
			Uri imageUri = (Uri) intent.getParcelableExtra(Intent.EXTRA_STREAM);
			if (imageUri != null) {
				mImagePreview.setImageURI(imageUri);
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
			dialog.setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
				
				@Override
				public void onClick(DialogInterface dialog, int which) {
					Intent intent = new Intent();
					intent.setClassName("com.android.settings", "com.android.settings.DevelopmentSettings");
					startActivity(intent);
				}
			});
			dialog.setNegativeButton(android.R.string.cancel, null);
			dialog.create().show();
		}
	};

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.mlpctivity, menu);
		return true;
	}

	private boolean isMockLocationEnabled() {
		return !Settings.Secure.getString(getContentResolver(), Settings.Secure.ALLOW_MOCK_LOCATION).equals("0");
	}
}
