package com.hyperionics.wdserverlib;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.ActivityManager;
import android.app.ActivityManager.RunningServiceInfo;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.provider.Settings;
import android.util.Log;
import android.view.View;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.snackbar.Snackbar;

import com.hyperionics.wdserverlib.BuildConfig;
import pub.devrel.easypermissions.EasyPermissions;

import static android.content.pm.PackageManager.PERMISSION_GRANTED;


public class ServerSettingsActivity extends AppCompatActivity implements EasyPermissions.RationaleCallbacks
{
    //region Fields
    private static final int RESULT_MANAGE_ALL_FILES = 101;
    private static final int RESULT_EXT_STORAGE = 102;
    TextView mIpStatusText;
    Switch mServerSwitch, mAllStorageSwitch;
    EditText mPortEdit;
    //endregion

    @SuppressLint("SetTextI18n")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        TextView appVer = findViewById(R.id.app_ver);
        appVer.setText(getString(R.string.wds_app_name) + " ver. " + BuildConfig.VERSION_NAME);

        // UI object initialization assignment
        mIpStatusText = findViewById(R.id.ip_status_textview);
        mServerSwitch = findViewById(R.id.server_switch);
        mAllStorageSwitch = findViewById(R.id.all_storage);
        mPortEdit = findViewById(R.id.portNo);
        int portNo = getSharedPreferences("WebDav", MODE_PRIVATE).getInt("port", 8080);
        mPortEdit.setText(Integer.toString(portNo));
        boolean wholeStorage = getSharedPreferences("WebDav", MODE_PRIVATE).getBoolean("WholeStorage", false);
        if (wholeStorage && isExternalStorageManager()) {
            wholeStorage = false;
            setWholeStorage(false);
        }
        mAllStorageSwitch.setChecked(wholeStorage);

        if (isMyServiceRunning())
        {
            doStartService();
            mServerSwitch.setChecked(true);
            mAllStorageSwitch.setEnabled(false);
            mPortEdit.setEnabled(false);
        }

        mIpStatusText.setText("Current IP: " + Utils.getIPAddress(true));
        mServerSwitch.setOnCheckedChangeListener(mServerSwitchListener);
        mAllStorageSwitch.setOnCheckedChangeListener(mAllStorageListener);
    }

    OnCheckedChangeListener mAllStorageListener = new OnCheckedChangeListener() {
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
            if (isChecked) {
                if (!isExternalStorageManager()) {
                    if (Build.VERSION.SDK_INT >= 30) {
                        Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                        startActivityForResult(intent, RESULT_MANAGE_ALL_FILES);
                    }
                    else {
                        // Ask for storage access
                        EasyPermissions.requestPermissions(ServerSettingsActivity.this, getString(R.string.wds_storage_perm),
                                    RESULT_EXT_STORAGE, Manifest.permission.WRITE_EXTERNAL_STORAGE);
                    }
                }
            }
            setWholeStorage(isChecked);
        }
    };

    private boolean isExternalStorageManager() {
        if (Build.VERSION.SDK_INT < 30)
            return EasyPermissions.hasPermissions(this, Manifest.permission.READ_EXTERNAL_STORAGE);
        else
            return Environment.isExternalStorageManager();
    }

    //region EasyPermissions
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == RESULT_EXT_STORAGE) {
            if (grantResults.length > 0 && grantResults[0] == PERMISSION_GRANTED) {
                setWholeStorage(true);
            }
            else {
                if (Build.VERSION.SDK_INT >= 23 && !shouldShowRequestPermissionRationale(Manifest.permission.READ_EXTERNAL_STORAGE)) {
                    Log.d("my", "Enable storage access...");
                    Snackbar snackbar = Snackbar.make(findViewById(android.R.id.content), R.string.wds_approve_perm,
                            Snackbar.LENGTH_LONG).setAction(R.string.wds_action_settings, new View.OnClickListener() {
                        @Override
                        public void onClick(View view) {
                            startActivity(new Intent(android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + getPackageName())));
                        }
                    });
                    View snackbarView = snackbar.getView();
                    TextView textView = (TextView) snackbarView.findViewById(com.google.android.material.R.id.snackbar_text);
                    textView.setMaxLines(5);  //Or as much as you need
                    snackbar.show();
                }
                setWholeStorage(false);
                mAllStorageSwitch.setChecked(false);
            }
        }
        else // Forward results to EasyPermissions
            EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == RESULT_MANAGE_ALL_FILES) {
            boolean wholeStorage = isExternalStorageManager();
            mAllStorageSwitch.setChecked(wholeStorage);
            setWholeStorage(wholeStorage);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRationaleAccepted(int requestCode) {}

    @Override
    public void onRationaleDenied(int requestCode) {
        mAllStorageSwitch.setChecked(false);
        Log.d("my", "Storage rationale denied");
    }
    //endregion

    OnCheckedChangeListener mServerSwitchListener = new OnCheckedChangeListener() {
        @SuppressLint("SetTextI18n")
        @Override
        public void onCheckedChanged(CompoundButton buttonView, boolean isChecked)
        {
            if (isChecked)
            {
                savePort();
                doStartService();
            }
            else
            {
                stopService(new Intent(ServerSettingsActivity.this, HttpService.class));
            }
            mPortEdit.setEnabled(!isChecked);
            mAllStorageSwitch.setEnabled(!isChecked);
        }
    };

    private void setWholeStorage(boolean wholeStorage) {
        getSharedPreferences("WebDav", MODE_PRIVATE)
                .edit()
                .putBoolean("WholeStorage", wholeStorage)
                .apply();
        if (Build.VERSION.SDK_INT >= 30 && !wholeStorage && isExternalStorageManager()) {
            Toast.makeText(ServerSettingsActivity.this, R.string.wds_withdraw_perm, Toast.LENGTH_LONG).show();
            new Handler(getMainLooper()).postDelayed(new Runnable() {
                @Override
                public void run() {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION);
                    startActivity(intent);
                }
            }, 1500);
        }
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
        startService(new Intent(ServerSettingsActivity.this, HttpService.class));
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
