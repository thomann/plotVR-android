package io.github.thomann.plotvr;

import android.content.Context;
import android.graphics.Color;
import android.opengl.GLES20;
import android.opengl.Matrix;
import android.os.Vibrator;
import android.util.Log;

import com.google.vr.sdk.audio.GvrAudioEngine;
import com.google.vr.sdk.base.Eye;
import com.google.vr.sdk.base.GvrView.StereoRenderer;
import com.google.vr.sdk.base.HeadTransform;
import com.google.vr.sdk.base.Viewport;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.Arrays;

import javax.microedition.khronos.egl.EGLConfig;

/**
 * The Renderer encapsules the OpenGL and Cardboard rendering stuff.
 *
 * Created by pht on 05.11.15.
 */
public class Renderer implements StereoRenderer {
    private static final String TAG = "Renderer";

    private PlotVRActivity plotVr;

    private Vibrator vibrator;

    private static final float Z_NEAR = 0.1f;
    private static final float Z_FAR = 100.0f;

    private static final float CAMERA_Z = 0.01f;
    private static final float TIME_DELTA = 0.02f;

    private static final float YAW_LIMIT = 0.12f;
    private static final float PITCH_LIMIT = 0.12f;

    private static final int COORDS_PER_VERTEX = 3;

    // We keep the light always position just above the user.
    private static final float[] LIGHT_POS_IN_WORLD_SPACE = new float[]{0.0f, 2.0f, 0.0f, 1.0f};

    private final float[] lightPosInEyeSpace = new float[4];

    private FloatBuffer floorVertices;
    private FloatBuffer floorColors;
    private FloatBuffer floorNormals;

    private FloatBuffer cubeVertices;
    private FloatBuffer cubeColors;
    private FloatBuffer cubeNormals;

    private int cubeProgram;
    private int floorProgram;

    private int cubePositionParam;
    private int cubeNormalParam;
    private int cubeColorParam;
    private int cubeModelParam;
    private int cubeModelViewParam;
    private int cubeModelViewProjectionParam;
    private int cubeLightPosParam;

    private int floorPositionParam;
    private int floorNormalParam;
    private int floorColorParam;
    private int floorModelParam;
    private int floorModelViewParam;
    private int floorModelViewProjectionParam;
    private int floorLightPosParam;

    private float[] modelCube;
    private float[] camera;
    private float[] view;
    private float[] headView;
    private float[] modelViewProjection;
    private float[] modelView;
    private float[] modelFloor;

    private float[] headPreView;
    private float[] lastOkHeadView;
    public boolean doTrackHead = true;
    private float[][] eyesViews = new float[3][16];

    float[][] vectorTriangle = new float[3][4];

    private float objectDistance = 12f;
    private float floorDepth = 20f;

    private Data data = new Data();

    private boolean doWalking = false;
    private boolean doDrawFloor = true;

    private static final int[] COLOR_PALETTE = {Color.BLACK, Color.RED, Color.GREEN, Color.BLUE, Color.CYAN, Color.MAGENTA, Color.YELLOW, Color.GRAY};
    private float[][] colorsMatrix;

    public Renderer(PlotVRActivity plotVr) {
        this.plotVr = plotVr;
        modelCube = new float[16];
        camera = new float[16];
        view = new float[16];
        modelViewProjection = new float[16];
        modelView = new float[16];
        modelFloor = new float[16];
        headView = new float[16];
        vibrator = (Vibrator) plotVr.getSystemService(Context.VIBRATOR_SERVICE);

        headPreView = new float[16];
        lastOkHeadView = new float[16];


    }

    @Override
    public void onRendererShutdown() {
        Log.i(Renderer.TAG, "onRendererShutdown");
    }

    @Override
    public void onSurfaceChanged(int width, int height) {
        Log.i(Renderer.TAG, "onSurfaceChanged");
    }

    /**
     * Creates the buffers we use to store information about the 3D world.
     * <p/>
     * <p>OpenGL doesn't use Java arrays, but rather needs data in a format it can understand.
     * Hence we use ByteBuffers.
     *
     * @param config The EGL configuration used when creating the surface.
     */
    @Override
    public void onSurfaceCreated(EGLConfig config) {
        Log.i(Renderer.TAG, "onSurfaceCreated");
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 0.5f); // Dark background so text shows up well.

        makeCubes();

        // make a floor
        ByteBuffer bbFloorVertices = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COORDS.length * 4);
        bbFloorVertices.order(ByteOrder.nativeOrder());
        floorVertices = bbFloorVertices.asFloatBuffer();
        floorVertices.put(WorldLayoutData.FLOOR_COORDS);
        floorVertices.position(0);

        ByteBuffer bbFloorNormals = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_NORMALS.length * 4);
        bbFloorNormals.order(ByteOrder.nativeOrder());
        floorNormals = bbFloorNormals.asFloatBuffer();
        floorNormals.put(WorldLayoutData.FLOOR_NORMALS);
        floorNormals.position(0);

        ByteBuffer bbFloorColors = ByteBuffer.allocateDirect(WorldLayoutData.FLOOR_COLORS.length * 4);
        bbFloorColors.order(ByteOrder.nativeOrder());
        floorColors = bbFloorColors.asFloatBuffer();
        floorColors.put(WorldLayoutData.FLOOR_COLORS);
        floorColors.position(0);

        int vertexShader = loadGLShader(GLES20.GL_VERTEX_SHADER, R.raw.light_vertex);
        int gridShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.grid_fragment);
        int passthroughShader = loadGLShader(GLES20.GL_FRAGMENT_SHADER, R.raw.passthrough_fragment);

        cubeProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(cubeProgram, vertexShader);
        GLES20.glAttachShader(cubeProgram, passthroughShader);
        GLES20.glLinkProgram(cubeProgram);
        GLES20.glUseProgram(cubeProgram);

        checkGLError("Cube program");

        cubePositionParam = GLES20.glGetAttribLocation(cubeProgram, "a_Position");
        cubeNormalParam = GLES20.glGetAttribLocation(cubeProgram, "a_Normal");
        cubeColorParam = GLES20.glGetAttribLocation(cubeProgram, "a_Color");

        cubeModelParam = GLES20.glGetUniformLocation(cubeProgram, "u_Model");
        cubeModelViewParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVMatrix");
        cubeModelViewProjectionParam = GLES20.glGetUniformLocation(cubeProgram, "u_MVP");
        cubeLightPosParam = GLES20.glGetUniformLocation(cubeProgram, "u_LightPos");

        GLES20.glEnableVertexAttribArray(cubePositionParam);
        GLES20.glEnableVertexAttribArray(cubeNormalParam);
        GLES20.glEnableVertexAttribArray(cubeColorParam);

        checkGLError("Cube program params");

        floorProgram = GLES20.glCreateProgram();
        GLES20.glAttachShader(floorProgram, vertexShader);
        GLES20.glAttachShader(floorProgram, gridShader);
        GLES20.glLinkProgram(floorProgram);
        GLES20.glUseProgram(floorProgram);

        checkGLError("Floor program");

        floorModelParam = GLES20.glGetUniformLocation(floorProgram, "u_Model");
        floorModelViewParam = GLES20.glGetUniformLocation(floorProgram, "u_MVMatrix");
        floorModelViewProjectionParam = GLES20.glGetUniformLocation(floorProgram, "u_MVP");
        floorLightPosParam = GLES20.glGetUniformLocation(floorProgram, "u_LightPos");

        floorPositionParam = GLES20.glGetAttribLocation(floorProgram, "a_Position");
        floorNormalParam = GLES20.glGetAttribLocation(floorProgram, "a_Normal");
        floorColorParam = GLES20.glGetAttribLocation(floorProgram, "a_Color");

        GLES20.glEnableVertexAttribArray(floorPositionParam);
        GLES20.glEnableVertexAttribArray(floorNormalParam);
        GLES20.glEnableVertexAttribArray(floorColorParam);

        checkGLError("Floor program params");

        // Object first appears directly in front of user.
        Matrix.setIdentityM(modelCube, 0);
        Matrix.translateM(modelCube, 0, 0, 0, -objectDistance);

        Matrix.setIdentityM(modelFloor, 0);
        Matrix.translateM(modelFloor, 0, 0, -floorDepth, 0); // Floor appears below user.

        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        Matrix.setIdentityM(headPreView, 0);
        // Avoid any delays during start-up due to decoding of sound files.
//    new Thread(
//            new Runnable() {
//              @Override
//              public void run() {
//                // Start spatial audio playback of OBJECT_SOUND_FILE at the model position. The
//                // returned sourceId handle is stored and allows for repositioning the sound object
//                // whenever the cube position changes.
//                gvrAudioEngine.preloadSoundFile(OBJECT_SOUND_FILE);
//                sourceId = gvrAudioEngine.createSoundObject(OBJECT_SOUND_FILE);
//                gvrAudioEngine.setSoundObjectPosition(
//                    sourceId, modelPosition[0], modelPosition[1], modelPosition[2]);
//                gvrAudioEngine.playSound(sourceId, true /* looped playback */);
//                // Preload an unspatialized sound to be played on a successful trigger on the cube.
//                gvrAudioEngine.preloadSoundFile(SUCCESS_SOUND_FILE);
//              }
//            })
//        .start();
//        // Update the sound location to match it with the new cube position.
//        if (sourceId != GvrAudioEngine.INVALID_ID) {
//            gvrAudioEngine.setSoundObjectPosition(
//                    sourceId, modelPosition[0], modelPosition[1], modelPosition[2]);
//        }

        checkGLError("onSurfaceCreated");
    }

    public float[] getColorsMatrix(int col_index) {
        if (colorsMatrix == null) {
            int length = WorldLayoutData.CUBE_COLORS.length;
            colorsMatrix = new float[COLOR_PALETTE.length][length];
            for (int c = 0; c < COLOR_PALETTE.length; c++) {
                float r = Color.red(COLOR_PALETTE[c]) / 256f;
                float g = Color.green(COLOR_PALETTE[c]) / 256f;
                float b = Color.blue(COLOR_PALETTE[c]) / 256f;
                for (int i = 0; i < length; i += 4) {
                    colorsMatrix[c][i + 0] = r;
                    colorsMatrix[c][i + 1] = g;
                    colorsMatrix[c][i + 2] = b;
                    colorsMatrix[c][i + 3] = 1f;
                }
            }
        }
        if (col_index > colorsMatrix.length)
            col_index = col_index % colorsMatrix.length;
        return colorsMatrix[col_index];
    }

    private void makeCubes() {

        if (getmData() == null)
            return;

        float SCALE_CUBE = 0.1f;

        float[] CUBE_COORDS = WorldLayoutData.CUBE_COORDS;
        float[] data_coords = new float[getmData().length * CUBE_COORDS.length];
        float[] CUBE_COLORS = WorldLayoutData.CUBE_COLORS;
        float[] data_colors = new float[getmData().length * CUBE_COLORS.length];
        float[] CUBE_NORMALS = WorldLayoutData.CUBE_NORMALS;
        float[] data_normals = new float[getmData().length * CUBE_NORMALS.length];

        float[] t = new float[16];

        for (int i = 0; i < getmData().length; i++) {
            float[] sample = getmData()[i];
            int color_index = (int) sample[3];
            int offset;

            // first the vertices
            offset = i * CUBE_COORDS.length;
            int j = 0;
            for (int v = 0; v < CUBE_COORDS.length; v++) {
                data_coords[offset + v] = SCALE_CUBE * CUBE_COORDS[v] + sample[v % 3];
            }
            // now the colors
            offset = i * CUBE_COLORS.length;
            System.arraycopy(getColorsMatrix(color_index), 0, data_colors, offset, CUBE_COLORS.length);
//            for(int v=0; v<CUBE_COLORS.length; v+=4){
//                int col = COLOR_PALETTE[color_index];
//                data_colors[offset+v+0] = Color.red(col)/256f;
//                data_colors[offset+v+1] = Color.green(col)/256f;
//                data_colors[offset+v+2] = Color.blue(col)/256f;
//                data_colors[offset+v+3] = 1f;
//            }
            // finally the normals
            offset = i * CUBE_NORMALS.length;
            System.arraycopy(CUBE_NORMALS, 0, data_normals, offset, CUBE_NORMALS.length);
        }

//        data_coords = CUBE_COORDS;
//        data_colors = CUBE_COLORS;
//        data_normals = CUBE_NORMALS;

//        for(int i=0; i<CUBE_COORDS.length; i++)
//            if(CUBE_COORDS[i] != data_coords[i])
//                Log.w(TAG, "Not the same: "+i+": "+CUBE_COORDS[i] +" vs. "+ data_coords[i]);
//        for(int i=0; i<CUBE_COLORS.length; i++)
//            if(CUBE_COLORS[i] != data_colors[i])
//                Log.w(TAG, "Not the same: "+i+": "+CUBE_COLORS[i] +" vs. "+ data_colors[i]);
//        for(int i=0; i<CUBE_NORMALS.length; i++)
//            if(CUBE_NORMALS[i] != data_normals[i])
//                Log.w(TAG, "Not the same: "+i+": "+CUBE_NORMALS[i] +" vs. "+ data_normals[i]);

        ByteBuffer bbVertices = ByteBuffer.allocateDirect(data_coords.length * 4);
        bbVertices.order(ByteOrder.nativeOrder());
        cubeVertices = bbVertices.asFloatBuffer();
        cubeVertices.put(data_coords);
        cubeVertices.position(0);

        ByteBuffer bbColors = ByteBuffer.allocateDirect(data_colors.length * 4);
        bbColors.order(ByteOrder.nativeOrder());
        this.cubeColors = bbColors.asFloatBuffer();
        this.cubeColors.put(data_colors);
        this.cubeColors.position(0);

        ByteBuffer bbNormals = ByteBuffer.allocateDirect(data_normals.length * 4);
        bbNormals.order(ByteOrder.nativeOrder());
        this.cubeNormals = bbNormals.asFloatBuffer();
        this.cubeNormals.put(data_normals);
        this.cubeNormals.position(0);
    }

    /**
     * Prepares OpenGL ES before we draw a frame.
     *
     * @param headTransform The head transformation in the new frame.
     */
    @Override
    public void onNewFrame(HeadTransform headTransform) {
        // Build the Model part of the ModelView matrix.
        //Matrix.rotateM(modelCube, 0, TIME_DELTA, 0.5f, 0.5f, 1.0f);

        // Build the camera matrix and apply it to the ModelView.
//        Matrix.setLookAtM(camera, 0, 0.0f, 0.0f, CAMERA_Z, 0.0f, 0.0f, 0.0f, 0.0f, 1.0f, 0.0f);

        headTransform.getHeadView(headView, 0);

        tmpMat[3] = 1f;
        headTransform.getRightVector(tmpMat, 0);
        Matrix.multiplyMV(vectorTriangle[0], 0, headPreView, 0, tmpMat, 0);
        headTransform.getUpVector(tmpMat, 0);
        Matrix.multiplyMV(vectorTriangle[1], 0, headPreView, 0, tmpMat, 0);
        headTransform.getForwardVector(tmpMat, 0);
        Matrix.multiplyMV(vectorTriangle[2], 0, headPreView, 0, tmpMat, 0);

        if (isDoWalking() && doTrackHead) {
            Matrix.translateM(camera, 0, vectorTriangle[2][0] * TIME_DELTA, vectorTriangle[2][1] * TIME_DELTA, -vectorTriangle[2][2] * TIME_DELTA);
        }

//        // Update the 3d audio engine with the most recent head rotation.
//        headTransform.getQuaternion(headRotation, 0);
//        gvrAudioEngine.setHeadRotation(
//                headRotation[0], headRotation[1], headRotation[2], headRotation[3]);
//        // Regular update call to GVR audio engine.
//        gvrAudioEngine.update();

        checkGLError("onReadyToDraw");
    }

    float[] tmpMat = new float[16];

    /**
     * Draws a frame for an eye.
     *
     * @param eye The eye to render. Includes all required transformations.
     */
    @Override
    public void onDrawEye(Eye eye) {
        GLES20.glEnable(GLES20.GL_DEPTH_TEST);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        checkGLError("colorParam");

        float[] eyeView = eye.getEyeView();
        if (doTrackHead) {
            System.arraycopy(eyeView, 0, eyesViews[eye.getType()], 0, 16);
        } else {
            eyeView = eyesViews[eye.getType()];
        }

        // Apply the eye transformation to the camera.
        Matrix.multiplyMM(view, 0, eyeView, 0, camera, 0);

        // Set the position of the light
        Matrix.multiplyMV(lightPosInEyeSpace, 0, view, 0, LIGHT_POS_IN_WORLD_SPACE, 0);

        // Build the ModelView and ModelViewProjection matrices
        // for calculating cube position and light.
        float[] perspective = eye.getPerspective(Z_NEAR, Z_FAR);
        Matrix.multiplyMM(modelView, 0, view, 0, modelCube, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0, modelView, 0);
        drawCube();

        // Set modelView for the floor, so we draw floor in the correct location
        Matrix.multiplyMM(modelView, 0, view, 0, modelFloor, 0);
        Matrix.multiplyMM(modelViewProjection, 0, perspective, 0,
                modelView, 0);
        if (isDoDrawFloor())
            drawFloor();
    }

    @Override
    public void onFinishFrame(Viewport viewport) {
    }

    /**
     * Draw the cube.
     * <p/>
     * <p>We've set all of our transformation matrices. Now we simply pass them into the shader.
     */
    public void drawCube() {
        if (getmData() == null || cubeVertices == null || cubeColors == null || cubeNormals == null)
            return;
        GLES20.glUseProgram(cubeProgram);

        GLES20.glUniform3fv(cubeLightPosParam, 1, lightPosInEyeSpace, 0);

        // Set the Model in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(cubeModelParam, 1, false, modelCube, 0);

        // Set the ModelView in the shader, used to calculate lighting
        GLES20.glUniformMatrix4fv(cubeModelViewParam, 1, false, modelView, 0);

        // Set the position of the cube
        GLES20.glVertexAttribPointer(cubePositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, cubeVertices);

        // Set the ModelViewProjection matrix in the shader.
        GLES20.glUniformMatrix4fv(cubeModelViewProjectionParam, 1, false, modelViewProjection, 0);

        // Set the normal positions of the cube, again for shading
        GLES20.glVertexAttribPointer(cubeNormalParam, 3, GLES20.GL_FLOAT, false, 0, cubeNormals);
        GLES20.glVertexAttribPointer(cubeColorParam, 4, GLES20.GL_FLOAT, false, 0, cubeColors);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 36 * getmData().length);
        checkGLError("Drawing cube");
    }

    /**
     * Draw the floor.
     * <p/>
     * <p>This feeds in data for the floor into the shader. Note that this doesn't feed in data about
     * position of the light, so if we rewrite our code to draw the floor first, the lighting might
     * look strange.
     */
    public void drawFloor() {
        GLES20.glUseProgram(floorProgram);

        // Set ModelView, MVP, position, normals, and color.
        GLES20.glUniform3fv(floorLightPosParam, 1, lightPosInEyeSpace, 0);
        GLES20.glUniformMatrix4fv(floorModelParam, 1, false, modelFloor, 0);
        GLES20.glUniformMatrix4fv(floorModelViewParam, 1, false, modelView, 0);
        GLES20.glUniformMatrix4fv(floorModelViewProjectionParam, 1, false,
                modelViewProjection, 0);
        GLES20.glVertexAttribPointer(floorPositionParam, COORDS_PER_VERTEX, GLES20.GL_FLOAT,
                false, 0, floorVertices);
        GLES20.glVertexAttribPointer(floorNormalParam, 3, GLES20.GL_FLOAT, false, 0,
                floorNormals);
        GLES20.glVertexAttribPointer(floorColorParam, 4, GLES20.GL_FLOAT, false, 0, floorColors);

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 6);

        checkGLError("drawing floor");
    }


    long lastTrigger = 0;

    /**
     * Find a new random position for the object.
     * <p/>
     * <p>We'll rotate it around the Y-axis so it's out of sight, and then up or down by a little bit.
     */
    private void hideObject() {
        float[] rotationMatrix = new float[16];
        float[] posVec = new float[4];

        // First rotate in XZ plane, between 90 and 270 deg away, and scale so that we vary
        // the object's distance from the user.
        float angleXZ = (float) Math.random() * 180 + 90;
        Matrix.setRotateM(rotationMatrix, 0, angleXZ, 0f, 1f, 0f);
        float oldObjectDistance = objectDistance;
        objectDistance = (float) Math.random() * 15 + 5;
        float objectScalingFactor = objectDistance / oldObjectDistance;
        Matrix.scaleM(rotationMatrix, 0, objectScalingFactor, objectScalingFactor,
                objectScalingFactor);
        Matrix.multiplyMV(posVec, 0, rotationMatrix, 0, modelCube, 12);

        // Now get the up or down angle, between -20 and 20 degrees.
        float angleY = (float) Math.random() * 80 - 40; // Angle in Y plane, between -40 and 40.
        angleY = (float) Math.toRadians(angleY);
        float newY = (float) Math.tan(angleY) * objectDistance;

        Matrix.setIdentityM(modelCube, 0);
        Matrix.translateM(modelCube, 0, posVec[0], newY, posVec[2]);
    }

    /**
     * Called when the Cardboard trigger is pulled.
     */
    public void onCardboardTrigger() {
        Log.i(Renderer.TAG, "onCardboardTrigger");

        setDoWalking(!isDoWalking());
        if (System.currentTimeMillis() - lastTrigger < 300) {
            // This is a double click!

            plotVr.doubleClick();

        }

        // Always give user feedback.
        vibrator.vibrate(50);
        lastTrigger = System.currentTimeMillis();
    }

    /**
     * Check if user is looking at object by calculating where the object is in eye-space.
     *
     * @return true if the user is looking at the object.
     */
    private boolean isLookingAtObject() {
        float[] initVec = {0, 0, 0, 1.0f};
        float[] objPositionVec = new float[4];

        // Convert object space to camera space. Use the headView from onNewFrame.
        Matrix.multiplyMM(modelView, 0, headView, 0, modelCube, 0);
        Matrix.multiplyMV(objPositionVec, 0, modelView, 0, initVec, 0);

        float pitch = (float) Math.atan2(objPositionVec[1], -objPositionVec[2]);
        float yaw = (float) Math.atan2(objPositionVec[0], -objPositionVec[2]);

        return Math.abs(pitch) < PITCH_LIMIT && Math.abs(yaw) < YAW_LIMIT;
    }

    public void go(int direction, int sign) {
        float SCALE = 1f;
        SCALE *= sign;
        if (direction != 2) SCALE *= -1;
//        float[] vec = { x,y,z, 1f};
//        float[] vec2 = new float[4];
//        Matrix.multiplyMV(vec2, 0, camera, 0, vec, 0);
        float[] vec2 = vectorTriangle[direction];
        Matrix.translateM(camera, 0, vec2[0] * SCALE, vec2[1] * SCALE, -vec2[2] * SCALE);

        Log.i(Renderer.TAG, "Camera now " + Arrays.toString(Arrays.copyOfRange(camera, 12, 16)));

//        float[] vec1 = { 1,0,0,1 };
//        vec2 = new float[4];
//        Matrix.multiplyMV(vec2, 0, headView, 0, vec1, 0);
//        Log.i(TAG, "Right now " + Arrays.toString(vec2));
//        Log.i(TAG, "Right now "+Arrays.toString(vectorTriangle[0]));
//        Log.i(TAG, "Up now "+Arrays.toString(vectorTriangle[1]));
//        Log.i(TAG, "Forward now " + Arrays.toString(vectorTriangle[2]));

    }


    public void toggleTrackHead() {
        doTrackHead = !doTrackHead;
        if (doTrackHead) {
            // we now track the head again:
            float[] tmp = new float[16];
            float[] tmp2 = new float[16];
            Matrix.multiplyMM(tmp, 0, lastOkHeadView, 0, camera, 0);
            boolean ok = Matrix.invertM(tmp2, 0, headView, 0);
            if (!ok) {
                Log.w(Renderer.TAG, "Matrix inversion not okay!");
                return;
            }
            Matrix.multiplyMM(headPreView, 0, tmp2, 0, lastOkHeadView, 0);
            System.arraycopy(camera, 0, tmp2, 0, 16);
            Matrix.multiplyMM(camera, 0, headPreView, 0, tmp2, 0);
        } else {
            System.arraycopy(headView, 0, lastOkHeadView, 0, headView.length);
        }
    }

    public void toggleDrawFloor() {
        setDoDrawFloor(!isDoDrawFloor());
    }

    public float[][] getmData() {
        return getData().getTheData();
    }

    public double getSpeed() {
        return getData().getSpeed();
    }

    public void setSpeed(double speed) {
        getData().setSpeed(speed);
    }

    public boolean isDoWalking() {
        return doWalking;
    }

    public void setDoWalking(boolean doWalking) {
        this.doWalking = doWalking;
    }

    public boolean isDoDrawFloor() {
        return doDrawFloor;
    }

    public void setDoDrawFloor(boolean doDrawFloor) {
        this.doDrawFloor = doDrawFloor;
    }

    public Data getData() {
        return data;
    }

    public void setData(Data data) {
        if(data==null){
            Log.e(TAG, "Data is null!");
            return;
        }
        this.data = data;
        makeCubes();
    }

    /**
     * Converts a raw text file into a string.
     *
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The context of the text file, or null in case of error.
     */
    private String readRawTextFile(int resId) {
        InputStream inputStream = plotVr.getResources().openRawResource(resId);
        try {
            BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append("\n");
            }
            reader.close();
            return sb.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    /**
     * Converts a raw text file, saved as a resource, into an OpenGL ES shader.
     *
     * @param type The type of shader we will be creating.
     * @param resId The resource ID of the raw text file about to be turned into a shader.
     * @return The shader object handler.
     */
    private int loadGLShader(int type, int resId) {
        String code = readRawTextFile(resId);
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, code);
        GLES20.glCompileShader(shader);

        // Get the compilation status.
        final int[] compileStatus = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compileStatus, 0);

        // If the compilation failed, delete the shader.
        if (compileStatus[0] == 0) {
            Log.e(TAG, "Error compiling shader: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            shader = 0;
        }

        if (shader == 0) {
            throw new RuntimeException("Error creating shader.");
        }

        return shader;
    }

    /**
     * Checks if we've had an error inside of OpenGL ES, and if so what that error is.
     *
     * @param label Label to report in case of error.
     */
    private static void checkGLError(String label) {
        int error;
        while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
            Log.e(TAG, label + ": glError " + error);
            throw new RuntimeException(label + ": glError " + error);
        }
    }


}
