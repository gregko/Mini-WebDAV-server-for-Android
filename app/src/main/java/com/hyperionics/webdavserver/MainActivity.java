package com.hyperionics.webdavserver;

import androidx.appcompat.app.AppCompatActivity;

import android.content.ComponentName;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        if (true /* BuildConfig.DEBUG*/) { // added true here to actually use the server in release mode for my own work
            Button b = findViewById(R.id.manage_server);
            b.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    Intent intent = new Intent();
                    intent.setClassName(MainActivity.this,
                            "com.hyperionics.wdserverlib.ServerSettingsActivity");
                    startActivity(intent);
                }
            });
        }
        else {
            findViewById(R.id.manage_server).setVisibility(View.GONE);
        }
    }
}