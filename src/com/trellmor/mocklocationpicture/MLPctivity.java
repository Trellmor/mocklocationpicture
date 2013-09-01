package com.trellmor.mocklocationpicture;

import com.trellmor.mocklocationpicture.R;

import android.os.Bundle;
import android.app.Activity;
import android.view.Menu;

public class MLPctivity extends Activity {

	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_mlpctivity);
	}

	@Override
	public boolean onCreateOptionsMenu(Menu menu) {
		// Inflate the menu; this adds items to the action bar if it is present.
		getMenuInflater().inflate(R.menu.mlpctivity, menu);
		return true;
	}

}
