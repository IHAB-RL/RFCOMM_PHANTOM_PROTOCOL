// SOURCE:  https://github.com/keshavlohani/BluetoothSpp
// SEE:     https://www.youtube.com/watch?v=DhB9_MNgrpE

package com.example.ihabluetoothaudio;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.os.SystemClock;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.example.ihabluetoothaudio.bluetotohspp.library.BluetoothSPP;
import com.example.ihabluetoothaudio.bluetotohspp.library.BluetoothState;
import com.example.ihabluetoothaudio.bluetotohspp.library.DeviceList;

import java.nio.CharBuffer;
import java.nio.charset.Charset;
import java.util.Arrays;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;

public class MainActivity extends AppCompatActivity {
    private BluetoothSPP bt;

    private static final int block_size = 64;
    private static final int RECORDER_SAMPLERATE = 16000;
    private int AudioBufferSize = block_size * 4;
    private RingBuffer ringBuffer = new RingBuffer(AudioBufferSize * 2);
    private byte[] AudioBlock = new byte[AudioBufferSize];

    private int BufferElements2Rec = 2048; // block_size * 16; // want to play 2048 (2K) since 2 bytes we use only 1024
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private AudioTrack audioTrack = null;

    private short AudioVolume = 0;
    private long lostBlockCount, completeBlockCount, countBlocks, corruptBlocks, BlockCount;
    private int countSamples, additionalBytesCount;
    private boolean liveAudioOut = false;
    byte checksum = 0;
    private boolean IsBluetoothConnectionPingNecessary = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        //getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        findViewById(R.id.button_VolumeUp).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) { AudioVolume += 1; setVolume(); }
        });
        findViewById(R.id.button_VolumeDown).setOnClickListener(new View.OnClickListener() {public void onClick(View v) { AudioVolume -= 1; setVolume(); }});
        findViewById(R.id.checkBox_adaptiveBitShift).setOnClickListener(new View.OnClickListener() {public void onClick(View v) { setAdaptiveBitShift(); }});
        findViewById(R.id.checkBox_LiveAudioOut).setOnClickListener(new View.OnClickListener() {public void onClick(View v) { setLiveAudioOut(); }});
        findViewById(R.id.button_Link).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), DeviceList.class);
                startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
            }
        });
        initAudioTrack();
        initBluetooth();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if(requestCode == BluetoothState.REQUEST_CONNECT_DEVICE && resultCode == Activity.RESULT_OK)
        {
            bt.connect(data);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    bt.send("STOREMAC", false);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            bt.disconnect();
                        }
                    }, 1000);
                }
            }, 2000);
        }
    }

    private void initBluetooth()
    {
        bt = new BluetoothSPP(this);
        if (bt.isBluetoothEnabled() == true) {
            bt.setBluetoothStateListener(new BluetoothSPP.BluetoothStateListener() {
                public void onServiceStateChanged(int state) {
                    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                    PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag");
                    IsBluetoothConnectionPingNecessary = false;
                    if (state == BluetoothState.STATE_CONNECTED)
                    {
                        if (!wakeLock.isHeld()) wakeLock.acquire();
                        BlockCount = 0;
                        completeBlockCount = 0;
                        lostBlockCount = 0;
                        countSamples = 0;
                        checksum = 0;
                        countBlocks = 0;
                        corruptBlocks = 0;
                        additionalBytesCount = 0;
                        ((TextView) findViewById(R.id.LogView)).append("Bluetooth State changed: STATE_CONNECTED\n");
                        findViewById(R.id.button_Link).setEnabled(false);
                    } else if (state == BluetoothState.STATE_CONNECTING) {
                        ((TextView) findViewById(R.id.LogView)).append("Bluetooth State changed: STATE_CONNECTING\n");
                    }
                    else if (state == BluetoothState.STATE_LISTEN)
                    {
                        if (wakeLock.isHeld()) wakeLock.release();
                        ((TextView) findViewById(R.id.LogView)).append("Bluetooth State changed: STATE_LISTEN\n");
                        findViewById(R.id.button_Link).setEnabled(true);
                    }
                    else if (state == BluetoothState.STATE_NONE) {
                        if (wakeLock.isHeld()) wakeLock.release();
                        ((TextView) findViewById(R.id.LogView)).append("Bluetooth State changed: STATE_NONE\n");
                    }
                    else
                    {
                        if (wakeLock.isHeld()) wakeLock.release();
                    }
                }
            });
            bt.setOnDataReceivedListener(new BluetoothSPP.OnDataReceivedListener() {
                public void onDataReceived(byte[] data) {
                    for (int i = 0; i < data.length; i++)
                    {
                        IsBluetoothConnectionPingNecessary = true;
                        ringBuffer.addByte(data[i]);
                        countSamples++;
                        if (ringBuffer.getByte(-2) == (byte) 0x00 && ringBuffer.getByte(-3) == (byte) 0x80) {
                            switch (((ringBuffer.getByte(-4) & 0xFF) << 8) | (ringBuffer.getByte(-5) & 0xFF)) { // Check Protocol-Version
                                case 1:
                                    if ((ringBuffer.getByte(2 - (AudioBufferSize + 12)) == (byte) 0xFF && ringBuffer.getByte(1 - (AudioBufferSize + 12)) == (byte) 0x7F))
                                        additionalBytesCount = 12;
                                    break;
                            }
                            if (ringBuffer.getByte(2 - (AudioBufferSize + additionalBytesCount)) == (byte) 0xFF && ringBuffer.getByte(1 - (AudioBufferSize + additionalBytesCount)) == (byte) 0x7F) {
                                if (ringBuffer.getByte(0) == checksum) {
                                    long tmpBlockCount = BlockCount;
                                    AudioBlock = Arrays.copyOf(ringBuffer.data(3 - (AudioBufferSize + additionalBytesCount), AudioBufferSize), AudioBufferSize);
                                    AudioVolume = (short)(((ringBuffer.getByte(-8) & 0xFF) << 8) | (ringBuffer.getByte(-9) & 0xFF));
                                    BlockCount = ((ringBuffer.getByte(-6) & 0xFF) << 8) | (ringBuffer.getByte(-7) & 0xFF);
                                    if (tmpBlockCount < BlockCount) {
                                        bt.send(" ", false);
                                        lostBlockCount += BlockCount - (tmpBlockCount + 1);
                                        completeBlockCount += BlockCount - tmpBlockCount;
                                    }
                                    writeData(AudioBlock);
                                }
                                countSamples = 0;
                                checksum = data[i];
                                countBlocks++;
                            }
                        }
                        if (additionalBytesCount > 0 && countSamples == AudioBufferSize + additionalBytesCount) {
                            countSamples = 0;
                            corruptBlocks++;
                            countBlocks++;
                        }
                        checksum ^= data[i];
                    }
                }
            });
            bt.setupService();
            bt.startService(BluetoothState.DEVICE_OTHER);
        }
        else{
            bt.enable();
            initBluetooth();
        }
        new Timer().scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                if (IsBluetoothConnectionPingNecessary) {
                    bt.send(" ", false);
                }
            }
        }, 0, 100);
    }

    private void writeData(byte[] data) {
        if (audioTrack != null && liveAudioOut)
            audioTrack.write(data, 0, AudioBufferSize);
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.textViewVolume)).setText(String.format("%d", AudioVolume));
                ((TextView) findViewById(R.id.textCorruptValue)).setText(String.format("%.3f", ((double) corruptBlocks / (double) countBlocks) * 100.0));
                ((TextView) findViewById(R.id.textLostValue)).setText(String.format("%.3f", ((double) lostBlockCount / (double) completeBlockCount) * 100.0));
                ((TextView) findViewById(R.id.textCorruptRealNumbersLabel)).setText(String.format("%d", corruptBlocks));
                ((TextView) findViewById(R.id.textLostRealNumbersLabel)).setText(String.format("%d", lostBlockCount));
            }
        });

    }

    private void setVolume()
    {
        AudioVolume = (short)Math.max(Math.min(AudioVolume, 9), -9);
        String data = "V+" + AudioVolume;
        if (AudioVolume < 0)
            data = "V" + AudioVolume;
        bt.send(data, false);
    }

    private void setAdaptiveBitShift()
    {
        CheckBox checkbox = findViewById(R.id.checkBox_adaptiveBitShift);
        Button button1 = findViewById(R.id.button_VolumeUp);
        Button button2 = findViewById(R.id.button_VolumeDown);
        button1.setEnabled(!checkbox.isChecked());
        button2.setEnabled(!checkbox.isChecked());
        String data = "B0";
        if (checkbox.isChecked())
            data = "B1";
        bt.send(data, false);
    }

    protected void initAudioTrack() {
        if (audioTrack == null) {
            audioTrack = new AudioTrack(
                    AudioManager.STREAM_MUSIC,
                    RECORDER_SAMPLERATE,
                    RECORDER_CHANNELS,
                    RECORDER_AUDIO_ENCODING,
                    BufferElements2Rec,
                    AudioTrack.MODE_STREAM);
        }
        setLiveAudioOut();
    }

    private void setLiveAudioOut() {
        if (audioTrack != null) {
            liveAudioOut = ((CheckBox) findViewById(R.id.checkBox_LiveAudioOut)).isChecked();
            if (liveAudioOut)
                audioTrack.play();
            else
                audioTrack.stop();
        }
    }
}
