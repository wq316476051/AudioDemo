package com.wang.review1.client;

import android.app.Activity;
import android.content.ComponentName;
import android.media.session.MediaController;
import android.os.Bundle;
import android.os.RemoteException;
import android.support.annotation.NonNull;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.session.MediaControllerCompat;
import android.support.v4.media.session.PlaybackStateCompat;
import android.util.Log;
import android.widget.Button;

import com.wang.review1.R;
import com.wang.review1.server.PlaybackService;

import java.util.List;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private Button mBtnPlay, mBtnPause, mBtnStop;
    private MediaBrowserCompat mMediaBrowser;
    private ComponentName mPlaybackServiceCompontent;
    private MediaControllerCompat mMediaController;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        Log.d(TAG, "onCreate: ");

        mBtnPlay = findViewById(R.id.btn_play);
        mBtnPause = findViewById(R.id.btn_pause);
        mBtnStop = findViewById(R.id.btn_stop);

        mPlaybackServiceCompontent = new ComponentName(this, PlaybackService.class);
        mMediaBrowser = new MediaBrowserCompat(this, mPlaybackServiceCompontent, mConnectionCallback, null);

        mBtnPlay.setOnClickListener(view -> {mMediaController.getTransportControls().play();});
        mBtnPause.setOnClickListener(view -> {mMediaController.getTransportControls().pause();});
        mBtnStop.setOnClickListener(view -> {mMediaController.getTransportControls().stop();});
    }

    @Override
    protected void onStart() {
        super.onStart();
        Log.d(TAG, "onStart: ");
        mMediaBrowser.connect();
    }

    @Override
    protected void onStop() {
        super.onStop();
        Log.d(TAG, "onStop: ");
        mMediaBrowser.disconnect();
    }

    private MediaBrowserCompat.ConnectionCallback mConnectionCallback = new MediaBrowserCompat.ConnectionCallback() {
        @Override
        public void onConnected() {
            Log.d(TAG, "onConnected: ");
            try {
                mMediaController = new MediaControllerCompat(MainActivity.this, mMediaBrowser.getSessionToken());
                PlaybackStateCompat playbackState = mMediaController.getPlaybackState();
                MediaControllerCompat.PlaybackInfo playbackInfo = mMediaController.getPlaybackInfo();
            } catch (RemoteException e) {
                e.printStackTrace();
            }
            mMediaBrowser.subscribe("root", new MediaBrowserCompat.SubscriptionCallback() {
                @Override
                public void onChildrenLoaded(@NonNull String parentId, @NonNull List<MediaBrowserCompat.MediaItem> children) {
                    Log.d(TAG, "onChildrenLoaded: ");
                }
            });
        }
    };
}
