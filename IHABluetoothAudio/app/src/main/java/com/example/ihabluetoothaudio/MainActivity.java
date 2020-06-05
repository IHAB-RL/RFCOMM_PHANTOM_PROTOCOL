// SOURCE:  https://github.com/keshavlohani/BluetoothSpp
// SEE:     https://www.youtube.com/watch?v=DhB9_MNgrpE

package com.example.ihabluetoothaudio;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.media.AudioFormat;
import android.media.AudioManager;
import android.media.AudioTrack;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.PowerManager;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.TextView;

import com.example.ihabluetoothaudio.bluetotohspp.library.BluetoothSPP;
import com.example.ihabluetoothaudio.bluetotohspp.library.BluetoothState;
import com.example.ihabluetoothaudio.bluetotohspp.library.DeviceList;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;

public class MainActivity extends AppCompatActivity {
    private final static String LOG = "_IHA_";

    private BluetoothSPP bt;

    private static final int block_size = 64;
    private static final int RECORDER_SAMPLERATE = 16000;
    private int AudioBufferSize = block_size * 4;
    private int BufferElements2Rec = 2048; // block_size * 16; // want to play 2048 (2K) since 2 bytes we use only 1024
    private static final int RECORDER_CHANNELS = AudioFormat.CHANNEL_IN_STEREO;
    private static final int RECORDER_AUDIO_ENCODING = AudioFormat.ENCODING_PCM_16BIT;
    private AudioTrack audioTrack = null;

    private short AudioVolume = 0;
    private long lostBlockCount, BlockCount;
    private boolean liveAudioOut = false;
    private double[] calibValues = new double[]{Double.NaN, Double.NaN};
    private double[] calibValuesInDB = new double[]{Double.NaN, Double.NaN};

    private ConnectedThread mConnectedThread;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        findViewById(R.id.button_VolumeUp).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AudioVolume += 1;
                setVolume();
        }
        });
        findViewById(R.id.button_VolumeDown).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                AudioVolume -= 1;
                setVolume();
            }
        });
        findViewById(R.id.checkBox_adaptiveBitShift).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setAdaptiveBitShift();
            }
        });
        findViewById(R.id.checkBox_LiveAudioOut).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                setLiveAudioOut();
            }
        });
        findViewById(R.id.button_Link).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                Intent intent = new Intent(getApplicationContext(), DeviceList.class);
                startActivityForResult(intent, BluetoothState.REQUEST_CONNECT_DEVICE);
            }
        });
        findViewById(R.id.button_Calib).setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                sendCalib();
            }
        });
        WifiManager wifiManager = (WifiManager) getApplicationContext().getApplicationContext().getSystemService(Context.WIFI_SERVICE);
        if (wifiManager.isWifiEnabled()) {
            ((TextView) findViewById(R.id.LogView)).append("WiFi is enabled!\nPlease disable for lossless transmission!\n\n");//
        }
        initAudioTrack();
        initBluetooth();
    }

    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == BluetoothState.REQUEST_CONNECT_DEVICE && resultCode == Activity.RESULT_OK) {
            bt.connect(data);
            new Handler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    bt.send("STOREMAC", false);
                    bt.send("STOREMAC", false);
                    new Handler().postDelayed(new Runnable() {
                        @Override
                        public void run() {
                            bt.disconnect();
                        }
                    }, 2000);
                }
            }, 2000);
        }
    }

    private void initBluetooth() {
        bt = new BluetoothSPP(this);
        if (bt.isBluetoothEnabled() == true) {
            bt.setBluetoothStateListener(new BluetoothSPP.BluetoothStateListener() {
                public void onServiceStateChanged(int state) {
                    PowerManager powerManager = (PowerManager) getSystemService(POWER_SERVICE);
                    PowerManager.WakeLock wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyApp::MyWakelockTag");
                    if (state == BluetoothState.STATE_CONNECTED) {
                        if (!wakeLock.isHeld()) wakeLock.acquire();
                        ((TextView) findViewById(R.id.LogView)).append("Bluetooth State changed: STATE_CONNECTED\n");
                        findViewById(R.id.button_Link).setEnabled(false);
                        mConnectedThread = new ConnectedThread(bt.getBluetoothService().getConnectedThread().getInputStream());
                        mConnectedThread.setPriority(Thread.MAX_PRIORITY);
                        mConnectedThread.start();
                    } else {
                        if (mConnectedThread != null) {
                            mConnectedThread = null;
                        }
                        if (wakeLock.isHeld()) wakeLock.release();
                        if (state == BluetoothState.STATE_CONNECTING) {
                            ((TextView) findViewById(R.id.LogView)).append("Bluetooth State changed: STATE_CONNECTING\n");
                        } else if (state == BluetoothState.STATE_LISTEN) {
                            ((TextView) findViewById(R.id.LogView)).append("Bluetooth State changed: STATE_LISTEN\n");
                            findViewById(R.id.button_Link).setEnabled(true);
                        } else if (state == BluetoothState.STATE_NONE) {
                            ((TextView) findViewById(R.id.LogView)).append("Bluetooth State changed: STATE_NONE\n");
                        }
                    }
                }
            });
            bt.setupService();
            bt.startService(BluetoothState.DEVICE_OTHER);
        } else {
            bt.enable();
            initBluetooth();
        }
    }

    private void AudioTransmissionStart() {
        Log.d(LOG, "Transmission: START");
    }

    private void writeData(byte[] data) {
        if (audioTrack != null && liveAudioOut) {
            audioTrack.write(data, 0, AudioBufferSize, AudioTrack.WRITE_NON_BLOCKING);
        }
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ((TextView) findViewById(R.id.textViewVolume)).setText(String.format("%d", AudioVolume));
                ((TextView) findViewById(R.id.textLostValue)).setText(String.format("%.3f", (double) (lostBlockCount * 100.0 / BlockCount)));
                ((TextView) findViewById(R.id.textLostRealNumbersLabel)).setText(String.format("%d", lostBlockCount));
            }
        });
    }

    private void AudioTransmissionEnd() {
        Log.d(LOG, "Transmission: END");
    }

    private void setVolume() {
        AudioVolume = (short) Math.max(Math.min(AudioVolume, 9), -9);
        String data = "V+" + AudioVolume;
        if (AudioVolume < 0)
            data = "V" + AudioVolume;
        bt.send(data, false);
    }

    private void sendCalib() {
        class Local {
            public byte [] convertDoubleToByteArray(double number) {
                ByteBuffer byteBuffer = ByteBuffer.allocate(Double.BYTES);
                byteBuffer.putDouble(number);
                return byteBuffer.array();
            }
            private byte[] createData(byte type, String value) {
                byte[] data = "C0000000000".getBytes();
                byte[] values = convertDoubleToByteArray(Double.parseDouble(value));
                data[1] = type;
                data[10] = type;
                for (int count = 0; count < values.length; count++)
                {
                    data[2 + count] = values[count];
                    data[10] ^= values[count];
                }
                return data;
            }
        }
        String valueLeft = ((TextView) findViewById(R.id.textCalibValueLeft)).getText().toString().replace(",", ".");
        String valueRight = ((TextView) findViewById(R.id.textCalibValueRight)).getText().toString().replace(",", ".");

        bt.send(new Local().createData((byte)'L', valueLeft), false);
        bt.send(new Local().createData((byte)'R', valueRight), false);

        ((TextView) findViewById(R.id.LogView)).append("Set CalibValue left: " + valueLeft + "\n");
        ((TextView) findViewById(R.id.LogView)).append("Set CalibValue right: " + valueRight + "\n");
        ((TextView) findViewById(R.id.textCalibValueLeft)).setText("");
        ((TextView) findViewById(R.id.textCalibValueLeft)).setEnabled(false);
        ((TextView) findViewById(R.id.textCalibValueRight)).setText("");
        ((TextView) findViewById(R.id.textCalibValueRight)).setEnabled(false);
        new Handler().postDelayed(new Runnable() {
            @Override
            public void run() {
                mConnectedThread.initializeState = initState.STOP;
                ((TextView) findViewById(R.id.textCalibValueLeft)).setEnabled(true);
                ((TextView) findViewById(R.id.textCalibValueRight)).setEnabled(true);
            }
        }, 5000);
    }

    private void setAdaptiveBitShift() {
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

    enum initState {UNINITIALIZED, WAITING_FOR_CALIBRATION_VALUES, WAITING_FOR_AUDIOTRANSMISSION, INITIALIZED, STOP}

    class ConnectedThread extends Thread {
        private final InputStream mmInStream;
        public initState initializeState;
        boolean isRunning = true;
        public boolean useCalib = true;

        ConnectedThread(InputStream stream) {
            mmInStream = stream;
        }

        public void run() {
            RingBuffer ringBuffer = new RingBuffer(AudioBufferSize * 2);
            int alivePingTimeout = 100, i, lastBlockNumber = 0, currBlockNumber = 0, additionalBytesCount = 0;
            byte[] data = new byte[1024], emptyAudioBlock = new byte[AudioBufferSize];
            byte checksum = 0;
            int timeoutBlockLimit = 500, millisPerBlock = block_size * 1000 / RECORDER_SAMPLERATE;
            BlockCount = 0;
            lostBlockCount = 0;
            initializeState = initState.UNINITIALIZED;
            Long lastBluetoothPingTimer = System.currentTimeMillis(), lastEmptyPackageTimer = System.currentTimeMillis(), lastStreamTimer = System.currentTimeMillis();
            try {
                while (isRunning) {
                    if (mmInStream.available() >= data.length) {
                        mmInStream.read(data, 0, data.length);
                        for (i = 0; i < data.length; i++) {
                            ringBuffer.addByte(data[i]);
                            checksum ^= ringBuffer.getByte(0);
                            if (ringBuffer.getByte(-2) == (byte) 0x00 && ringBuffer.getByte(-3) == (byte) 0x80) {
                                switch (initializeState){
                                    case UNINITIALIZED:
                                        switch (((ringBuffer.getByte(-4) & 0xFF) << 8) | (ringBuffer.getByte(-5) & 0xFF)) { // Check Protocol-Version
                                            case 1:
                                                calibValuesInDB[0] = 0.0; calibValuesInDB[1] = 0.0;
                                                calibValues[0] = 1.0; calibValues[1] = 1.0;
                                                additionalBytesCount = 12;
                                                initializeState = initState.WAITING_FOR_AUDIOTRANSMISSION;
                                                break;
                                            case 2:
                                                calibValuesInDB[0] = Double.NaN; calibValuesInDB[1] = Double.NaN;
                                                calibValues[0] = Double.NaN; calibValues[1] = Double.NaN;
                                                additionalBytesCount = 12;
                                                initializeState = initState.WAITING_FOR_CALIBRATION_VALUES;
                                                break;
                                        }
                                        break;
                                    case WAITING_FOR_CALIBRATION_VALUES:
                                        if (ringBuffer.getByte(-15) == (byte) 0xFF && ringBuffer.getByte(-16) == (byte) 0x7F && ringBuffer.getByte(-14) == (byte)'C' && (ringBuffer.getByte(-13) == (byte)'L' || ringBuffer.getByte(-13) == (byte)'R')) {
                                            byte[] values = new byte[8];
                                            byte ValuesChecksum = ringBuffer.getByte(-13);
                                            for (int count = 0; count < 8; count++)
                                            {
                                                values[count] = ringBuffer.getByte(-12 + count);
                                                ValuesChecksum ^= values[count];
                                            }
                                            if (ValuesChecksum == ringBuffer.getByte(- 4)) {
                                                if (ringBuffer.getByte(-13) == 'L')
                                                    calibValuesInDB[0] = ByteBuffer.wrap(values).getDouble();
                                                else if (ringBuffer.getByte(-13) == 'R')
                                                    calibValuesInDB[1] = ByteBuffer.wrap(values).getDouble();
                                                if (!Double.isNaN(calibValuesInDB[0]) && !Double.isNaN(calibValuesInDB[1])) {
                                                    if (calibValuesInDB[0] <= calibValuesInDB[1]) {
                                                        calibValues[0] = Math.pow(10, (calibValuesInDB[0] - calibValuesInDB[1]) / 20.0);
                                                        calibValues[1] = 1;
                                                    } else {
                                                        calibValues[0] = 1;
                                                        calibValues[1] =  Math.pow(10, (calibValuesInDB[1] - calibValuesInDB[0]) / 20.0);
                                                    }
                                                    Log.d(LOG, "START AUDIOTRANSMISSION");
                                                    initializeState = initState.WAITING_FOR_AUDIOTRANSMISSION;
                                                    runOnUiThread(new Runnable() {
                                                        @Override
                                                        public void run() {
                                                            ((TextView) findViewById(R.id.LogView)).append("CalibValue left received:  " + calibValuesInDB[0] + "\n");
                                                            ((TextView) findViewById(R.id.LogView)).append("CalibValue right received: " + calibValuesInDB[1] + "\n");
                                                            ((TextView) findViewById(R.id.textCalibValueLeft)).setText(String.format("%.5f", calibValuesInDB[0]));
                                                            ((TextView) findViewById(R.id.textCalibValueRight)).setText(String.format("%.5f", calibValuesInDB[1]));
                                                        }
                                                    });
                                                }
                                            }
                                        }
                                        else if (System.currentTimeMillis() - lastStreamTimer > 1000) {
                                            bt.send("GC", false);
                                            lastStreamTimer = System.currentTimeMillis();
                                            Log.d(LOG, "send GC");
                                        }
                                        break;
                                    case WAITING_FOR_AUDIOTRANSMISSION:
                                        if (ringBuffer.getByte(2 - (AudioBufferSize + additionalBytesCount)) == (byte) 0xFF && ringBuffer.getByte(1 - (AudioBufferSize + additionalBytesCount)) == (byte) 0x7F) {
                                            if (ringBuffer.getByte(0) == (checksum ^ ringBuffer.getByte(0))) {
                                                AudioTransmissionStart();
                                                initializeState = initState.INITIALIZED;
                                                currBlockNumber = ((ringBuffer.getByte(-6) & 0xFF) << 8) | (ringBuffer.getByte(-7) & 0xFF);
                                                lastBlockNumber = currBlockNumber;
                                            }
                                            checksum = 0;
                                        }
                                        break;
                                    case INITIALIZED:
                                        if (ringBuffer.getByte(2 - (AudioBufferSize + additionalBytesCount)) == (byte) 0xFF && ringBuffer.getByte(1 - (AudioBufferSize + additionalBytesCount)) == (byte) 0x7F) {
                                            if (ringBuffer.getByte(0) == (checksum ^ ringBuffer.getByte(0))) {
                                                AudioVolume = (short) (((ringBuffer.getByte(-8) & 0xFF) << 8) | (ringBuffer.getByte(-9) & 0xFF));
                                                currBlockNumber = ((ringBuffer.getByte(-6) & 0xFF) << 8) | (ringBuffer.getByte(-7) & 0xFF);
                                                if (currBlockNumber < lastBlockNumber && lastBlockNumber - currBlockNumber > currBlockNumber + (65536 - lastBlockNumber))
                                                    currBlockNumber += 65536;
                                                if (lastBlockNumber < currBlockNumber) {
                                                    BlockCount += currBlockNumber - lastBlockNumber;
                                                    lostBlockCount += currBlockNumber - lastBlockNumber - 1;
                                                    while (lastBlockNumber < currBlockNumber - 1) {
                                                        Log.d(LOG, "CurrentBlock: " + currBlockNumber + "\tLostBlocks: " + lostBlockCount);
                                                        writeData(emptyAudioBlock);
                                                        lastBlockNumber++;
                                                    }
                                                    lastBlockNumber = currBlockNumber % 65536;
                                                    if (!getApplicationInfo().loadLabel(getPackageManager()).toString().equals("IHABSystemCheck")) {
                                                        for (int idx = 0; idx < AudioBufferSize / 2; idx++) {
                                                            if (useCalib)
                                                                ringBuffer.setShort((short) (ringBuffer.getShort(3 - (AudioBufferSize + additionalBytesCount) + idx * 2) * calibValues[idx % 2]), 3 - (AudioBufferSize + additionalBytesCount) + idx * 2);
                                                            else
                                                                ringBuffer.setShort((short) (ringBuffer.getShort(3 - (AudioBufferSize + additionalBytesCount) + idx * 2)), 3 - (AudioBufferSize + additionalBytesCount) + idx * 2);
                                                        }
                                                    }
                                                    writeData(ringBuffer.data(3 - (AudioBufferSize + additionalBytesCount), AudioBufferSize));
                                                    lastStreamTimer = System.currentTimeMillis();
                                                } else
                                                    Log.d(LOG, "CurrentBlock: " + currBlockNumber + "\tTOO SLOW!");
                                            }
                                            checksum = 0;
                                        }
                                        break;
                                    case STOP:
                                        if (initializeState == initState.INITIALIZED) AudioTransmissionEnd();
                                        initializeState = initState.UNINITIALIZED;
                                        bt.getBluetoothService().connectionLost();
                                        bt.getBluetoothService().start(false);
                                }
                            }
                        }
                        lastEmptyPackageTimer = System.currentTimeMillis();
                    } else if (initializeState == initState.INITIALIZED && System.currentTimeMillis() - lastEmptyPackageTimer > timeoutBlockLimit) {
                        for (long count = 0; count < timeoutBlockLimit / millisPerBlock; count++) {
                            BlockCount++;
                            lostBlockCount++;
                            lastBlockNumber++;
                            writeData(emptyAudioBlock);
                        }
                        Log.d(LOG, "Transmission Timeout\t");
                        lastEmptyPackageTimer = System.currentTimeMillis();
                    }
                    if (initializeState == initState.INITIALIZED) {
                        if (System.currentTimeMillis() - lastBluetoothPingTimer > alivePingTimeout) {
                            bt.send(" ", false);
                            lastBluetoothPingTimer = System.currentTimeMillis();
                        }
                        if (System.currentTimeMillis() - lastStreamTimer > 5 * 1000) // 5 seconds
                        {
                            if (initializeState == initState.INITIALIZED) AudioTransmissionEnd();
                            initializeState = initState.UNINITIALIZED;
                            bt.getBluetoothService().connectionLost();
                            bt.getBluetoothService().start(false);
                        }
                    }

                }
            } catch (IOException e) {
            }
        }

        public void close(){
            isRunning = false;
        }

    }
}
