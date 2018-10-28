package com.wang.review1.server;

import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.media.MediaBrowserCompat;
import android.support.v4.media.MediaBrowserServiceCompat;
import android.support.v4.media.MediaDescriptionCompat;
import android.support.v4.media.session.MediaSessionCompat;
import android.util.Log;

import com.wang.review1.MusicRepo;

import java.util.List;

public class PlaybackService extends MediaBrowserServiceCompat {

    private static final String TAG = "PlaybackService";

    private MediaSessionCompat mMediaSession;
    private HandlerThread mPlaybackThread;
    private MusicPlayer mMusicPlayer;

    @Override
    public void onCreate() {
        super.onCreate();
        mPlaybackThread = new HandlerThread("playback");
        mPlaybackThread.start();

        mMediaSession = new MediaSessionCompat(this, "playback");
        mMediaSession.setFlags(MediaSessionCompat.FLAG_HANDLES_TRANSPORT_CONTROLS);
        mMediaSession.setCallback(mSessionCallback, new Handler(mPlaybackThread.getLooper()));

        setSessionToken(mMediaSession.getSessionToken());

        mMusicPlayer = new MusicPlayer(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        mPlaybackThread.quit();
    }

    @Nullable
    @Override
    public BrowserRoot onGetRoot(@NonNull String s, int i, @Nullable Bundle bundle) {
        return new BrowserRoot("root", null);
    }

    @Override
    public void onLoadChildren(@NonNull String s, @NonNull Result<List<MediaBrowserCompat.MediaItem>> result) {
        result.sendResult(MusicRepo.getMediaItems());
    }

    private MediaSessionCompat.Callback mSessionCallback = new MediaSessionCompat.Callback() {

        @Override
        public void onAddQueueItem(MediaDescriptionCompat description) {
            Log.d(TAG, "onAddQueueItem: ");
        }

        @Override
        public void onRemoveQueueItem(MediaDescriptionCompat description) {
            Log.d(TAG, "onRemoveQueueItem: ");
        }

        @Override
        public void onPlay() {
            Log.d(TAG, "onPlay: ");
            mMusicPlayer.play();
        }

        @Override
        public void onPause() {
            Log.d(TAG, "onPause: ");
            mMusicPlayer.pause();
        }

        @Override
        public void onStop() {
            Log.d(TAG, "onStop: ");
            mMusicPlayer.stop();
        }
    };
}
