package org.astrobotics.ds2016.io;

import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.util.concurrent.LinkedBlockingQueue;

import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Implements the network protocol
 */
public class Protocol {
    private static final String TAG = "Astro-Proto-2015";
    private static InetAddress ROBOT_ADDRESS = null;
    private static final int ROBOT_PORT = 6800;
    private DatagramSocket socket;
    private LinkedBlockingQueue<ControlData> sendQueue = new LinkedBlockingQueue<>();
    private Thread sendThread;

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
    }

    public void sendStick(int axis, float value) {
        int controlId;
        switch(axis) {
            case MotionEvent.AXIS_X:
                controlId = ControlIDs.LTHUMBX;
                break;
            case MotionEvent.AXIS_Y:
                controlId = ControlIDs.LTHUMBY;
                break;
            case MotionEvent.AXIS_Z:
                controlId = ControlIDs.RTHUMBX;
                break;
            case MotionEvent.AXIS_RZ:
                controlId = ControlIDs.RTHUMBY;
                break;
            case MotionEvent.AXIS_LTRIGGER:
                controlId = ControlIDs.LTRIGGER;
                break;
            case MotionEvent.AXIS_RTRIGGER:
                controlId = ControlIDs.RTRIGGER;
                break;
            default:
                return;
        }
        // NOTE: The triggers have range [0, 100] instead of [-100, 100]
        // So the final value that is sent will have range [90, 180] instead of [0, 180]
        // TODO actually verify this
        int data = (int) Math.max(0, Math.min(180, (value + 1) * 90));
        sendData(controlId, data);
    }

    public void sendButton(int keycode, boolean pressed) {
        int controlId;
        switch(keycode) {
            case KeyEvent.KEYCODE_BUTTON_A:
                controlId = ControlIDs.A;
                break;
            case KeyEvent.KEYCODE_BUTTON_B:
                controlId = ControlIDs.B;
                break;
            case KeyEvent.KEYCODE_BUTTON_X:
                controlId = ControlIDs.X;
                break;
            case KeyEvent.KEYCODE_BUTTON_Y:
                controlId = ControlIDs.Y;
                break;
            case KeyEvent.KEYCODE_BUTTON_L1:
                controlId = ControlIDs.LB;
                break;
            case KeyEvent.KEYCODE_BUTTON_R1:
                controlId = ControlIDs.RB;
                break;
            case KeyEvent.KEYCODE_BUTTON_SELECT:
                // TODO verify Back button maps to Select
                controlId = ControlIDs.BACK;
                break;
            case KeyEvent.KEYCODE_BUTTON_START:
                controlId = ControlIDs.START;
                break;
            case KeyEvent.KEYCODE_BUTTON_MODE:
                // TODO verify Xbox button maps to Mode
                controlId = ControlIDs.XBOX;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBL:
                controlId = ControlIDs.LTHUMBBTN;
                break;
            case KeyEvent.KEYCODE_BUTTON_THUMBR:
                controlId = ControlIDs.RTHUMBBTN;
                break;
            case KeyEvent.KEYCODE_BUTTON_L2:
                controlId = ControlIDs.L2;
                break;
            case KeyEvent.KEYCODE_BUTTON_R2:
                controlId = ControlIDs.R2;
                break;
            default:
                return;
        }
        sendData(controlId, b(pressed));
    }

    public void sendDPad(int keycode) {
        // DPAD is 4 groups of 2 bits: +Y -Y +X -X
        // Note, this will not send angled dpad positions
        int value = (b(keycode == KeyEvent.KEYCODE_DPAD_UP))
                | (b(keycode == KeyEvent.KEYCODE_DPAD_DOWN)  << 2)
                | (b(keycode == KeyEvent.KEYCODE_DPAD_RIGHT) << 4)
                | (b(keycode == KeyEvent.KEYCODE_DPAD_LEFT)  << 6);
        sendData(ControlIDs.DPAD, value);
    }

    private void sendData(int controlId, int value) {
        Log.d(TAG, "controlId: " + controlId + ", value: " + value);
        sendQueue.offer(new ControlData(controlId, value));
    }

    private int b(boolean value) {
        return (value ? 1 : 0);
    }

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
        public static final int DPAD = 17;
        public static final int L2 = 18;
        public static final int R2 = 19;
    }

    private static class ControlData {
        public final int controlId;
        public final int value;

        public ControlData(int controlId, int value) {
            this.controlId = controlId;
            this.value = value;
        }
    }

    private class SendWorker implements Runnable {
        @Override
        public void run() {
            ControlData data;
            while(!Thread.interrupted() && !socket.isClosed()) {
                try {
                    data = sendQueue.take();
                } catch(InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                }

                ByteBuffer buffer = ByteBuffer.allocate(4);
                buffer.order(ByteOrder.LITTLE_ENDIAN);
                buffer.put((byte)data.controlId).put((byte) data.value);
                byte[] dataBare = new byte[2];
                buffer.rewind();
                buffer.get(dataBare, 0, 2);
                short crc16 = (short)CRC16CCITT.crc16(dataBare);
                buffer.putShort(2, crc16);
                byte[] dataBytes = new byte[buffer.capacity()];
                buffer.get(dataBytes, 0, 4)
                try {
                    socket.send(new DatagramPacket(dataBytes, dataBytes.length, ROBOT_ADDRESS, ROBOT_PORT));
                } catch(IOException e) {
                    e.printStackTrace();
                }
            }
        }
    }
}
