package com.example.ceiec.vlcsmultipath;

import android.annotation.TargetApi;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Presentation;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.Configuration;
import android.graphics.ImageFormat;
import android.graphics.PixelFormat;
import android.media.AudioManager;
import android.media.AudioManager.OnAudioFocusChangeListener;
import android.media.MediaRouter;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.Display;
import android.view.MotionEvent;
import android.view.Surface;
import android.view.SurfaceHolder;
import android.view.SurfaceHolder.Callback;
import android.view.SurfaceView;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.View.OnSystemUiVisibilityChangeListener;
import android.view.ViewGroup.LayoutParams;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.RotateAnimation;
import android.widget.FrameLayout;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import org.videolan.libvlc.EventHandler;
import org.videolan.libvlc.IVideoPlayer;
import org.videolan.libvlc.LibVLC;
import org.videolan.libvlc.LibVlcException;
import org.videolan.libvlc.LibVlcUtil;
import org.videolan.libvlc.Media;
import org.videolan.vlc.audio.AudioServiceController;
import org.videolan.vlc.util.Strings;
import org.videolan.vlc.util.VLCInstance;
import org.videolan.vlc.util.WeakHandler;

import java.io.File;
import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.net.URLDecoder;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class VideoPlayerMultiActivity extends Activity implements IVideoPlayer {
    public final static String TAG = "VLC/VideoPlayerActivity";

    private final static String PLAY_FROM_VIDEOGRID = "org.videolan.vlc.gui.video.PLAY_FROM_VIDEOGRID";

    private SurfaceView mSurface;
    private SurfaceView mSubtitlesSurface;
    private SurfaceHolder mSurfaceHolder;
    private SurfaceHolder mSubtitlesSurfaceHolder;
    private FrameLayout mSurfaceFrame;
    private MediaRouter mMediaRouter;
    private MediaRouter.SimpleCallback mMediaRouterCallback;
    private SecondaryDisplay mPresentation;
    private LibVLC mLibVLC;
    private String mLocation;

    private SharedPreferences mSettings;
    /**
     * Overlay
     */
    private View mOverlayHeader;
    private View mOverlayProgress;
    private static final int OVERLAY_TIMEOUT = 4000;
    private static final int FADE_OUT = 1;
    private static final int SHOW_PROGRESS = 2;
    private static final int SURFACE_SIZE = 3;
    private static final int AUDIO_SERVICE_CONNECTION_SUCCESS = 5;
    private static final int AUDIO_SERVICE_CONNECTION_FAILED = 6;
    private static final int FADE_OUT_INFO = 4;
    private boolean mShowing;
    private int mUiVisibility = -1;
    private SeekBar mSeekbar;
    private TextView mTitle;
    private TextView mTime;
    private TextView mLength;
    private TextView mInfo;
    private ImageView mLoading;
    private TextView mLoadingText;
    private ImageButton mPlayPause;
    private boolean mEnableCloneMode;
    private boolean mDisplayRemainingTime = false;

    /**
     * For uninterrupted switching between audio and video mode
     */
    private boolean mSwitchingView;
    private boolean mCanSeek;

    // Playlist
    private int savedIndexPosition = -1;

    // size of the video
    private int mVideoHeight;
    private int mVideoWidth;
    private int mVideoVisibleHeight;
    private int mVideoVisibleWidth;
    private int mSarNum;
    private int mSarDen;

    //Volume
    private AudioManager mAudioManager;
    private OnAudioFocusChangeListener mAudioFocusListener;

    // Whether fallback from HW acceleration to SW decoding was done.
    private boolean mDisabledHardwareAcceleration = false;
    private int mPreviousHardwareAccelerationMode;

    private boolean mIsNavMenu = false;

    @Override
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (LibVlcUtil.isJellyBeanMR1OrLater()) {
            // Get the media router service (Miracast)
            mMediaRouter = (MediaRouter) getSystemService(Context.MEDIA_ROUTER_SERVICE);
            mMediaRouterCallback = new MediaRouter.SimpleCallback() {
                @Override
                public void onRoutePresentationDisplayChanged(
                        MediaRouter router, MediaRouter.RouteInfo info) {
                    Log.d(TAG, "onRoutePresentationDisplayChanged: info=" + info);
                    removePresentation();
                }
            };
            Log.d(TAG, "MediaRouter information : " + mMediaRouter.toString());
        }

        mSettings = PreferenceManager.getDefaultSharedPreferences(this);

        /* Services and miscellaneous */
        mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);

        mEnableCloneMode = mSettings.getBoolean("enable_clone_mode", false);
        createPresentation();
        setContentView(R.layout.player_multi);

        if (LibVlcUtil.isICSOrLater())
            getWindow().getDecorView().findViewById(android.R.id.content).setOnSystemUiVisibilityChangeListener(
                    new OnSystemUiVisibilityChangeListener() {
                        @Override
                        public void onSystemUiVisibilityChange(int visibility) {
                            if (visibility == mUiVisibility)
                                return;
                            setSurfaceSize(mVideoWidth, mVideoHeight, mVideoVisibleWidth, mVideoVisibleHeight, mSarNum, mSarDen);
                            if (visibility == View.SYSTEM_UI_FLAG_VISIBLE && !mShowing && !isFinishing()) {
                                showOverlay();
                            }
                            mUiVisibility = visibility;
                        }
                    }
            );

        /** initialize Views an their Events */
        mOverlayHeader = findViewById(R.id.player_overlay_header);
        mOverlayProgress = findViewById(R.id.progress_overlay);
        /* header */
        mTitle = (TextView) findViewById(R.id.player_overlay_title);
        mTime = (TextView) findViewById(R.id.player_overlay_time);
        mLength = (TextView) findViewById(R.id.player_overlay_length);
        // the info textView is not on the overlay
        mInfo = (TextView) findViewById(R.id.player_overlay_info);
        mPlayPause = (ImageButton) findViewById(R.id.player_overlay_play);
        mPlayPause.setOnClickListener(mPlayPauseListener);

        try {
            mLibVLC = VLCInstance.getLibVlcInstance();
        } catch (LibVlcException e) {
            Log.d(TAG, "LibVLC initialisation failed");
            return;
        }

        mSurface = (SurfaceView) findViewById(R.id.player_surface);
        mSurfaceHolder = mSurface.getHolder();
        mSurfaceFrame = (FrameLayout) findViewById(R.id.player_surface_frame);
        String chroma = mSettings.getString("chroma_format", "");
        if (LibVlcUtil.isGingerbreadOrLater() && chroma.equals("YV12")) {
            mSurfaceHolder.setFormat(ImageFormat.YV12);
        } else if (chroma.equals("RV16")) {
            mSurfaceHolder.setFormat(PixelFormat.RGB_565);
        } else {
            mSurfaceHolder.setFormat(PixelFormat.RGBX_8888);
        }

        mSubtitlesSurface = (SurfaceView) findViewById(R.id.subtitles_surface);
        mSubtitlesSurfaceHolder = mSubtitlesSurface.getHolder();
        mSubtitlesSurfaceHolder.setFormat(PixelFormat.RGBA_8888);
        mSubtitlesSurface.setZOrderMediaOverlay(true);
        if (mPresentation == null) {
            mSurfaceHolder.addCallback(mSurfaceCallback);
            mSubtitlesSurfaceHolder.addCallback(mSubtitlesSurfaceCallback);
        }

        mSeekbar = (SeekBar) findViewById(R.id.player_overlay_seekbar);
        /* Loading view */
        mLoading = (ImageView) findViewById(R.id.player_overlay_loading);
        mLoadingText = (TextView) findViewById(R.id.player_overlay_loading_text);
        startLoadingAnimation();

        mSwitchingView = false;

        Log.d(TAG,
                "Hardware acceleration mode: "
                        + Integer.toString(mLibVLC.getHardwareAcceleration()));

        /* Only show the subtitles surface when using "Full Acceleration" mode */
        if (mLibVLC.getHardwareAcceleration() == LibVLC.HW_ACCELERATION_FULL)
            mSubtitlesSurface.setVisibility(View.VISIBLE);
        // Signal to LibVLC that the videoPlayerActivity was created, thus the
        // SurfaceView is now available for MediaCodec direct rendering.
        mLibVLC.eventVideoPlayerActivityCreated(true);

        EventHandler em = EventHandler.getInstance();
        em.addHandler(eventHandler);

        this.setVolumeControlStream(AudioManager.STREAM_MUSIC);
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (mMediaRouter != null) {
            // Stop listening for changes to media routes.
            mediaRouterAddCallback(false);
        }

        if (mSwitchingView) {
            Log.d(TAG, "mLocation = \"" + mLocation + "\"");
            AudioServiceController.getInstance().showWithoutParse(savedIndexPosition);
            AudioServiceController.getInstance().unbindAudioService(this);
            return;
        }
        /*
         * Pausing here generates errors because the vout is constantly
         * trying to refresh itself every 80ms while the surface is not
         * accessible anymore.
         * To workaround that, we keep the last known position in the playlist
         * in savedIndexPosition to be able to restore it during onResume().
         */
        mLibVLC.stop();

        mSurface.setKeepScreenOn(false);
        AudioServiceController.getInstance().unbindAudioService(this);
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    @Override
    protected void onStop() {
        super.onStop();

        // Dismiss the presentation when the activity is not visible.
        if (mPresentation != null) {
            Log.i(TAG, "Dismissing presentation because the activity is no longer visible.");
            mPresentation.dismiss();
            mPresentation = null;
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        EventHandler em = EventHandler.getInstance();
        em.removeHandler(eventHandler);

        // MediaCodec opaque direct rendering should not be used anymore since there is no surface to attach.
        mLibVLC.eventVideoPlayerActivityCreated(false);
        // HW acceleration was temporarily disabled because of an error, restore the previous value.
        if (mDisabledHardwareAcceleration)
            mLibVLC.setHardwareAcceleration(mPreviousHardwareAccelerationMode);

        mAudioManager = null;
    }

    @Override
    protected void onResume() {
        super.onResume();
        mSwitchingView = false;
        AudioServiceController.getInstance().bindAudioService(this,
                new AudioServiceController.AudioServiceConnectionListener() {
                    @Override
                    public void onConnectionSuccess() {
                        mHandler.sendEmptyMessage(AUDIO_SERVICE_CONNECTION_SUCCESS);
                    }

                    @Override
                    public void onConnectionFailed() {
                        mHandler.sendEmptyMessage(AUDIO_SERVICE_CONNECTION_FAILED);
                    }
                });

        if (mMediaRouter != null) {
            // Listen for changes to media routes.
            mediaRouterAddCallback(true);
        }
    }

    /**
     * Add or remove MediaRouter callbacks. This is provided for version targeting.
     *
     * @param add true to add, false to remove
     */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void mediaRouterAddCallback(boolean add) {
        if (!LibVlcUtil.isJellyBeanMR1OrLater() || mMediaRouter == null) return;

        if (add)
            mMediaRouter.addCallback(MediaRouter.ROUTE_TYPE_LIVE_VIDEO, mMediaRouterCallback);
        else
            mMediaRouter.removeCallback(mMediaRouterCallback);
    }

    private void startPlayback() {
        loadMedia();
        /*
         * if the activity has been paused by pressing the power button,
         * pressing it again will show the lock screen.
         * But onResume will also be called, even if vlc-android is still in the background.
         * To workaround that, pause playback if the lockscreen is displayed
         */
    }

    public static void start(Context context, String location) {
        start(context, location, null, -1, false, false);
    }

    public static void start(Context context, String location, Boolean fromStart) {
        start(context, location, null, -1, false, fromStart);
    }

    public static void start(Context context, String location, String title, Boolean dontParse) {
        start(context, location, title, -1, dontParse, false);
    }

    public static void start(Context context, String location, String title, int position, Boolean dontParse) {
        start(context, location, title, position, dontParse, false);
    }

    public static void start(Context context, String location, String title, int position, Boolean dontParse, Boolean fromStart) {
        Intent intent = new Intent(context, VideoPlayerMultiActivity.class);
        intent.setAction(VideoPlayerMultiActivity.PLAY_FROM_VIDEOGRID);
        intent.putExtra("itemLocation", location);
        intent.putExtra("itemTitle", title);
        intent.putExtra("dontParse", dontParse);
        intent.putExtra("fromStart", fromStart);
        intent.putExtra("itemPosition", position);

        if (dontParse)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_MULTIPLE_TASK);

        context.startActivity(intent);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent event) {
        showOverlay();
        return true;
    }

    @Override
    public void onConfigurationChanged(Configuration newConfig) {
        setSurfaceSize(mVideoWidth, mVideoHeight, mVideoVisibleWidth, mVideoVisibleHeight, mSarNum, mSarDen);
        super.onConfigurationChanged(newConfig);
    }

    @Override
    public void setSurfaceSize(int width, int height, int visible_width, int visible_height, int sar_num, int sar_den) {
        if (width * height == 0)
            return;

        // store video size
        mVideoHeight = height;
        mVideoWidth = width;
        mVideoVisibleHeight = visible_height;
        mVideoVisibleWidth = visible_width;
        mSarNum = sar_num;
        mSarDen = sar_den;
        Message msg = mHandler.obtainMessage(SURFACE_SIZE);
        mHandler.sendMessage(msg);
    }

    private void fadeOutInfo() {
        if (mInfo.getVisibility() == View.VISIBLE)
            mInfo.startAnimation(AnimationUtils.loadAnimation(
                    VideoPlayerMultiActivity.this, android.R.anim.fade_out));
        mInfo.setVisibility(View.INVISIBLE);
    }

    @TargetApi(Build.VERSION_CODES.FROYO)
    private int changeAudioFocus(boolean acquire) {
        if (!LibVlcUtil.isFroyoOrLater()) // NOP if not supported
            return AudioManager.AUDIOFOCUS_REQUEST_GRANTED;

        if (mAudioFocusListener == null) {
            mAudioFocusListener = new OnAudioFocusChangeListener() {
                @Override
                public void onAudioFocusChange(int focusChange) {
                    /*
                     * Pause playback during alerts and notifications
                     */
                    switch (focusChange) {
                        case AudioManager.AUDIOFOCUS_LOSS:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK:
                            if (mLibVLC.isPlaying())
                                mLibVLC.pause();
                            break;
                        case AudioManager.AUDIOFOCUS_GAIN:
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT:
                        case AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK:
                            if (!mLibVLC.isPlaying())
                                mLibVLC.play();
                            break;
                    }
                }
            };
        }

        int result;
        if (acquire) {
            result = mAudioManager.requestAudioFocus(mAudioFocusListener,
                    AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN);
            mAudioManager.setParameters("bgm_state=true");
        } else {
            if (mAudioManager != null) {
                result = mAudioManager.abandonAudioFocus(mAudioFocusListener);
                mAudioManager.setParameters("bgm_state=false");
            } else
                result = AudioManager.AUDIOFOCUS_REQUEST_FAILED;
        }
        return result;
    }

    /**
     * Handle libvlc asynchronous events
     */
    private final Handler eventHandler = new VideoPlayerEventHandler(this);

    private static class VideoPlayerEventHandler extends WeakHandler<VideoPlayerMultiActivity> {
        public VideoPlayerEventHandler(VideoPlayerMultiActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoPlayerMultiActivity activity = getOwner();
            if (activity == null) return;
            // Do not handle events if we are leaving the VideoPlayerActivity
            if (activity.mSwitchingView) return;

            switch (msg.getData().getInt("event")) {
                case EventHandler.MediaParsedChanged:
                    Log.i(TAG, "MediaParsedChanged");
                    break;
                case EventHandler.MediaPlayerPlaying:
                    Log.i(TAG, "MediaPlayerPlaying");
                    activity.stopLoadingAnimation();
                    activity.showOverlay();
                    /** FIXME: update the track list when it changes during the
                     *  playback. (#7540) */
                    activity.changeAudioFocus(true);
                    break;
                case EventHandler.MediaPlayerPaused:
                    Log.i(TAG, "MediaPlayerPaused");
                    break;
                case EventHandler.MediaPlayerStopped:
                    Log.i(TAG, "MediaPlayerStopped");
                    activity.changeAudioFocus(false);
                    break;
                case EventHandler.MediaPlayerEndReached:
                    Log.i(TAG, "MediaPlayerEndReached");
                    activity.changeAudioFocus(false);
                    activity.endReached();
                    break;
                case EventHandler.MediaPlayerVout:
                    break;
                case EventHandler.MediaPlayerPositionChanged:
                    if (!activity.mCanSeek)
                        activity.mCanSeek = true;
                    //don't spam the logs
                    break;
                case EventHandler.MediaPlayerEncounteredError:
                    Log.i(TAG, "MediaPlayerEncounteredError");
                    activity.encounteredError();
                    break;
                case EventHandler.HardwareAccelerationError:
                    Log.i(TAG, "HardwareAccelerationError");
                    activity.handleHardwareAccelerationError();
                    break;
                case EventHandler.MediaPlayerTimeChanged:
                    // avoid useless error logs
                    break;
                default:
                    Log.e(TAG, String.format("Event not handled (0x%x)", msg.getData().getInt("event")));
                    break;
            }
            activity.updateOverlayPausePlay();
        }
    }

    /**
     * Handle resize of the surface and the overlay
     */
    private final Handler mHandler = new VideoPlayerHandler(this);

    private static class VideoPlayerHandler extends WeakHandler<VideoPlayerMultiActivity> {
        public VideoPlayerHandler(VideoPlayerMultiActivity owner) {
            super(owner);
        }

        @Override
        public void handleMessage(Message msg) {
            VideoPlayerMultiActivity activity = getOwner();
            if (activity == null) // WeakReference could be GC'ed early
                return;

            Log.i(TAG, "VideoPlayerHandler handleMessage: msg.what "+msg.what);

            switch (msg.what) {
                case FADE_OUT:
                    activity.hideOverlay(false);
                    break;
                case SHOW_PROGRESS:
                    int pos = activity.setOverlayProgress();
                    if (activity.canShowProgress()) {
                        msg = obtainMessage(SHOW_PROGRESS);
                        sendMessageDelayed(msg, 1000 - (pos % 1000));
                    }
                    break;
                case SURFACE_SIZE:
                    activity.changeSurfaceSize();
                    break;
                case FADE_OUT_INFO:
                    activity.fadeOutInfo();
                    break;
                case AUDIO_SERVICE_CONNECTION_SUCCESS:
                    activity.startPlayback();
                    break;
                case AUDIO_SERVICE_CONNECTION_FAILED:
                    activity.finish();
                    break;
            }
        }
    }

    private boolean canShowProgress() {
        return mShowing && mLibVLC.isPlaying();
    }

    private void endReached() {
        if (mLibVLC.getMediaList().expandMedia(savedIndexPosition) == 0) {
            Log.d(TAG, "Found a video playlist, expanding it");
            eventHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    loadMedia();
                }
            }, 1000);
        } else {
            /* Exit player when reaching the end */
//            finish();
        }
    }

    private void encounteredError() {
        /* Encountered Error, exit player with a message */
        AlertDialog dialog = new AlertDialog.Builder(VideoPlayerMultiActivity.this)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                })
                .setTitle(R.string.encountered_error_title)
                .setMessage(R.string.encountered_error_message)
                .create();
        dialog.show();
    }

    private void handleHardwareAccelerationError() {
        mLibVLC.stop();
        AlertDialog dialog = new AlertDialog.Builder(VideoPlayerMultiActivity.this)
                .setPositiveButton(R.string.ok, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        mDisabledHardwareAcceleration = true;
                        mPreviousHardwareAccelerationMode = mLibVLC.getHardwareAcceleration();
                        mLibVLC.setHardwareAcceleration(LibVLC.HW_ACCELERATION_DISABLED);
                        mSubtitlesSurface.setVisibility(View.INVISIBLE);
                        loadMedia();
                    }
                })
                .setNegativeButton(R.string.cancel, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int id) {
                        finish();
                    }
                })
                .setTitle(R.string.hardware_acceleration_error_title)
                .setMessage(R.string.hardware_acceleration_error_message)
                .create();
        if (!isFinishing())
            dialog.show();
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void changeSurfaceSize() {
        int sw;
        int sh;

        // get screen size
        if (mPresentation == null) {
            sw = getWindow().getDecorView().getWidth() / 2;
            sh = getWindow().getDecorView().getHeight() / 2;
        } else {
            sw = mPresentation.getWindow().getDecorView().getWidth() / 2;
            sh = mPresentation.getWindow().getDecorView().getHeight() / 2;
        }

        double dw = sw, dh = sh;
        boolean isPortrait;

        if (mPresentation == null) {
            // getWindow().getDecorView() doesn't always take orientation into account, we have to correct the values
            isPortrait = getResources().getConfiguration().orientation == Configuration.ORIENTATION_PORTRAIT;
        } else {
            isPortrait = false;
        }

        if (sw > sh && isPortrait || sw < sh && !isPortrait) {
            dw = sh;
            dh = sw;
        }

        // sanity check
        if (dw * dh == 0 || mVideoWidth * mVideoHeight == 0) {
            Log.e(TAG, "Invalid surface size");
            return;
        }

        // compute the aspect ratio
        double ar, vw;
        if (mSarDen == mSarNum) {
            /* No indication about the density, assuming 1:1 */
            vw = mVideoVisibleWidth;
            ar = (double) mVideoVisibleWidth / (double) mVideoVisibleHeight;
        } else {
            /* Use the specified aspect ratio */
            vw = mVideoVisibleWidth * (double) mSarNum / mSarDen;
            ar = vw / mVideoVisibleHeight;
        }

        // compute the display aspect ratio
        double dar = dw / dh;
        if (dar < ar)
            dh = dw / ar;
        else
            dw = dh * ar;

        SurfaceView surface;
        SurfaceView subtitlesSurface;
        SurfaceHolder surfaceHolder;
        SurfaceHolder subtitlesSurfaceHolder;
        FrameLayout surfaceFrame;

        if (mPresentation == null) {
            surface = mSurface;
            subtitlesSurface = mSubtitlesSurface;
            surfaceHolder = mSurfaceHolder;
            subtitlesSurfaceHolder = mSubtitlesSurfaceHolder;
            surfaceFrame = mSurfaceFrame;
        } else {
            surface = mPresentation.mSurface;
            subtitlesSurface = mPresentation.mSubtitlesSurface;
            surfaceHolder = mPresentation.mSurfaceHolder;
            subtitlesSurfaceHolder = mPresentation.mSubtitlesSurfaceHolder;
            surfaceFrame = mPresentation.mSurfaceFrame;
        }

        // force surface buffer size
        surfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);
        subtitlesSurfaceHolder.setFixedSize(mVideoWidth, mVideoHeight);

        // set display size
        LayoutParams lp = surface.getLayoutParams();
        lp.width = (int) Math.ceil(dw * mVideoWidth / mVideoVisibleWidth);
        lp.height = (int) Math.ceil(dh * mVideoHeight / mVideoVisibleHeight);
        surface.setLayoutParams(lp);
        subtitlesSurface.setLayoutParams(lp);

        // set frame size (crop if necessary)
        lp = surfaceFrame.getLayoutParams();
        lp.width = (int) Math.floor(dw);
        lp.height = (int) Math.floor(dh);
        surfaceFrame.setLayoutParams(lp);

        surface.invalidate();
        subtitlesSurface.invalidate();
    }

    /**
     * show/hide the overlay
     */

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        // locked, only handle show/hide & ignore all actions
        if (event.getAction() == MotionEvent.ACTION_UP) {
            if (!mShowing) {
                showOverlay();
            } else {
                hideOverlay(true);
            }
        }
        return false;
    }

    /**
     *
     */
    private final OnClickListener mPlayPauseListener = new OnClickListener() {
        @Override
        public void onClick(View v) {
            if (mLibVLC.isPlaying())
                pause();
            else
                play();
            showOverlay();
        }
    };

    /**
     * attach and disattach surface to the lib
     */
    private final Callback mSurfaceCallback = new Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (format == PixelFormat.RGBX_8888)
                Log.d(TAG, "Pixel format is RGBX_8888");
            else if (format == PixelFormat.RGB_565)
                Log.d(TAG, "Pixel format is RGB_565");
            else if (format == ImageFormat.YV12)
                Log.d(TAG, "Pixel format is YV12");
            else
                Log.d(TAG, "Pixel format is other/unknown");
            if (mLibVLC != null)
                mLibVLC.attachSurface(holder.getSurface(), VideoPlayerMultiActivity.this);
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (mLibVLC != null)
                mLibVLC.detachSurface();
        }
    };

    private final Callback mSubtitlesSurfaceCallback = new Callback() {
        @Override
        public void surfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            if (mLibVLC != null)
                mLibVLC.attachSubtitlesSurface(holder.getSurface());
        }

        @Override
        public void surfaceCreated(SurfaceHolder holder) {
        }

        @Override
        public void surfaceDestroyed(SurfaceHolder holder) {
            if (mLibVLC != null)
                mLibVLC.detachSubtitlesSurface();
        }
    };

    /**
     * show overlay the the default timeout
     */
    private void showOverlay() {
        showOverlay(OVERLAY_TIMEOUT);
    }

    /**
     * show overlay
     */
    private void showOverlay(int timeout) {
        if (mIsNavMenu)
            return;
        mHandler.sendEmptyMessage(SHOW_PROGRESS);
        if (!mShowing) {
            mShowing = true;
            mOverlayHeader.setVisibility(View.VISIBLE);
            mPlayPause.setVisibility(View.VISIBLE);
            mOverlayProgress.setVisibility(View.VISIBLE);
        }
        Message msg = mHandler.obtainMessage(FADE_OUT);
        if (timeout != 0) {
            mHandler.removeMessages(FADE_OUT);
            mHandler.sendMessageDelayed(msg, timeout);
        }
        updateOverlayPausePlay();
    }

    /**
     * hider overlay
     */
    private void hideOverlay(boolean fromUser) {
        if (mShowing) {
            mHandler.removeMessages(SHOW_PROGRESS);
            Log.i(TAG, "remove View!");
            if (!fromUser) {
                mOverlayHeader.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mOverlayProgress.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
                mPlayPause.startAnimation(AnimationUtils.loadAnimation(this, android.R.anim.fade_out));
            }
            mOverlayHeader.setVisibility(View.INVISIBLE);
            mOverlayProgress.setVisibility(View.INVISIBLE);
            mPlayPause.setVisibility(View.INVISIBLE);
            mShowing = false;
        }
    }

    private void updateOverlayPausePlay() {
        if (mLibVLC == null)
            return;

        if (mPresentation == null)
            mPlayPause.setBackgroundResource(mLibVLC.isPlaying() ? R.drawable.ic_pause_circle
                    : R.drawable.ic_play_circle);
        else
            mPlayPause.setBackgroundResource(mLibVLC.isPlaying() ? R.drawable.ic_pause_circle_big_o
                    : R.drawable.ic_play_circle_big_o);
    }

    /**
     * update the overlay
     */
    private int setOverlayProgress() {
        if (mLibVLC == null) {
            return 0;
        }
        int time = (int) mLibVLC.getTime();
        int length = (int) mLibVLC.getLength();

        // Update all view elements
        mSeekbar.setMax(length);
        mSeekbar.setProgress(time);
        if (time >= 0) mTime.setText(Strings.millisToString(time));
        if (length >= 0) mLength.setText(mDisplayRemainingTime && length > 0
                ? "- " + Strings.millisToString(length - time)
                : Strings.millisToString(length));
        return time;
    }

    /**
     *
     */
    private void play() {
        mLibVLC.play();
        mSurface.setKeepScreenOn(true);
        new Handler().postDelayed(new Runnable() {

            @Override
            public void run() {
                // TODO Auto-generated method stub
                takeSnapShot();
            }

        }, 300);
    }

    /**
     *
     */
    private void pause() {
        mLibVLC.pause();
        mSurface.setKeepScreenOn(false);
    }

    private void takeSnapShot() {
        try {
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss");
            String name = df.format(new Date());
            name = "/sdcard/aaa/capture/" + name + ".png";
            File file = new File(name);
            if (!file.exists())
                file.createNewFile();
            if (mLibVLC.takeSnapShot(name, 640, 360)) {
                Toast.makeText(getApplicationContext(), "已保存", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(getApplicationContext(), "截图失败", Toast.LENGTH_SHORT).show();
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * External extras:
     * - position (long) - position of the video to start with (in ms)
     */
    @SuppressWarnings({"unchecked"})
    private void loadMedia() {
        mLocation = null;
        String title = getResources().getString(R.string.title);
        boolean dontParse = false;
        String itemTitle = null;
        int itemPosition = -1; // Index in the media list as passed by AudioServer (used only for vout transition internally)
        long intentPosition = -1; // position passed in by intent (ms)

        if (getIntent().getAction() != null
                && getIntent().getAction().equals(Intent.ACTION_VIEW)) {
            /* Started from external application 'content' */
            if (getIntent().getData() != null
                    && getIntent().getData().getScheme() != null
                    && getIntent().getData().getScheme().equals("content")) {
                mLocation = getIntent().getData().getPath();
            } /* External application */ else if (getIntent().getDataString() != null) {
                // Plain URI
                mLocation = getIntent().getDataString();
                // Remove VLC prefix if needed
                if (mLocation.startsWith("vlc://")) {
                    mLocation = mLocation.substring(6);
                }
                // Decode URI
                if (!mLocation.contains("/")) {
                    try {
                        mLocation = URLDecoder.decode(mLocation, "UTF-8");
                    } catch (UnsupportedEncodingException e) {
                        Log.w(TAG, "UnsupportedEncodingException while decoding MRL " + mLocation);
                    }
                }
            } else {
                Log.e(TAG, "Couldn't understand the intent");
                encounteredError();
            }

            // Try to get the position
            if (getIntent().getExtras() != null)
                intentPosition = getIntent().getExtras().getLong("position", -1);
        } /* ACTION_VIEW */
        /* Started from VideoListActivity */
        else if (getIntent().getAction() != null
                && getIntent().getAction().equals(PLAY_FROM_VIDEOGRID)
                && getIntent().getExtras() != null) {
            mLocation = getIntent().getExtras().getString("itemLocation");
            itemTitle = getIntent().getExtras().getString("itemTitle");
            dontParse = getIntent().getExtras().getBoolean("dontParse");
            itemPosition = getIntent().getExtras().getInt("itemPosition", -1);
        }

        mSurface.setKeepScreenOn(true);

        if (mLibVLC == null)
            return;

        /* WARNING: hack to avoid a crash in mediacodec on KitKat.
         * Disable hardware acceleration if the media has a ts extension. */
        if (mLocation != null && LibVlcUtil.isKitKatOrLater()) {
            String locationLC = mLocation.toLowerCase(Locale.ENGLISH);
            if (locationLC.endsWith(".ts")
                    || locationLC.endsWith(".tts")
                    || locationLC.endsWith(".m2t")
                    || locationLC.endsWith(".mts")
                    || locationLC.endsWith(".m2ts")) {
                mDisabledHardwareAcceleration = true;
                mPreviousHardwareAccelerationMode = mLibVLC.getHardwareAcceleration();
                mLibVLC.setHardwareAcceleration(LibVLC.HW_ACCELERATION_DISABLED);
            }
        }

        /* Start / resume playback */
        if (dontParse && itemPosition >= 0) {
            // Provided externally from AudioService
            Log.d(TAG, "Continuing playback from AudioService at index " + itemPosition);
            savedIndexPosition = itemPosition;
            if (!mLibVLC.isPlaying()) {
                // AudioService-transitioned playback for item after sleep and resume
                mLibVLC.playIndex(savedIndexPosition);
                dontParse = false;
            } else {
                stopLoadingAnimation();
                showOverlay();
            }
        } else if (savedIndexPosition > -1) {
            AudioServiceController.getInstance().stop(); // Stop the previous playback.
            mLibVLC.setMediaList();
            mLibVLC.playIndex(savedIndexPosition);
        } else if (mLocation != null && mLocation.length() > 0 && !dontParse) {
            AudioServiceController.getInstance().stop(); // Stop the previous playback.
            mLibVLC.setMediaList();
            mLibVLC.getMediaList().add(new Media(mLibVLC, mLocation));
            savedIndexPosition = mLibVLC.getMediaList().size() - 1;
            mLibVLC.playIndex(savedIndexPosition);
        }
        mCanSeek = false;

        if (mLocation != null && mLocation.length() > 0 && !dontParse) {
            // restore last position
            // Get the title
            try {
                title = URLDecoder.decode(mLocation, "UTF-8");
            } catch (UnsupportedEncodingException e) {
            } catch (IllegalArgumentException e) {
            }
            if (title.startsWith("file:")) {
                title = new File(title).getName();
                int dotIndex = title.lastIndexOf('.');
                if (dotIndex != -1)
                    title = title.substring(0, dotIndex);
            }
        } else if (itemTitle != null) {
            title = itemTitle;
        }
        mTitle.setText(title);
    }

    @SuppressWarnings("deprecation")
    private int getScreenRotation() {
        WindowManager wm = (WindowManager) getSystemService(Context.WINDOW_SERVICE);
        Display display = wm.getDefaultDisplay();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.FROYO /* Android 2.2 has getRotation */) {
            try {
                Method m = display.getClass().getDeclaredMethod("getRotation");
                return (Integer) m.invoke(display);
            } catch (Exception e) {
                return Surface.ROTATION_0;
            }
        } else {
            return display.getOrientation();
        }
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void createPresentation() {
        if (mMediaRouter == null || mEnableCloneMode)
            return;

        // Get the current route and its presentation display.
        MediaRouter.RouteInfo route = mMediaRouter.getSelectedRoute(
                MediaRouter.ROUTE_TYPE_LIVE_VIDEO);

        Display presentationDisplay = route != null ? route.getPresentationDisplay() : null;

        if (presentationDisplay != null) {
            // Show a new presentation if possible.
            Log.i(TAG, "Showing presentation on display: " + presentationDisplay);
            mPresentation = new SecondaryDisplay(this, presentationDisplay);
            mPresentation.setOnDismissListener(mOnDismissListener);
            try {
                mPresentation.show();
            } catch (WindowManager.InvalidDisplayException ex) {
                Log.w(TAG, "Couldn't show presentation!  Display was removed in "
                        + "the meantime.", ex);
                mPresentation = null;
            }
        } else
            Log.i(TAG, "No secondary display detected");
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private void removePresentation() {
        if (mMediaRouter == null)
            return;

        // Dismiss the current presentation if the display has changed.
        Log.i(TAG, "Dismissing presentation because the current route no longer "
                + "has a presentation display.");
        mLibVLC.pause(); // Stop sending frames to avoid a crash.
        finish(); //TODO restore the video on the new display instead of closing
        if (mPresentation != null) mPresentation.dismiss();
        mPresentation = null;
    }

    /**
     * Listens for when presentations are dismissed.
     */
    private final DialogInterface.OnDismissListener mOnDismissListener = new DialogInterface.OnDismissListener() {
        @Override
        public void onDismiss(DialogInterface dialog) {
            if (dialog == mPresentation) {
                Log.i(TAG, "Presentation was dismissed.");
                mPresentation = null;
            }
        }
    };

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
    private final class SecondaryDisplay extends Presentation {
        public final static String TAG = "VLC/SecondaryDisplay";

        private SurfaceView mSurface;
        private SurfaceView mSubtitlesSurface;
        private SurfaceHolder mSurfaceHolder;
        private SurfaceHolder mSubtitlesSurfaceHolder;
        private FrameLayout mSurfaceFrame;
        private LibVLC mLibVLC;

        public SecondaryDisplay(Context context, Display display) {
            super(context, display);
            if (context instanceof Activity) {
                setOwnerActivity((Activity) context);
            }
            try {
                mLibVLC = VLCInstance.getLibVlcInstance();
            } catch (LibVlcException e) {
                Log.d(TAG, "LibVLC initialisation failed");
                return;
            }
        }

        @Override
        protected void onCreate(Bundle savedInstanceState) {
            super.onCreate(savedInstanceState);
            setContentView(R.layout.player_remote);

            mSurface = (SurfaceView) findViewById(R.id.remote_player_surface);
            mSurfaceHolder = mSurface.getHolder();
            mSurfaceFrame = (FrameLayout) findViewById(R.id.remote_player_surface_frame);
            String chroma = mSettings.getString("chroma_format", "");
            if (LibVlcUtil.isGingerbreadOrLater() && chroma.equals("YV12")) {
                mSurfaceHolder.setFormat(ImageFormat.YV12);
            } else if (chroma.equals("RV16")) {
                mSurfaceHolder.setFormat(PixelFormat.RGB_565);
            } else {
                mSurfaceHolder.setFormat(PixelFormat.RGBX_8888);
            }

            VideoPlayerMultiActivity activity = (VideoPlayerMultiActivity) getOwnerActivity();
            if (activity == null) {
                Log.e(TAG, "Failed to get the VideoPlayerActivity instance, secondary display won't work");
                return;
            }

            mSurfaceHolder.addCallback(activity.mSurfaceCallback);

            mSubtitlesSurface = (SurfaceView) findViewById(R.id.remote_subtitles_surface);
            mSubtitlesSurfaceHolder = mSubtitlesSurface.getHolder();
            mSubtitlesSurfaceHolder.setFormat(PixelFormat.RGBA_8888);
            mSubtitlesSurface.setZOrderMediaOverlay(true);
            mSubtitlesSurfaceHolder.addCallback(activity.mSubtitlesSurfaceCallback);

            /* Only show the subtitles surface when using "Full Acceleration" mode */
            if (mLibVLC != null && mLibVLC.getHardwareAcceleration() == LibVLC.HW_ACCELERATION_FULL)
                mSubtitlesSurface.setVisibility(View.VISIBLE);
            Log.i(TAG, "Secondary display created");
        }
    }

    /**
     * Start the video loading animation.
     */
    private void startLoadingAnimation() {
        AnimationSet anim = new AnimationSet(true);
        RotateAnimation rotate = new RotateAnimation(0f, 360f, Animation.RELATIVE_TO_SELF, 0.5f, Animation.RELATIVE_TO_SELF, 0.5f);
        rotate.setDuration(800);
        rotate.setInterpolator(new DecelerateInterpolator());
        rotate.setRepeatCount(RotateAnimation.INFINITE);
        anim.addAnimation(rotate);
        mLoading.startAnimation(anim);
        mLoadingText.setVisibility(View.VISIBLE);
    }

    /**
     * Stop the video loading animation.
     */
    private void stopLoadingAnimation() {
        mLoading.setVisibility(View.INVISIBLE);
        mLoading.clearAnimation();
        mLoadingText.setVisibility(View.GONE);
    }
}
