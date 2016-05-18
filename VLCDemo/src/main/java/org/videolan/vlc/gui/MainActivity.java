/*****************************************************************************
 * MainActivity.java
 * ****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
 * <p>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * <p>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.gui;

import android.annotation.TargetApi;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.util.Log;
import android.view.Display;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;

import com.slidingmenu.lib.SlidingMenu;

import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.audio.AudioService;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.gui.audio.AudioAlbumsSongsFragment;
import org.videolan.vlc.gui.audio.AudioPlayer;
import org.videolan.vlc.gui.audio.EqualizerFragment;
import org.videolan.vlc.gui.video.MediaInfoFragment;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.gui.video.VideoPlayerActivity;
import org.videolan.vlc.util.Util;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;
import org.videolan.vlc.widget.SlidingPaneLayout;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends ActionBarActivity implements View.OnClickListener{
    public final static String TAG = "VLC/MainActivity";

    protected static final String ACTION_SHOW_PROGRESSBAR = "org.videolan.vlc.gui.ShowProgressBar";
    protected static final String ACTION_HIDE_PROGRESSBAR = "org.videolan.vlc.gui.HideProgressBar";
    protected static final String ACTION_SHOW_TEXTINFO = "org.videolan.vlc.gui.ShowTextInfo";
    public static final String ACTION_SHOW_PLAYER = "org.videolan.vlc.gui.ShowPlayer";

    private static final int ACTIVITY_SHOW_INFOLAYOUT = 2;

    private EditText editUri;
    private EditText editPath;
    private Button btnPlay;
    private Button btnPlayLocal;
    private ActionBar mActionBar;
    private Handler mHandler = new MainActivityHandler(this);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!LibVlcUtil.hasCompatibleCPU(this)) {
            Log.e(TAG, LibVlcUtil.getErrorMsg());
            finish();
            super.onCreate(savedInstanceState);
            return;
        }

        super.onCreate(savedInstanceState);

        /* Enable the indeterminate progress feature */
//        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        View v_main = LayoutInflater.from(this).inflate(R.layout.main, null);
        setContentView(v_main);
        initView();
        /* Set up the action bar */
        prepareActionBar();

        /* Prepare the progressBar */
        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_SHOW_PROGRESSBAR);
        filter.addAction(ACTION_HIDE_PROGRESSBAR);
        filter.addAction(ACTION_SHOW_TEXTINFO);
        filter.addAction(ACTION_SHOW_PLAYER);
        registerReceiver(messageReceiver, filter);
    }

    @TargetApi(Build.VERSION_CODES.HONEYCOMB)
    private void prepareActionBar() {
        mActionBar = getSupportActionBar();
        mActionBar.setNavigationMode(ActionBar.NAVIGATION_MODE_STANDARD);
        mActionBar.setDisplayHomeAsUpEnabled(true);
    }

    @Override
    protected void onResume() {
        super.onResume();
        AudioServiceController.getInstance().bindAudioService(this);
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
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btnPlay:
                /* Start this in a new thread as to not block the UI thread */
                AudioServiceController c = AudioServiceController.getInstance();
                String s = editUri.getText().toString();
                s="http://114.215.238.235:5581/glass/web/glassactivation.mp4";
                c.load(s, false);
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

    @Override
    protected void onPause() {
        super.onPause();
        MediaLibrary.getInstance().stop();
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

    /**
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
                AudioServiceController c = AudioServiceController.getInstance();
                String s = "http://114.215.238.235:5581/glass/web/glassactivation.mp4";
                c.load(s, false);
                break;
        }
        return super.onOptionsItemSelected(item);
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

                if (info == null) {
                    /* Cancel any upcoming visibility change */
                    mHandler.removeMessages(ACTIVITY_SHOW_INFOLAYOUT);
                }
            }
        }
    };

    private static class MainActivityHandler extends WeakHandler<MainActivity> {
        public MainActivityHandler(MainActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
        }
    }

}
