package com.hyperionics.webdavserver;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.ServiceConnection;
import android.os.Bundle;
import android.os.IBinder;
import android.util.Log;
import android.view.Menu;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;


public class MainActivity extends Activity
{
    // ui element
    TextView mServerStatusText, mIpStatusText;
    Switch mServerSwitch;
    EditText mPortEdit;

    @Override
    protected void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.i("my", "app start");

        // UI object initialization assignment
        mServerStatusText = (TextView) this.findViewById(R.id.server_status_textview);
        mIpStatusText = (TextView) this.findViewById(R.id.ip_status_textview);
        mServerSwitch = (Switch) this.findViewById(R.id.server_switch);
        mPortEdit = findViewById(R.id.portNo);
        int portNo = getSharedPreferences("WebDav", MODE_PRIVATE).getInt("port", 8080);
        mPortEdit.setText(Integer.toString(portNo));

        if (isMyServiceRunning())
        {
            mServerStatusText.setText("http service started");
            doStartService();
            mServerSwitch.setChecked(true);
        }
        else
            mServerStatusText.setText("http service has not been started");

        mIpStatusText.setText("Current IP: " + Utils.getIPAddress(true));

        mServerSwitch.setOnCheckedChangeListener(new OnCheckedChangeListener()
        {
            @SuppressLint("SetTextI18n")
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
            {
                if (isChecked)
                {
                    savePort();
                    mPortEdit.setEnabled(false);
                    doStartService();
                }
                else
                {
                    stopService(new Intent(MainActivity.this, HttpService.class));
                    ((TextView) findViewById(R.id.server_status_textview)).setText("http service is not running");
                    mPortEdit.setEnabled(true);
                }
            }
        });
    }

    @Override
    protected void onPause() {
        savePort();
        super.onPause();
    }

    private void savePort() {
        String s = mPortEdit.getText().toString();
        int portNo = 8080;
        try {
            portNo = Integer.parseInt(s);
        } catch (NumberFormatException ignore) {}
        if (portNo == 0)
            portNo = 8080;
        mPortEdit.setText(Integer.toString(portNo));
        getSharedPreferences("WebDav", MODE_PRIVATE)
                .edit().putInt("port", portNo)
                .apply();
    }

    private void doStartService()
    {
        startService(new Intent(MainActivity.this, HttpService.class));
        ((TextView) this.findViewById(R.id.server_status_textview)).setText("http service is started");
    }

    private boolean isMyServiceRunning()
    {
        ActivityManager manager = (ActivityManager) getSystemService(Context.ACTIVITY_SERVICE);
        for (RunningServiceInfo service : manager.getRunningServices(Integer.MAX_VALUE))
        {
            if (HttpService.class.getName().equals(service.service.getClassName()))
            {
                Log.i("my", "service already running...");
                return true;
            }
        }
        return false;
    }
}
