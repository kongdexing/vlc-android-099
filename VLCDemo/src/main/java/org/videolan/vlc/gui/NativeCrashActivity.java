package org.videolan.vlc.gui;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.zip.GZIPOutputStream;

import org.videolan.vlc.R;
import org.videolan.vlc.util.Logcat;
import org.videolan.vlc.util.Util;

import android.app.Activity;
import android.app.ProgressDialog;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

public class NativeCrashActivity extends Activity {

    private TextView mCrashLog;
    private Button mRestartButton;
    private Button mSendLog;

    private ProgressDialog mProgressDialog;

    private String mLog;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.native_crash);

        mCrashLog = (TextView) findViewById(R.id.crash_log);
        mRestartButton = (Button) findViewById(R.id.restart_vlc);
        mRestartButton.setEnabled(false);
        mSendLog = (Button) findViewById(R.id.send_log);
        mSendLog.setEnabled(false);

        mRestartButton.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                android.os.Process.killProcess(getIntent().getExtras().getInt("PID"));
                Intent i = new Intent(NativeCrashActivity.this, MainActivity.class);
                i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(i);
                finish();
            }
        });

        mSendLog.setOnClickListener(new Button.OnClickListener() {
            @Override
            public void onClick(View v) {
                String buildDate = "Build date: " + Util.readAsset("builddate.txt", "Unknown");
                String builder = "Builder: "  + Util.readAsset("builder.txt", "unknown");
                String revision = "Revision: " + Util.readAsset("revision.txt", "Unknown revision");
            }
        });

        new LogTask().execute();
    }

    class LogTask extends AsyncTask<Void, Void, String>
    {
        @Override
        protected String doInBackground(Void... v) {
            String log = null;
            try {
                log = Logcat.getLogcat();
            } catch (IOException e) {
                e.printStackTrace();
            }
            return log;
        }

        @Override
        protected void onPostExecute(String log) {
            mLog = log;
            mCrashLog.setText(log);
            mRestartButton.setEnabled(true);
            mSendLog.setEnabled(true);
        }
    }

}
