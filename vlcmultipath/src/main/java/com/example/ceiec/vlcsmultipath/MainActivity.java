package com.example.ceiec.vlcsmultipath;

import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;

import org.videolan.libvlc.LibVLC;
import org.videolan.vlc.audio.AudioServiceController;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    protected static final String TAG = "Vlc";
    private EditText editUri;
    private EditText editPath;
    private Button btnPlay;
    private Button btnPlayLocal;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        initView();
    }

    private void initView() {
        editUri = (EditText) findViewById(R.id.editUri);
        editPath = (EditText) findViewById(R.id.editPath);
        btnPlay = (Button) findViewById(R.id.btnPlay);
        btnPlayLocal = (Button) findViewById(R.id.btnPlayLocal);
        ((Button) findViewById(R.id.btnMulti)).setOnClickListener(this);
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
                mediaLocation = LibVLC.PathToURI(editPath.getText().toString());
                mediaLocation = "file:///storage/emulated/0/tencent/QQfile_recv/%E5%A4%A7%E5%A6%9E%E5%AD%A6%E8%BD%A6.mp4";
                VideoPlayerMultiActivity.start(this, mediaLocation);
                break;
        }
    }

}