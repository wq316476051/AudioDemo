package com.wang.audiodemo;

import android.app.Activity;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.ProgressBar;
import android.widget.TextView;

import java.io.IOException;
import java.nio.ByteBuffer;

public class MainActivity extends Activity implements View.OnClickListener {

    private static final String TAG = "MainActivity";

    private ProgressBar mProgressVolume, mProgressDuration;
    private TextView mTvVolume, mTvDurationPercentage;
    private Button mBtnVolumeUp, mBtnVolumeDown;
    private Button mBtnPlay, mBtnPause, mBtnStop;
    private AudioPlayer mAudioPlayer;
    private long larger;
    private AudioManager mAudioManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        Log.d(TAG, "onCreate: ");

        mProgressVolume = findViewById(R.id.progress_volume);
        mProgressDuration = findViewById(R.id.progress_duration);

        mTvVolume = findViewById(R.id.tv_volume);
        mTvDurationPercentage = findViewById(R.id.tv_duration_percentage);

        mBtnVolumeUp = findViewById(R.id.btn_volume_up);
        mBtnVolumeDown = findViewById(R.id.btn_volume_down);

        mBtnPlay = findViewById(R.id.btn_play);
        mBtnPause = findViewById(R.id.btn_pause);
        mBtnStop = findViewById(R.id.btn_stop);

        mAudioPlayer = new AudioPlayer();
        try {
            mAudioPlayer.setDataSource(getAssets().openFd("LaLaLoveOnMyMind.mp3"));
            mAudioPlayer.setCallback(new AudioPlayer.Callback() {
                @Override
                public void onLoudlessChange(final long loudless, final long max) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            if (loudless > larger) {
                                larger = loudless;
                                Log.d(TAG, "onLoudlessChange: " + Thread.currentThread());
                            }
                            mProgressVolume.setMax((int) max);
                            mProgressVolume.setProgress((int) (loudless));
                            mTvVolume.setText("current = " + loudless + "; larger = " + larger + "; max = " + max);
                        }
                    });
                }

                @Override
                public void onPlayProgressChange(long progress, long max) {
                    mProgressDuration.setMax((int) max);
                    mProgressDuration.setProgress((int) (progress));
                }
            });
        } catch (IOException e) {
            e.printStackTrace();
        }

        mBtnVolumeUp.setOnClickListener(this);
        mBtnVolumeDown.setOnClickListener(this);
        mBtnPlay.setOnClickListener(this);
        mBtnPause.setOnClickListener(this);
        mBtnStop.setOnClickListener(this);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mAudioPlayer.release();
        mAudioPlayer = null;
    }

    @Override
    public void onClick(View v) {
        switch (v.getId()) {
            case R.id.btn_play:
                mAudioPlayer.play();
                break;
            case R.id.btn_pause:
                if (mAudioPlayer.isPlaying()) {
                    mAudioPlayer.pause();
                }
                break;
            case R.id.btn_stop:
                if (mAudioPlayer.isPlaying() || mAudioPlayer.isPaused()) {
                    mAudioPlayer.stop();
                }
                break;
            case R.id.btn_volume_up:
                Log.d(TAG, "onClick: btn_volume_up");
//                setVolumeControlStream(AudioManager.STREAM_MUSIC);

                mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                mAudioManager.adjustVolume(AudioManager.ADJUST_RAISE, 0);
                break;
            case R.id.btn_volume_down:
                Log.d(TAG, "onClick: btn_volume_down");
//                setVolumeControlStream(AudioManager.STREAM_VOICE_CALL);

                mAudioManager = (AudioManager) getSystemService(AUDIO_SERVICE);
                mAudioManager.adjustVolume(AudioManager.ADJUST_LOWER, 0);

//                new Handler().postDelayed(new Runnable() {
//                    @Override
//                    public void run() {
//                        mAudioManager.playSoundEffect(AudioManager.FX_KEYPRESS_DELETE);
//                    }
//                }, 3000);
                break;
        }
    }

    /**
     * 从头到尾，完整流程。
     */
    private void playMusic() {
        try {
            // 创建 MediaExtractor
            Log.d(TAG, "onCreate: 创建 MediaExtractor");
            MediaExtractor extractor = new MediaExtractor();        // 1. 数据提取器
            extractor.setDataSource(getAssets().openFd("LaLaLoveOnMyMind.mp3"));    // 2. 设置被提取的数据源
            int trackCount = extractor.getTrackCount();             // 3. 获取音轨数量
            Log.d(TAG, "onCreate: trackCount = " + trackCount);
            for (int index = 0; index < trackCount; index++) {
                MediaFormat mediaFormat = extractor.getTrackFormat(index);      // 4. 获取某个音轨的媒体格式
                String mimetype = mediaFormat.getString(MediaFormat.KEY_MIME); // 5. 获取 Mimetype

                Log.d(TAG, "onCreate: mimetype = " + mimetype);

                if (mimetype.startsWith("audio")) {
                    // 确定音轨
                    Log.d(TAG, "onCreate: 确定音轨");
                    extractor.selectTrack(index);       // 6. 选中指定的音轨，为后续调用绑定音轨
                    // 创建 MediaCodec
                    Log.d(TAG, "onCreate: 创建 MediaCodec");
                    MediaCodec audioDecoder = MediaCodec.createDecoderByType(mimetype);         // 7. 创建 MediaCodec
                    audioDecoder.configure(mediaFormat, null, null, 0); // 8. 配置 MediaCodec
                    audioDecoder.start();                                                      // 9. 开始解码
                    Log.d(TAG, "onCreate: MediaCodec started");

                    // 创建 AudioTrack
                    Log.d(TAG, "onCreate: 创建 AudioTrack");
                    int streamType = AudioManager.STREAM_MUSIC;
                    int sampleRateInHz = mediaFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE);
                    int channelConfig = mediaFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT) == 1 ?
                            AudioFormat.CHANNEL_OUT_MONO : AudioFormat.CHANNEL_OUT_STEREO;
                    int audioFormat = AudioFormat.ENCODING_PCM_16BIT;
                    int bufferSizeInBytes = AudioTrack.getMinBufferSize(sampleRateInHz, channelConfig, audioFormat);    // 10. 计算最小缓冲区
                    Log.d(TAG, "onCreate: sampleRateInHz = " + sampleRateInHz);
                    Log.d(TAG, "onCreate: bufferSizeInBytes = " + bufferSizeInBytes);
                    int mode = AudioTrack.MODE_STREAM;
                    AudioTrack audioTrack = new AudioTrack(streamType, sampleRateInHz,          // 11. 创建 AudioTrack
                            channelConfig, audioFormat, bufferSizeInBytes, mode);
                    audioTrack.play();                                                          // 12. 开始播放
                    Log.d(TAG, "onCreate: AudioTrack played");



                    byte[] tempBuffer = new byte[1024];
                    MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();     // 13. 在后续调用中，被填充数据。单次大小、累计时间、结束标记
                    long startMs = System.currentTimeMillis();
                    while (true) {
                        // 写数据
                        Log.d(TAG, "onCreate: 写数据");
                        int inputBufferId = audioDecoder.dequeueInputBuffer(1000 * 10/* 10 毫秒 */);  // 14. 获取向 MediaCodec 写数据的缓冲区
                        Log.d(TAG, "onCreate: inputBufferId = " + inputBufferId);
                        if (inputBufferId >= 0) {
                            ByteBuffer inputBuffer = audioDecoder.getInputBuffer(inputBufferId);    // 15. 获取向 MediaCodec 写数据的缓冲区

                            int sampleSize = extractor.readSampleData(inputBuffer, 0);      // 16. MediaExtractor 从文件中获取编码的音频数据
                            long sampleTime = extractor.getSampleTime();
                            Log.d(TAG, "onCreate: sampleSize = " + sampleSize);
                            Log.d(TAG, "onCreate: sampleTime = " + sampleTime);
                            if (sampleSize > 0) {
                                audioDecoder.queueInputBuffer(inputBufferId, 0, sampleSize, sampleTime, 0); // 17. 将编码的音频数据传递给 MediaCodec 解码
                                extractor.advance();    // 18. 非常容易被忽略；MediaExtractor 进入下一次提取数据
                            } else {
                                audioDecoder.queueInputBuffer(inputBufferId, 0, 0, 0, MediaCodec.BUFFER_FLAG_END_OF_STREAM); // 19. 数据已被提取完
                            }
                        }

                        // 读数据
                        Log.d(TAG, "onCreate: 读数据");
                        int outputBufferId = audioDecoder.dequeueOutputBuffer(bufferInfo, 1000 * 10/* 10 毫秒 */); // 20. 从 MediaCodec 中获取已解码的数据
                        Log.d(TAG, "onCreate: outputBufferId = " + outputBufferId);
                        if (outputBufferId >= 0) {
                            ByteBuffer outputBuffer = audioDecoder.getOutputBuffer(outputBufferId);      // 21. 从 MediaCodec 中获取已解码的数据
                            if (tempBuffer.length < bufferInfo.size) {
                                tempBuffer = new byte[bufferInfo.size];
                            }
                            outputBuffer.position(0);
                            outputBuffer.get(tempBuffer, 0, bufferInfo.size);           // 22. 将 ByteBuffer 转成 byte[]
                            outputBuffer.clear();

                            Log.d(TAG, "onCreate: 使用数据 size = " + bufferInfo.size);
                            audioTrack.write(tempBuffer, 0, bufferInfo.size);    // 23. AudioTrack 播放音乐
                            audioDecoder.releaseOutputBuffer(outputBufferId, false);   // 24. 释放缓冲区

                            // 等待
                            Log.d(TAG, "onCreate: 延时等待，如果缓冲区里的可展示时间 > 当前视频播放的进度，就休眠一下");
                            while (bufferInfo.presentationTimeUs / 1000 > System.currentTimeMillis() - startMs + 10/*a little more*/) {     // 25. 延迟，避免渲染过快
                                long presentationTime = bufferInfo.presentationTimeUs / 1000;
                                long presentedTime = System.currentTimeMillis() - startMs;
                                Log.d(TAG, "onCreate: presentationTimeUs = " + presentationTime);
                                Log.d(TAG, "onCreate: presentedTime = " + presentedTime);
                                try {
                                    Thread.sleep(10);
                                } catch (InterruptedException e) {
                                    e.printStackTrace();
                                }
                            }
                        } else if (outputBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                            Log.d(TAG, "onCreate: INFO_OUTPUT_FORMAT_CHANGED");
                        } else if (outputBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            Log.d(TAG, "onCreate: INFO_TRY_AGAIN_LATER");
                        }

                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {   // 26. 提取文件到末尾，退出循环
                            Log.d(TAG, "buffer stream end");
                            break;
                        }
                    }



                    audioTrack.stop();          // 27. 释放 AudioTrack
                    audioTrack.release();
                    audioTrack = null;

                    Log.d(TAG, "onCreate: MediaCodec stopping");
                    audioDecoder.stop();        // 28. 释放 MediaCodec
                    audioDecoder.release();
                    audioDecoder = null;

                    extractor.release();        // 29. 释放 MediaExtractor
                    extractor = null;
                    break;
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
