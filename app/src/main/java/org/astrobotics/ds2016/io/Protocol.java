package org.astrobotics.ds2016.io;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.concurrent.LinkedBlockingQueue;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Implements the network protocol
 */
public class Protocol {
    private static final String TAG = "Astro-Proto-2016";
    private static InetAddress ROBOT_ADDRESS = null;
    private static final int ROBOT_PORT = 6800;
    private DatagramSocket socket;
    private LinkedBlockingQueue<ControlData> sendQueue = new LinkedBlockingQueue<>();
    private Thread sendThread;
    private ControlData controlData;

    static {
        try {
            ROBOT_ADDRESS = InetAddress.getByAddress(new byte[] {10, 0, 0, 30});
        } catch(UnknownHostException e) {
            throw new ExceptionInInitializerError(e);
        }
    }

    public Protocol() throws IOException {
        socket = new DatagramSocket();
        socket.setReuseAddress(true);
        sendThread = new Thread(new SendWorker());
        sendThread.start();
        this.controlData = new ControlData();
    }

    public void setStick(int axis, float value) {
        Log.d(TAG, "axis " + axis + ": " + value);
        switch(axis) {
            case MotionEvent.AXIS_X:
                controlData.setAxis(ControlIDs.LTHUMBX, value);
                break;
            case MotionEvent.AXIS_Y:
                controlData.setAxis(ControlIDs.LTHUMBY, value);
                break;
            case MotionEvent.AXIS_Z:
                controlData.setAxis(ControlIDs.RTHUMBX, value);
                break;
            case MotionEvent.AXIS_RZ:
                controlData.setAxis(ControlIDs.RTHUMBY, value);
                break;
            case MotionEvent.AXIS_BRAKE:
                controlData.setAxis(ControlIDs.LTRIGGER, value);
                break;
            case MotionEvent.AXIS_THROTTLE:
                controlData.setAxis(ControlIDs.RTRIGGER, value);
                break;
            case MotionEvent.AXIS_HAT_Y:
                controlData.setDpad(MotionEvent.AXIS_HAT_Y, value);
                break;
            case MotionEvent.AXIS_HAT_X:
                controlData.setDpad(MotionEvent.AXIS_HAT_X, value);
                break;
            default:
                return;
        }

        sendData();
    }

    // for pressing buttons
    public void sendButton(int keycode, boolean pressed) {
        Log.d(TAG, "button " + keycode + ": " + pressed);
        boolean wasChanged;
        switch(keycode) {
            case KeyEvent.KEYCODE_BUTTON_A:
                wasChanged = controlData.setButton(ControlIDs.A, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                wasChanged = controlData.setButton(ControlIDs.B, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                wasChanged = controlData.setButton(ControlIDs.X, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                wasChanged = controlData.setButton(ControlIDs.Y, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                wasChanged = controlData.setButton(ControlIDs.LB, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                wasChanged = controlData.setButton(ControlIDs.RB, pressed);
                break;
            case KeyEvent.KEYCODE_BACK:
                // TODO verify Back button maps to Select
                wasChanged = controlData.setButton(ControlIDs.BACK, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
                wasChanged = controlData.setButton(ControlIDs.START, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_MODE:
                // TODO verify Xbox button maps to Mode
                wasChanged = controlData.setButton(ControlIDs.XBOX, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                wasChanged = controlData.setButton(ControlIDs.LTHUMBBTN, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                wasChanged = controlData.setButton(ControlIDs.RTHUMBBTN, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
                wasChanged = controlData.setButton(ControlIDs.L2, pressed);
                break;
            case KeyEvent.KEYCODE_BUTTON_R2:
                wasChanged = controlData.setButton(ControlIDs.R2, pressed);
                break;
            default:
                return;
        }
        // send the data on change
        if (wasChanged) {
            sendData();
        }
    }

    private void sendData() {
//        Log.d(TAG, "Adding Data to send queue");
        sendQueue.offer(new ControlData(controlData));
    }

    // ***NOTE*** change size if IDs are changed
    private static class ControlIDs {
        public static final int LTHUMBX = 0;
        public static final int LTHUMBY = 1;
        public static final int RTHUMBX = 2;
        public static final int RTHUMBY = 3;
        public static final int RTRIGGER = 4;
        public static final int LTRIGGER = 5;
        public static final int A = 6;
        public static final int B = 7;
        public static final int X = 8;
        public static final int Y = 9;
        public static final int LB = 10;
        public static final int RB = 11;
        public static final int BACK = 12;
        public static final int START = 13;
        public static final int XBOX = 14;
        public static final int LTHUMBBTN = 15;
        public static final int RTHUMBBTN = 16;
        public static final int L2 = 17;
        public static final int R2 = 18;
        public static final int DPAD_UP = 19;
        public static final int DPAD_DOWN = 20;
        public static final int DPAD_LEFT = 21;
        public static final int DPAD_RIGHT = 22;
        public static final int SIZE = 23;
    }

    private static class ControlData {
        // array for data, everything can be stored in byte,
        // though for buttons, only one bit will be used
        public byte data[];
        // for if the axis doesn't return to exactly 0 used + or -
        private final double AXIS_BOUNDS = 0.1;
        // max/min axis values can be
        private final double AXIS_MAX = 1.0;
        // max value byte should be
        private final int AXIS_BYTE_MAX = 100;
        // for the dead zone in the dpad
        private final double DPAD_BOUNDS = 0.1;


        // default constructor
        public ControlData() {
            this.data = new byte[ControlIDs.SIZE];
        }

        // copy constructor
        public ControlData(ControlData oldData){
            this.data = new byte[oldData.data.length];
            // deep copy old data
            for (int i = 0; i < oldData.data.length; i++){
                this.data[i] = oldData.data[i];
            }
        }

        // sets button to on/off
        // assumes they gave an ID of a button
        // returns true if button was changed, false if not
        public boolean setButton(int ID, boolean down){
            byte oldval = data[ID];
            if (down){
                data[ID] = 0x01;
            } else {
                data[ID] = 0x00;
            }
            return oldval != data[ID];
        }

        // assumes the id is for an axis
        // takes value from -1 to 1 and converts to specified range
        public void setAxis(int ID, double value){
            if (value > AXIS_BOUNDS){
                // truncate and make for 0 to 100
                int tVal = (int) (AXIS_BYTE_MAX * (value / AXIS_MAX));
                if (tVal > AXIS_BYTE_MAX){
                    data[ID] = AXIS_BYTE_MAX;
                } else {
                    data[ID] = ((byte) tVal);
                }
            }
            else if (value < -AXIS_BOUNDS){
                int tVal = (int) (AXIS_BYTE_MAX * (value / -AXIS_MAX));
                if (tVal > AXIS_BYTE_MAX){
                    data[ID] = -AXIS_BYTE_MAX;
                } else {
                    data[ID] = ((byte) -tVal);
                }
            }
            else {
                data[ID] = 0x00;
            }
        }

        // dpad comes as a float, but should be set to on or off
        public void setDpad(int eventCode, float value){
            if (eventCode == MotionEvent.AXIS_HAT_X) {
                if (value > DPAD_BOUNDS) {
                    data[ControlIDs.DPAD_RIGHT] = 0x01;
                } else if (value < -DPAD_BOUNDS){
                    data[ControlIDs.DPAD_LEFT] = 0x01;
                } else {
                    data[ControlIDs.DPAD_LEFT] = 0x00;
                    data[ControlIDs.DPAD_RIGHT] = 0x00;
                }
            }
            else if (eventCode == MotionEvent.AXIS_HAT_Y) {
                if (value > DPAD_BOUNDS) {
                    data[ControlIDs.DPAD_DOWN] = 0x01;
                } else if (value < -DPAD_BOUNDS){
                    data[ControlIDs.DPAD_UP] = 0x01;
                } else {
                    data[ControlIDs.DPAD_UP] = 0x00;
                    data[ControlIDs.DPAD_DOWN] = 0x00;
                }
            }
        }

        // create the binary string with crc at the end
        public byte[] toBits(){
            Log.d(TAG, "Data: " + Arrays.toString(data));

            byte[] bits = new byte[11];

            // do stuff to array
            // 6 bytes for axes
            bits[0] = data[ControlIDs.LTHUMBX];
            bits[1] = data[ControlIDs.LTHUMBY];
            bits[2] = data[ControlIDs.RTHUMBX];
            bits[3] = data[ControlIDs.RTHUMBY];
            bits[4] = data[ControlIDs.LTRIGGER];
            bits[5] = data[ControlIDs.RTRIGGER];

            // 2 bytes for buttons
            byte buttons1 = 0, buttons2 = 0;
            buttons2 += data[ControlIDs.LTHUMBBTN];
            buttons2 = (byte) (buttons2 << 1);
            buttons2 += data[ControlIDs.RTHUMBBTN];
            bits[7] = buttons2;
            buttons1 += data[ControlIDs.START];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.BACK];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.RB];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.LB];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.Y];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.X];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.B];
            buttons1 = (byte) (buttons1 << 1);
            buttons1 += data[ControlIDs.A];
            Log.d(TAG, "buttons: " + buttons1 +  " " + buttons2);
            bits[6] = buttons1;

            // 1 byte for dpad
            byte dpad = 0;
            dpad += data[ControlIDs.DPAD_UP];
            dpad = (byte) (dpad << 2);
            dpad += data[ControlIDs.DPAD_DOWN];
            dpad = (byte) (dpad << 2);
            dpad += data[ControlIDs.DPAD_LEFT];
            dpad = (byte) (dpad << 2);
            dpad += data[ControlIDs.DPAD_RIGHT];
            bits[8] = dpad;

            // the 2 bit crc
            byte[] dataBare = new byte[9];
            for (int i = 0; i < dataBare.length; i++){
                dataBare[i] = bits[i];
            }
            short crc16 = (short)CRC16CCITT.crc16(dataBare);
            bits[9] = (byte)(crc16 & 0xff);
            bits[10] = (byte)((crc16 >> 8) & 0xff);

            return bits;
        }

        // return a printable string, for debugging
        public String toString(){
            String str = "";
            for (int i = 0; i < data.length; i++){
                str = str +"\n" +i +": " +data[i];
            }
            return str;
        }
    }

    private class PingWorker implements Runnable {
        @Override
        public void run() {
            // variables
            double lastTime = System.currentTimeMillis();
            double pingFrequency = 2D;
            // while thread can send
            while (!Thread.interrupted() && !socket.isClosed()){
                if (System.currentTimeMillis() - lastTime > pingFrequency){
                    //ping
                    //reset time
                    lastTime = System.currentTimeMillis();
                    // sleep for majority of the frequency
                    try {
                        Thread.sleep((long) (pingFrequency * .95));
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                }
            }
        }
    }

    // send the data from the queue in a thread
    private class SendWorker implements Runnable {
        @Override
        public void run() {
            ControlData data;
            // while the thread can send
            while(!Thread.interrupted() && !socket.isClosed()) {
                // keep running if something is taken from stack
                try {
                    data = sendQueue.take();
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }
//                Log.d(TAG, "Sending Data");
                byte[] dataBytes = data.toBits();
                try {
                    socket.send(new DatagramPacket(dataBytes, dataBytes.length, ROBOT_ADDRESS, ROBOT_PORT));
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
