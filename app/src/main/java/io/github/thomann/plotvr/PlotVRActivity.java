/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.thomann.plotvr;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.preference.PreferenceManager;
import android.util.Log;

import com.google.vr.sdk.audio.GvrAudioEngine;
import com.google.vr.sdk.base.AndroidCompat;
import com.google.vr.sdk.base.GvrActivity;
import com.google.vr.sdk.base.GvrView;

import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.UnknownHostException;

/**
 * PlotVR - walk through your data using Google VR
 *
 */
public class PlotVRActivity extends GvrActivity {
    public static final String TAG = "PlotVRActivity";

    public static final boolean OPEN_LAST_URI_ON_DEFAULT=false;

    private Renderer renderer;

    private WebSocketClient mWebSocketClient;
    private String host = "amarna:9454";
    private String dataUrl = null;

    private Data data;


    // at the moment we do not use VR-Audio, but maybe eventually...
    private GvrAudioEngine gvrAudioEngine;
    private volatile int sourceId = GvrAudioEngine.INVALID_ID;
    private volatile int successSourceId = GvrAudioEngine.INVALID_ID;

    /**
     * Sets the view to our GvrView and initializes the transformation matrices we will use
     * to render our scene.
     */
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        // Java-Websocket seems to have problem with ipv6, hence:
        java.lang.System.setProperty("java.net.preferIPv6Addresses", "false");
        java.lang.System.setProperty("java.net.preferIPv4Stack", "true");

        renderer = new Renderer(this);

        initializeGvrView();

        // Initialize 3D audio engine.
        gvrAudioEngine = new GvrAudioEngine(this, GvrAudioEngine.RenderingMode.BINAURAL_HIGH_QUALITY);

        // now lets try to find out, what data to show:
        Intent intent = getIntent();
        Uri uri = intent.getData();
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        if (OPEN_LAST_URI_ON_DEFAULT && uri == null) {
            String preferredUri = sharedPref.getString("preferred.uri", null);
            if (preferredUri != null) {
                uri = Uri.parse(preferredUri);
                Log.i(TAG, "Getting intent uri from prefs: " + preferredUri);
            }
        }
        if (uri == null) {
            Log.i(TAG, "Loading Sample Data");
            setData(loadSampleData());
        } else {
            setDataHost(uri);

        }

    }

    public void initializeGvrView() {
        setContentView(R.layout.common_ui);

        GvrView gvrView = (GvrView) findViewById(R.id.gvr_view);
        gvrView.setEGLConfigChooser(8, 8, 8, 8, 16, 8);

        gvrView.setRenderer(renderer);
        gvrView.setTransitionViewEnabled(true);

        // Enable Cardboard-trigger feedback with Daydream headsets. This is a simple way of supporting
        // Daydream controller input for basic interactions using the existing Cardboard trigger API.
        gvrView.enableCardboardTriggerEmulation();

        if (gvrView.setAsyncReprojectionEnabled(true)) {
            // Async reprojection decouples the app framerate from the display framerate,
            // allowing immersive interaction even at the throttled clockrates set by
            // sustained performance mode.
            AndroidCompat.setSustainedPerformanceMode(this, true);
        }

        setGvrView(gvrView);
    }

    @Override
    public void onPause() {
        gvrAudioEngine.pause();
        super.onPause();
    }

    @Override
    public void onResume() {
        super.onResume();
        gvrAudioEngine.resume();
    }

    public void setDataHost(Uri uri) {
        Log.i(TAG, "Initial intent data: " + uri);
        if (uri.getLastPathSegment()==null || !uri.getLastPathSegment().equals("data.json"))
            uri = uri.buildUpon().path(uri.getPath().replaceAll("/[^/]*$", "/data.json")).build();
        if(uri.getPort() < 10)
            uri = uri.buildUpon().encodedAuthority(uri.getAuthority()+":2908").build();
        dataUrl = uri.toString();
        Log.i(TAG, "Using dataUrl " + dataUrl);
        refreshData();
        SharedPreferences sharedPref = getPreferences(Context.MODE_PRIVATE);
        sharedPref.edit().putString("preferred.uri", uri.toString()).apply();


        // Workaround since Java-Websocket does no ipv6:
        //host = uri.getHost()+":"+uri.getPort();
        final Uri uri2 = uri;
        new Thread() {
            public void run() {
                try {
                    InetAddress[] iads = InetAddress.getAllByName(uri2.getHost());
                    for (InetAddress ia : iads) {
                        if (ia instanceof Inet4Address) {
                            host = ia.getHostAddress() + ":" + uri2.getPort();
                            connectWebSocket();
                            break;
                        }
                    }
                } catch (UnknownHostException e) {
                    e.printStackTrace();
                }
            }
        }.start();
    }

    private void connectWebSocket() {
        try {
            if (mWebSocketClient != null && mWebSocketClient.getConnection().isOpen()) {
                mWebSocketClient.close();
            }
        } catch (Throwable t) {
            Log.i(TAG, "Problem with hanging websocket? " + t);
        }
        URI uri;
        try {
            uri = new URI("ws://" + host);
        } catch (URISyntaxException e) {
            e.printStackTrace();
            return;
        }

        Log.i(TAG, "Webscoket Connecting to " + uri);

        mWebSocketClient = new WebSocketClient(uri) {
            @Override
            public void onOpen(ServerHandshake serverHandshake) {
                Log.i(TAG, "Opened");
                mWebSocketClient.send("Hello from " + Build.MANUFACTURER + " " + Build.MODEL);
            }

            @Override
            public void onMessage(String message) {
                Log.i(TAG, "Websocket Got Message: " + message);
                handle_char(message);
            }

            @Override
            public void onClose(int i, String s, boolean b) {
                Log.i(TAG, "Websocket Closed " + s);
            }

            @Override
            public void onError(Exception e) {
                Log.i(TAG, "Websocket Error " + e.getMessage());
            }
        };
        mWebSocketClient.connect();
    }

    public void doubleClick() {
        Intent intent = new Intent(this, ServerListActivity.class);
        startActivity(intent);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent intent) {
        if (requestCode == 0) {
            if (resultCode == RESULT_OK) {
                String contents = intent.getStringExtra("SCAN_RESULT");
                String format = intent.getStringExtra("SCAN_RESULT_FORMAT");
                // Handle successful scan
                Log.i(TAG, "Got result: " + contents + " and format: " + format);
                setDataHost(Uri.parse(contents));
            } else if (resultCode == RESULT_CANCELED) {
                // nothing
            }
        }
    }


    public Data loadSampleData() {
        InputStream inputStream = getResources().openRawResource(R.raw.data);
        try {
            return new Data().readJson(new InputStreamReader(inputStream));
        } catch (IOException e) {
            e.printStackTrace();
            return new Data();
        }
    }

    void handle_char(String msg) {
        // by interning, we can use == ;-)
        msg = msg.intern();
        if (msg == "r") {
            reload();
            return;
        } else if (msg == "reload_data" || msg == "x") {
            refreshData();
        } else if (msg == "space") {
            renderer.setDoWalking(!renderer.isDoWalking());
        } else if (msg == "period" || msg == "Tab") {
            renderer.toggleTrackHead();
        } else if (msg == "f") {
            renderer.toggleDrawFloor();
            //toggleFullscreen();
/*      }else if(msg == "c"){
        if(connected){
          device_controls.disconnect();
        }else{
          device_controls.connect();
        }
        connected = !connected;*/
        } else if (renderer.doTrackHead) {
            float[] vector = new float[3];
            float ALPHA_ROT = 0.1f;
            switch (msg) {
                case "a":
                case "Left":
                    go(0, -1);
                    break;
                case "d":
                case "Right":
                    go(0, 1);
                    break;
                case "w":
                case "Up":
                    go(2, 1);
                    break;
                case "s":
                case "Down":
                    go(2, -1);
                    break;
                case "W-Shift":
                case "Up-Shift":
                    go(1, 1);
                    break;
                case "S-Shift":
                case "Down-Shift":
                    go(1, -1);
                    break;
            }
        }
    }

    public void go(int direction, int sign) {
        renderer.go(direction, sign);
    }

    private void refreshData() {
        new DownloadTask(this).execute(getDataUrl());
    }

    private String getDataUrl() {
        if (dataUrl == null)
            return "http://" + host + "/data.json";
        else
            return dataUrl;
    }

    private void reload() {
        Log.w(TAG, "Reload not implemented yet.");
        refreshData();
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        Log.i(TAG, "Got Data: " + data);
        this.data = data;
        renderer.setData(data);
    }
}
