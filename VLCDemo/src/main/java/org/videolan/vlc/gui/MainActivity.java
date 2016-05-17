/*****************************************************************************
 * MainActivity.java
 * ****************************************************************************
 * Copyright © 2011-2014 VLC authors and VideoLAN
 * <p/>
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 2 of the License, or
 * (at your option) any later version.
 * <p/>
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * <p/>
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston MA 02110-1301, USA.
 *****************************************************************************/

package org.videolan.vlc.gui;

import android.annotation.TargetApi;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.Configuration;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.app.FragmentManager;
import android.support.v4.app.FragmentTransaction;
import android.support.v7.app.ActionBar;
import android.support.v7.app.ActionBarActivity;
import android.text.InputType;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.RelativeLayout;
import android.widget.TextView;

import org.videolan.android.ui.MyPlayerActivity;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.vlc.MediaLibrary;
import org.videolan.vlc.R;
import org.videolan.vlc.VLCApplication;
import org.videolan.vlc.VLCCallbackTask;
import org.videolan.vlc.audio.AudioService;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.gui.video.MediaInfoFragment;
import org.videolan.vlc.gui.video.VideoGridFragment;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import java.util.Arrays;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends ActionBarActivity {
    public final static String TAG = "VLC/MainActivity";

    private ActionBar mActionBar;

    private String mCurrentFragment;
    private List<String> secondaryFragments = Arrays.asList("albumsSongs", "equalizer",
            "about", "search", "mediaInfo",
            "videoGroupList");
    private HashMap<String, Fragment> mSecondaryFragments = new HashMap<String, Fragment>();

    private boolean mScanNeeded = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        if (!LibVlcUtil.hasCompatibleCPU(this)) {
            Log.e(TAG, LibVlcUtil.getErrorMsg());
            finish();
            super.onCreate(savedInstanceState);
            return;
        }

        try {
            // Start LibVLC
            VLCInstance.getLibVlcInstance();
        } catch (LibVlcException e) {
            e.printStackTrace();
            Log.e(TAG, "LibVLC failed to initialize (LibVlcException)");
            finish();
            super.onCreate(savedInstanceState);
            return;
        }

        super.onCreate(savedInstanceState);

        /*** Start initializing the UI ***/

        /* Enable the indeterminate progress feature */
        requestWindowFeature(Window.FEATURE_INDETERMINATE_PROGRESS);

        SharedPreferences pref = PreferenceManager.getDefaultSharedPreferences(this);
        boolean enableBlackTheme = pref.getBoolean("enable_black_theme", false);
        if (enableBlackTheme)
            setTheme(R.style.Theme_VLC_Black);

        setContentView(R.layout.main);

        /* Initialize UI variables */
        /* Set up the action bar */
        prepareActionBar();
        /* Reload the latest preferences */
        reloadPreferences();
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

        /* FIXME: this is used to avoid having MainActivity twice in the backstack */
        if (getIntent().hasExtra(AudioService.START_FROM_NOTIFICATION))
            getIntent().removeExtra(AudioService.START_FROM_NOTIFICATION);

        /* Load media items from database and storage */
        if (mScanNeeded)
            MediaLibrary.getInstance().loadMediaItems();
    }

    @Override
    protected void onResumeFragments() {
        super.onResumeFragments();

        // Figure out if currently-loaded fragment is a top-level fragment.
        Fragment current = getSupportFragmentManager()
                .findFragmentById(R.id.fragment_placeholder);

        if (current == null || (!current.getTag().equals(mCurrentFragment))) {
            Log.d(TAG, "Reloading displayed fragment");
            if (mCurrentFragment == null || secondaryFragments.contains(mCurrentFragment))
                mCurrentFragment = "video";
            Fragment ff = new VideoGridFragment();
            FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
            ft.replace(R.id.fragment_placeholder, ff, mCurrentFragment);
            ft.commit();
        }
    }

    /**
     * Stop audio player and save opened tab
     */
    @Override
    protected void onPause() {
        super.onPause();

        /* Check for an ongoing scan that needs to be resumed during onResume */
        mScanNeeded = MediaLibrary.getInstance().isWorking();
        /* Stop scanning for files */
        MediaLibrary.getInstance().stop();
        /* Save the tab status in pref */
        Editor editor = getSharedPreferences("MainActivity", MODE_PRIVATE).edit();
        editor.putString("fragment", mCurrentFragment);
        editor.commit();

        AudioServiceController.getInstance().unbindAudioService(this);
    }

    @Override
    protected void onRestart() {
        super.onRestart();
        /* Reload the latest preferences */
        reloadPreferences();
    }

    private static void ShowFragment(FragmentActivity activity, String tag, Fragment fragment) {
        if (fragment == null) {
            Log.e(TAG, "Cannot show a null fragment, ShowFragment(" + tag + ") aborted.");
            return;
        }

        FragmentManager fm = activity.getSupportFragmentManager();

        //abort if fragment is already the current one
        Fragment current = fm.findFragmentById(R.id.fragment_placeholder);
        if (current != null && current.getTag().equals(tag))
            return;

        //try to pop back if the fragment is already on the backstack
        if (fm.popBackStackImmediate(tag, 0))
            return;

        //fragment is not there yet, spawn a new one
        FragmentTransaction ft = fm.beginTransaction();
        ft.setCustomAnimations(R.anim.anim_enter_right, R.anim.anim_leave_left, R.anim.anim_enter_left, R.anim.anim_leave_right);
        ft.replace(R.id.fragment_placeholder, fragment, tag);
        ft.addToBackStack(tag);
        ft.commit();
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
    }

    /**
     * Handle onClick form menu buttons
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection
        switch (item.getItemId()) {
            case android.R.id.home:
                onOpenMRL();
                break;
        }
        return super.onOptionsItemSelected(item);
    }

    private void reloadPreferences() {
        SharedPreferences sharedPrefs = getSharedPreferences("MainActivity", MODE_PRIVATE);
        mCurrentFragment = sharedPrefs.getString("fragment", "video");
    }

//    private static class MainActivityHandler extends WeakHandler<MainActivity> {
//        public MainActivityHandler(MainActivity owner) {
//            super(owner);
//        }
//
//        @Override
//        public void handleMessage(Message msg) {
//            MainActivity ma = getOwner();
//            if (ma == null) return;
//
//            switch (msg.what) {
//                case ACTIVITY_SHOW_INFOLAYOUT:
//                    ma.mInfoLayout.setVisibility(View.VISIBLE);
//                    break;
//            }
//        }
//    }

    private void onOpenMRL() {
        AlertDialog.Builder b = new AlertDialog.Builder(this);
        final EditText input = new EditText(this);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        b.setTitle(R.string.open_mrl_dialog_title);
        b.setMessage(R.string.open_mrl_dialog_msg);
        b.setView(input);
        b.setPositiveButton(R.string.open, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int button) {

                /* Start this in a new thread as to not block the UI thread */
                        VLCCallbackTask task = new VLCCallbackTask(MainActivity.this) {
                            @Override
                            public void run() {
                                AudioServiceController c = AudioServiceController.getInstance();
                                String s = input.getText().toString();
                                c.load(s, false);
                            }
                        };
                        task.execute();
                    }
                }
        );
        b.setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface arg0, int arg1) {
                startActivity(new Intent(MainActivity.this,MyPlayerActivity.class));
//                return;
            }
        });
        b.show();
    }

}
