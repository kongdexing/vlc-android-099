package com.example.ceiec.vlcsmultipath;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;

import org.videolan.libvlc.LibVLC;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.util.WeakHandler;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    protected static final String TAG = "Vlc";
    protected static final String ACTION_SHOW_PROGRESSBAR = "org.videolan.vlc.gui.ShowProgressBar";
    protected static final String ACTION_HIDE_PROGRESSBAR = "org.videolan.vlc.gui.HideProgressBar";
    protected static final String ACTION_SHOW_TEXTINFO = "org.videolan.vlc.gui.ShowTextInfo";
    public static final String ACTION_SHOW_PLAYER = "org.videolan.vlc.gui.ShowPlayer";
    private static final int ACTIVITY_SHOW_INFOLAYOUT = 2;
    private EditText editUri;
    private EditText editPath;
    private Button btnPlay;
    private Button btnPlayLocal;
    private Handler mHandler = new MainActivityHandler(this);

    private static class MainActivityHandler extends WeakHandler<MainActivity> {
        public MainActivityHandler(MainActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
        }
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();

        /* Prepare the progressBar */
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW_PROGRESSBAR);
        filter.addAction(ACTION_HIDE_PROGRESSBAR);
        filter.addAction(ACTION_SHOW_TEXTINFO);
        filter.addAction(ACTION_SHOW_PLAYER);
        registerReceiver(messageReceiver, filter);
    }

    private void initView() {
        editUri = (EditText) findViewById(R.id.editUri);
        editPath = (EditText) findViewById(R.id.editPath);
        btnPlay = (Button) findViewById(R.id.btnPlay);
        btnPlayLocal = (Button) findViewById(R.id.btnPlayLocal);
        ((Button) findViewById(R.id.btnMulti)).setOnClickListener(this);
        ((Button) findViewById(R.id.btnMultiGrid)).setOnClickListener(this);
        btnPlay.setOnClickListener(this);
        btnPlayLocal.setOnClickListener(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AudioServiceController.getInstance().bindAudioService(this);
    }

    @Override
    protected void onPause() {
        super.onPause();
        AudioServiceController.getInstance().unbindAudioService(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        try {
            unregisterReceiver(messageReceiver);
        } catch (IllegalArgumentException e) {
        }
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnPlay:
                /* Start this in a new thread as to not block the UI thread */
                VLCCallbackTask task = new VLCCallbackTask(MainActivity.this) {
                    @Override
                    public void run() {
                        AudioServiceController c = AudioServiceController.getInstance();
                        String s = editUri.getText().toString();
                        c.load(s, false);
                    }
                };
                task.execute();
                break;
            case R.id.btnPlayLocal:
                String mediaLocation = LibVLC.PathToURI(editPath.getText().toString());
                mediaLocation = "file:///storage/emulated/0/tencent/QQfile_recv/%E5%A4%A7%E5%A6%9E%E5%AD%A6%E8%BD%A6.mp4";
                VideoPlayerActivity.start(this, mediaLocation);
                break;
            case R.id.btnMulti:
                break;
            case R.id.btnMultiGrid:
//                VideoGridActivity
                break;
        }
    }

    private final BroadcastReceiver messageReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            Log.i(TAG, "onReceive: action " + action);
            if (action.equalsIgnoreCase(ACTION_SHOW_PROGRESSBAR)) {
                setSupportProgressBarIndeterminateVisibility(true);
                getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else if (action.equalsIgnoreCase(ACTION_HIDE_PROGRESSBAR)) {
                setSupportProgressBarIndeterminateVisibility(false);
                getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            } else if (action.equalsIgnoreCase(ACTION_SHOW_TEXTINFO)) {
                String info = intent.getStringExtra("info");
                int max = intent.getIntExtra("max", 0);
                int progress = intent.getIntExtra("progress", 100);
                Log.i(TAG, "onReceive: ACTION_SHOW_TEXTINFO info " + info + " max " + max + " progress " + progress);

//                mInfoText.setText(info);
//                mInfoProgress.setMax(max);
//                mInfoProgress.setProgress(progress);

                if (info == null) {
                    /* Cancel any upcoming visibility change */
                    mHandler.removeMessages(ACTIVITY_SHOW_INFOLAYOUT);
//                    mInfoLayout.setVisibility(View.GONE);
                }
            } else if (action.equalsIgnoreCase(ACTION_SHOW_PLAYER)) {
//                showAudioPlayer();
            }
        }
    };

}
