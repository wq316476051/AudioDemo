package com.wang.mediarecorder;

import android.app.Activity;
import android.media.AudioDeviceInfo;
import android.media.AudioFormat;
import android.media.MediaRecorder;
import android.os.Bundle;
import android.os.Environment;
import android.util.Log;
import android.widget.Button;

import java.io.File;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import java.nio.LongBuffer;
import java.nio.ShortBuffer;

public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";

    private MediaRecorder mMediaRecorder;

    private Button mBtnStart, mBtnPause, mBtnStop;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // init views
        mBtnStart = (Button) findViewById(R.id.btn_start);
        mBtnPause = (Button) findViewById(R.id.btn_start);
        mBtnStop = (Button) findViewById(R.id.btn_start);

        // init data

        // init listener
        mBtnStart.setOnClickListener(view -> {});
        mBtnPause.setOnClickListener(view -> {});
        mBtnStop.setOnClickListener(view -> {});

//        mMediaRecorder = createMediaPlayer(new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "filename"));
        MediaRecorder recorder = new MediaRecorder();
        recorder.setAudioSource(MediaRecorder.AudioSource.MIC);
        recorder.setOutputFormat(MediaRecorder.OutputFormat.THREE_GPP);
        recorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB);
        recorder.setOutputFile(new File(getExternalFilesDir(Environment.DIRECTORY_MUSIC), "filename").getPath());
//        recorder.prepare();
//        recorder.start();   // Recording is now started
//// ...
//        recorder.stop();
//        recorder.reset();   // You can reuse the object by going back to setAudioSource() step
//        recorder.release(); // Now the object cannot be reused

        ByteBuffer byteBuffer = null; // 8 bit
        ShortBuffer shortBuffer = byteBuffer.asShortBuffer(); // 16 bit
        IntBuffer intBuffer = byteBuffer.asIntBuffer(); // 32 bit
        LongBuffer longBuffer = byteBuffer.asLongBuffer(); // 64 bit
    }

    private static MediaRecorder createMediaPlayer(File file) {
        int audio_source = MediaRecorder.AudioSource.MIC;

        int numChannels = AudioFormat.CHANNEL_IN_STEREO;

        int audio_encoder = MediaRecorder.AudioEncoder.AMR_NB;

        int bitRate = 12800;

        int samplingRate = 44100; //44.1 kHz

        int output_format = MediaRecorder.OutputFormat.AMR_NB;

        MediaRecorder mediaRecorder = new MediaRecorder();
        mediaRecorder.setAudioSource(audio_source);
        mediaRecorder.setAudioChannels(numChannels);
        mediaRecorder.setAudioEncoder(audio_encoder);
        mediaRecorder.setAudioEncodingBitRate(bitRate);
        mediaRecorder.setAudioSamplingRate(samplingRate);
        mediaRecorder.setOutputFormat(output_format);
        mediaRecorder.setOutputFile(file.getPath());

//        try {
//            List<MicrophoneInfo> activeMicrophones = mediaRecorder.getActiveMicrophones();
//            for (MicrophoneInfo info : activeMicrophones) {
//                Log.d(TAG, "MediaRecorderHelper: info = " + info);
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }

        int maxAmplitude = mediaRecorder.getMaxAmplitude();
        Log.d(TAG, "createMediaPlayer: maxAmplitude = " + maxAmplitude);

//        AudioDeviceInfo preferredDevice = mediaRecorder.getPreferredDevice();
//        Log.d(TAG, "createMediaPlayer: preferredDevice = " + preferredDevice);
//
//        AudioDeviceInfo routedDevice = mediaRecorder.getRoutedDevice();
//        Log.d(TAG, "createMediaPlayer: routedDevice = " + routedDevice);

        return mediaRecorder;
    }

}
