package com.wang.review1.server;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.media.AudioManager;
import android.util.Log;

/**
 * 处理 Audio Focus 和 耳机插入
 */
public abstract class PlayerBase {

    private static final String TAG = "PlayerBase";

    private static final IntentFilter AUDIO_NOISY_FILTER =
            new IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY);

    private static final float MEDIA_VOLUME_DEFAULT = 1.0f;
    private static final float MEDIA_VOLUME_DUCK = 0.2f;

    protected Context mApplicationContext;
    private AudioManager mAudioManager;
    private AudioFocusHelper mAudioFocusHelper;

    private boolean mAudioNoisyReceiverRegistered = false;

    public PlayerBase(Context context) {
        mApplicationContext = context.getApplicationContext();
        mAudioManager = (AudioManager) mApplicationContext.getSystemService(Context.AUDIO_SERVICE);
        mAudioFocusHelper = new AudioFocusHelper();
    }

    protected abstract boolean isPlaying();
    protected abstract void onPlay();
    protected abstract void onPause();
    protected abstract void onStop();
    protected abstract void setVolume(float volume);

    public void play() {
        Log.d(TAG, "play: ");
        if (mAudioFocusHelper.requestAudioFocus()) {
            registerAudioNoisyReceiver();
            onPlay();
        }
    }

    public void pause() {
        Log.d(TAG, "pause: ");
        mAudioFocusHelper.abandonAudioFocus();
        onPause();
    }

    public void stop() {
        Log.d(TAG, "stop: ");
        mAudioFocusHelper.abandonAudioFocus();
        unregisterAudioNoisyReceiver();
        onStop();
    }

    protected void registerAudioNoisyReceiver() {
        Log.d(TAG, "registerAudioNoisyReceiver: ");
        if (!mAudioNoisyReceiverRegistered) {
            mApplicationContext.registerReceiver(mBecomingNoisyReceiver, AUDIO_NOISY_FILTER);
        }
        mAudioNoisyReceiverRegistered = true;
    }

    protected void unregisterAudioNoisyReceiver() {
        Log.d(TAG, "unregisterAudioNoisyReceiver: ");
        if (mAudioNoisyReceiverRegistered) {
            mApplicationContext.unregisterReceiver(mBecomingNoisyReceiver);
        }
        mAudioNoisyReceiverRegistered = false;
    }

    private BroadcastReceiver mBecomingNoisyReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (AudioManager.ACTION_AUDIO_BECOMING_NOISY.equals(intent.getAction())) {
                Log.d(TAG, "onReceive: ACTION_AUDIO_BECOMING_NOISY");
                if (isPlaying()) {
                    pause();
                }
            }
        }
    };

    private final class AudioFocusHelper implements AudioManager.OnAudioFocusChangeListener {

        public boolean requestAudioFocus() {
            return mAudioManager.requestAudioFocus(this, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN)
                    == AudioManager.AUDIOFOCUS_REQUEST_GRANTED;
        }

        public void abandonAudioFocus() {
            mAudioManager.abandonAudioFocus(this);
        }

        @Override
        public void onAudioFocusChange(int focusChange) {
            switch (focusChange) {
                case AudioManager.AUDIOFOCUS_GAIN:
                    Log.d(TAG, "onAudioFocusChange: AUDIOFOCUS_GAIN");
                    // TODO 播放
                    if (isPlaying()) {
                        setVolume(MEDIA_VOLUME_DEFAULT);
                    } else {
                        play();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK: // transient 短暂的。duck 闪避
                    Log.d(TAG, "onAudioFocusChange: AUDIOFOCUS_LOSS_TRANSIENT_CAN_DUCK");
                    // TODO 调节音量
                    setVolume(MEDIA_VOLUME_DUCK);
                    break;
                case AudioManager.AUDIOFOCUS_LOSS_TRANSIENT: // transient 短暂的
                    Log.d(TAG, "onAudioFocusChange: AUDIOFOCUS_LOSS_TRANSIENT");
                    // TODO 暂停
                    if (isPlaying()) {
                        pause();
                    }
                    break;
                case AudioManager.AUDIOFOCUS_LOSS:
                    Log.d(TAG, "onAudioFocusChange: AUDIOFOCUS_LOSS");
                    // TODO 停止
                    stop();
                    // TODO: 2018/10/28 也应该暂停，看需求
//                    if (isPlaying()) {
//                        pause();
//                    }
                    break;
            }
        }
    }
}
