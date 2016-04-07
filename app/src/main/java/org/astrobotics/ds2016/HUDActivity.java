package org.astrobotics.ds2016;

import java.io.IOException;
import java.util.HashMap;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.LayerDrawable;
import android.hardware.input.InputManager;
import android.os.Build;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.view.InputDevice;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.widget.RadioGroup.OnCheckedChangeListener;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Toast;

import org.astrobotics.ds2016.io.MjpegInputStream;
import org.astrobotics.ds2016.io.MjpegView;
import org.astrobotics.ds2016.io.Protocol;

public class HUDActivity extends AppCompatActivity {
    private static final int[] AXES = new int[] {MotionEvent.AXIS_X, MotionEvent.AXIS_Y,
            MotionEvent.AXIS_Z, MotionEvent.AXIS_RZ, MotionEvent.AXIS_BRAKE,
            MotionEvent.AXIS_THROTTLE, MotionEvent.AXIS_HAT_X, MotionEvent.AXIS_HAT_Y};
    private HashMap<Integer, Float> prevJoyState = new HashMap<>();
    private Protocol protocol;
    private MjpegView mjpegView;
    private RadioGroup streamButtons;
    private RadioButton cam_left, cam_right, cam_none;
    private static final int MENU_QUIT = 1;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_hud);

        // set radio buttons
        streamButtons = (RadioGroup) findViewById(R.id.stream_buttons);
        streamButtons.setOnCheckedChangeListener(new OnCheckedChangeListener(){
            public void onCheckedChanged(RadioGroup group, int checkedId){
                if (checkedId == R.id.cam_left){
                    // TODO
                    // close right stream (if open)
                    Log.d("HUDActivity", "cam_left selected");
                    Log.d("HUDActivity", "x for cam left: " +findViewById(R.id.cam_left).getX());
                    Log.d("HUDActivity", "y for cam left: " + findViewById(R.id.cam_left).getY());
                    Log.d("HUDActivity", "x for cam right: " +findViewById(R.id.cam_right).getX());
                    Log.d("HUDActivity", "y for cam right: " +findViewById(R.id.cam_right).getY());
                    Log.d("HUDActivity", "x for cam none: " +findViewById(R.id.cam_none).getX());
                    Log.d("HUDActivity", "y for cam none: " +findViewById(R.id.cam_none).getY());

                } else if (checkedId == R.id.cam_right){
                    // TODO
                    // close left stream (if open)
                    Log.d("HUDActivity", "cam_right selected");
                } else if (checkedId == R.id.cam_none){
                    // TODO
                    // close both streams (if open)
                    // don't waste data
                    Log.d("HUDActivity", "cam_none selected");
                }
            }
        });

        cam_left = (RadioButton) findViewById(R.id.cam_left);
        cam_right = (RadioButton) findViewById(R.id.cam_right);
        cam_none = (RadioButton) findViewById(R.id.cam_none);

        try {
            protocol = new Protocol();
        } catch(IOException e) {
            e.printStackTrace();
            Toast.makeText(this, "Error initializing network protocol", Toast.LENGTH_LONG).show();
            finish();
        }

        // Initialize indicators
        initIndicator(R.id.robot_status, R.drawable.ic_robot_status);
        initIndicator(R.id.controller_status, R.drawable.ic_controller_status);

        // Register input device listener
        InputManager inputManager = (InputManager)getApplicationContext().getSystemService(Context.INPUT_SERVICE);
        inputManager.getInputDeviceIds(); // required for the device listener to be registered
        inputManager.registerInputDeviceListener(new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                updateGamepadStatus();
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                updateGamepadStatus();
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {
                updateGamepadStatus();
            }
        }, null);
        updateGamepadStatus();

        // how to set up GUI
        // take existing activity
        // Add the mjpeg view into activity.xml
        // 3 indicators for left camera, right camera, and no camera
        // start off disabled
        // when one button is pushed, release other two, handle view appropriately
        // maybe seperate into two layers
        // 1 for stream
        // 1 for other stuff
        String URL = "http://10.0.0.51/videostream.cgi?user=VTAstrobot&pwd=RoVER16";

        mjpegView = new MjpegView(this);
        try {
            mjpegView.setSource(MjpegInputStream.read(URL));
            mjpegView.setDisplayMode(MjpegView.SIZE_BEST_FIT);
            mjpegView.showFps(true);
            // THIS PROBABLY WON'T WORK!
            setContentView(findViewById(R.id.stream), mjpegView.getLayoutParams());
        } catch (Exception e){
            e.printStackTrace();
        }
//        setContentView(mjpegView);
    }

    @Override
    protected void onPostCreate(Bundle savedInstanceState) {
        super.onPostCreate(savedInstanceState);
    }

    @TargetApi(Build.VERSION_CODES.KITKAT)
    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        int flags = View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                  | View.SYSTEM_UI_FLAG_FULLSCREEN
                  | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN;
        if(hasFocus) {
            flags |= View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            protocol.sendButton(keyCode, true);
            return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if((event.getSource() & InputDevice.SOURCE_GAMEPAD) == InputDevice.SOURCE_GAMEPAD) {
            protocol.sendButton(keyCode, false);
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    @Override
    public boolean onGenericMotionEvent(MotionEvent event) {
        if((event.getSource() & InputDevice.SOURCE_JOYSTICK) == InputDevice.SOURCE_JOYSTICK) {
            for(int axis : AXES) {
                protocol.setStick(axis, event.getAxisValue(axis));
            }
            return true;
        }
        return super.onGenericMotionEvent(event);
    }

    // Sets indicator icon
    @SuppressWarnings("deprecation")
    private void initIndicator(int viewId, int iconId) {
        LayerDrawable layers = (LayerDrawable)findViewById(viewId).getBackground();
        layers.setDrawableByLayerId(R.id.indicator_icon, getResources().getDrawable(iconId));
    }

    // Change background color of indicator shape
    private void setIndicator(int viewId, boolean activated) {
        LayerDrawable layers = (LayerDrawable)findViewById(viewId).getBackground();
        Drawable shape = layers.findDrawableByLayerId(R.id.indicator_bg);
        if(activated) {
            shape.setLevel(1);
        } else {
            shape.setLevel(0);
        }
    }

    // Update gamepad status indicator
    private void updateGamepadStatus() {
        int gamepadCheck = InputDevice.SOURCE_GAMEPAD
                         | InputDevice.SOURCE_JOYSTICK;
//                         | InputDevice.SOURCE_DPAD;
        for(int id : InputDevice.getDeviceIds()) {
            InputDevice device = InputDevice.getDevice(id);
            int sources = device.getSources();
            if((sources & gamepadCheck) == gamepadCheck) {
                setIndicator(R.id.controller_status, true);
                return;
            }
        }
        setIndicator(R.id.controller_status, false);
    }
}
