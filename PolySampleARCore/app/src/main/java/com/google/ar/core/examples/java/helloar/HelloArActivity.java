/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.google.ar.core.examples.java.helloar;

import com.google.ar.core.Config;
import com.google.ar.core.Frame;
import com.google.ar.core.Frame.TrackingState;
import com.google.ar.core.HitResult;
import com.google.ar.core.Plane;
import com.google.ar.core.PlaneHitResult;
import com.google.ar.core.Session;
import com.google.ar.core.examples.java.helloar.rendering.BackgroundRenderer;
import com.google.ar.core.examples.java.helloar.rendering.ObjectRenderer;
import com.google.ar.core.examples.java.helloar.rendering.PlaneAttachment;
import com.google.ar.core.examples.java.helloar.rendering.PlaneRenderer;
import com.google.ar.core.examples.java.helloar.rendering.PointCloudRenderer;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.util.Log;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.concurrent.ArrayBlockingQueue;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

/**
 * This is a simple example that shows how to create an augmented reality (AR) application using
 * the ARCore API. The application will display any detected planes and will allow the user to
 * tap on a plane to place a 3d model of the Android robot.
 */
public class HelloArActivity extends AppCompatActivity implements GLSurfaceView.Renderer {
    private static final String TAG = HelloArActivity.class.getSimpleName();

    // The asset ID to download and display.
    private static final String ASSET_ID = "6b7Ul6MeLrJ";

    // Scale factor to apply to asset when displaying.
    private static final float ASSET_SCALE = 0.2f;

    // Rendering. The Renderers are created here, and initialized when the GL surface is created.
    private GLSurfaceView mSurfaceView;

    private Config mDefaultConfig;
    private Session mSession;
    private BackgroundRenderer mBackgroundRenderer = new BackgroundRenderer();
    private GestureDetector mGestureDetector;
    private Snackbar mLoadingMessageSnackbar = null;

    private ObjectRenderer mVirtualObject = null;
    private PlaneRenderer mPlaneRenderer = new PlaneRenderer();
    private PointCloudRenderer mPointCloud = new PointCloudRenderer();

    // Temporary matrix allocated here to reduce number of allocations for each frame.
    private final float[] mAnchorMatrix = new float[16];

    // Tap handling and UI.
    private ArrayBlockingQueue<MotionEvent> mQueuedSingleTaps = new ArrayBlockingQueue<>(16);
    private ArrayList<PlaneAttachment> mTouches = new ArrayList<>();

    // Our background thread, which does all of the heavy lifting so we don't block the main thread.
    private HandlerThread mBackgroundThread;

    // Handler for the background thread, to which we post background thread tasks.
    private Handler mBackgroundThreadHandler;

    // The AsyncFileDownloader responsible for downloading a set of data files from Poly.
    private AsyncFileDownloader mFileDownloader;

    // When we're finished downloading the asset files, we flip this boolean to true to
    // indicate to the GL thread that it can import and load the model.
    private volatile boolean mReadyToImport;

    // Attributions text to display for the object (title and author).
    private String mAttributionText = "";

    // Have we already shown the attribution toast?
    private boolean mShowedAttributionToast;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        mSurfaceView = (GLSurfaceView) findViewById(R.id.surfaceview);

        mSession = new Session(/*context=*/this);

        // Create default config, check is supported, create session from that config.
        mDefaultConfig = Config.createDefaultConfig();
        if (!mSession.isSupported(mDefaultConfig)) {
            Toast.makeText(this, "This device does not support AR", Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Set up tap listener.
        mGestureDetector = new GestureDetector(this, new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onSingleTapUp(MotionEvent e) {
                onSingleTap(e);
                return true;
            }

            @Override
            public boolean onDown(MotionEvent e) {
                return true;
            }
        });

        mSurfaceView.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View v, MotionEvent event) {
                return mGestureDetector.onTouchEvent(event);
            }
        });

        // Set up renderer.
        mSurfaceView.setPreserveEGLContextOnPause(true);
        mSurfaceView.setEGLContextClientVersion(2);
        mSurfaceView.setEGLConfigChooser(8, 8, 8, 8, 16, 0); // Alpha used for plane blending.
        mSurfaceView.setRenderer(this);
        mSurfaceView.setRenderMode(GLSurfaceView.RENDERMODE_CONTINUOUSLY);

        // Create a background thread, where we will do the heavy lifting.
        mBackgroundThread = new HandlerThread("Worker");
        mBackgroundThread.start();
        mBackgroundThreadHandler = new Handler(mBackgroundThread.getLooper());

        // Request the asset from the Poly API.
        Log.d(TAG, "Requesting asset "+ ASSET_ID);
        PolyApi.GetAsset(ASSET_ID, mBackgroundThreadHandler, new AsyncHttpRequest.CompletionListener() {
            @Override
            public void onHttpRequestSuccess(byte[] responseBody) {
                // Successfully fetched asset information. This does NOT include the model's geometry,
                // it's just the metadata. Let's parse it.
                parseAsset(responseBody);
            }
            @Override
            public void onHttpRequestFailure(int statusCode, String message, Exception exception) {
                // Something went wrong with the request.
                handleRequestFailure(statusCode, message, exception);
            }
        });

    }

    @Override
    protected void onResume() {
        super.onResume();

        // ARCore requires camera permissions to operate. If we did not yet obtain runtime
        // permission on Android M and above, now is a good time to ask the user for it.
        if (CameraPermissionHelper.hasCameraPermission(this)) {
            showLoadingMessage();
            // Note that order matters - see the note in onPause(), the reverse applies here.
            mSession.resume(mDefaultConfig);
            mSurfaceView.onResume();
        } else {
            CameraPermissionHelper.requestCameraPermission(this);
        }
    }

    @Override
    public void onPause() {
        super.onPause();
        // Note that the order matters - GLSurfaceView is paused first so that it does not try
        // to query the session. If Session is paused before GLSurfaceView, GLSurfaceView may
        // still call mSession.update() and get a SessionPausedException.
        mSurfaceView.onPause();
        mSession.pause();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(this,
                "Camera permission is needed to run this application", Toast.LENGTH_LONG).show();
            finish();
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            // Standard Android full-screen functionality.
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
            getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private void onSingleTap(MotionEvent e) {
        // Queue tap if there is space. Tap is lost if queue is full.
        mQueuedSingleTaps.offer(e);
    }

    @Override
    public void onSurfaceCreated(GL10 gl, EGLConfig config) {
        GLES20.glClearColor(0.1f, 0.1f, 0.1f, 1.0f);

        // Create the texture and pass it to ARCore session to be filled during update().
        mBackgroundRenderer.createOnGlThread(/*context=*/this);
        mSession.setCameraTextureName(mBackgroundRenderer.getTextureId());

        try {
            mPlaneRenderer.createOnGlThread(/*context=*/this, "trigrid.png");
        } catch (IOException e) {
            Log.e(TAG, "Failed to read plane texture");
        }
        mPointCloud.createOnGlThread(/*context=*/this);
    }

    private void importDownloadedObject() {
        try {
            mVirtualObject = new ObjectRenderer();

            byte[] objBytes = null;
            byte[] textureBytes = null;
            for (int i = 0; i < mFileDownloader.getEntryCount(); i++) {
                AsyncFileDownloader.Entry thisEntry = mFileDownloader.getEntry(i);
                if (thisEntry.fileName.toLowerCase().endsWith(".obj")) {
                    objBytes = thisEntry.contents;
                } else if (thisEntry.fileName.toLowerCase().endsWith(".png")) {
                    textureBytes = thisEntry.contents;
                }
            }

            if (objBytes == null || textureBytes == null) {
                Log.e(TAG, "Downloaded asset doesn't have OBJ data and a PNG texture.");
                return;
            }
            Log.d(TAG, "Importing OBJ.");

            mVirtualObject.createOnGlThread(/*context=*/this, objBytes, textureBytes);
            mVirtualObject.setMaterialProperties(0.0f, 3.5f, 1.0f, 6.0f);
        } catch (IOException e) {
            Log.e(TAG, "Failed to read or parse obj file");
        }
    }

    @Override
    public void onSurfaceChanged(GL10 gl, int width, int height) {
        GLES20.glViewport(0, 0, width, height);
        // Notify ARCore session that the view size changed so that the perspective matrix and
        // the video background can be properly adjusted.
        mSession.setDisplayGeometry(width, height);
    }

    @Override
    public void onDrawFrame(GL10 gl) {
        // Clear screen to notify driver it should not load any pixels from previous frame.
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

        // If we are ready to import the object and haven't done so yet, do it now.
        if (mReadyToImport && mVirtualObject == null) {
            importDownloadedObject();
        }

        try {
            // Obtain the current frame from ARSession. When the configuration is set to
            // UpdateMode.BLOCKING (it is by default), this will throttle the rendering to the
            // camera framerate.
            Frame frame = mSession.update();

            // Handle taps. Handling only one tap per frame, as taps are usually low frequency
            // compared to frame rate.
            MotionEvent tap = mQueuedSingleTaps.poll();
            if (tap != null && frame.getTrackingState() == TrackingState.TRACKING) {
                for (HitResult hit : frame.hitTest(tap)) {
                    // Check if any plane was hit, and if it was hit inside the plane polygon.
                    if (hit instanceof PlaneHitResult && ((PlaneHitResult) hit).isHitInPolygon()) {
                        // Cap the number of objects created. This avoids overloading both the
                        // rendering system and ARCore.
                        if (mTouches.size() >= 16) {
                            mSession.removeAnchors(Arrays.asList(mTouches.get(0).getAnchor()));
                            mTouches.remove(0);
                        }
                        // Adding an Anchor tells ARCore that it should track this position in
                        // space. This anchor will be used in PlaneAttachment to place the 3d model
                        // in the correct position relative both to the world and to the plane.
                        mTouches.add(new PlaneAttachment(
                            ((PlaneHitResult) hit).getPlane(),
                            mSession.addAnchor(hit.getHitPose())));

                        // Hits are sorted by depth. Consider only closest hit on a plane.
                        break;
                    }
                }
            }

            // Draw background.
            mBackgroundRenderer.draw(frame);

            // If not tracking, don't draw 3d objects.
            if (frame.getTrackingState() == TrackingState.NOT_TRACKING) {
                return;
            }

            // Get projection matrix.
            float[] projmtx = new float[16];
            mSession.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f);

            // Get camera matrix and draw.
            float[] viewmtx = new float[16];
            frame.getViewMatrix(viewmtx, 0);

            // Compute lighting from average intensity of the image.
            final float lightIntensity = frame.getLightEstimate().getPixelIntensity();

            // Visualize tracked points.
            mPointCloud.update(frame.getPointCloud());
            mPointCloud.draw(frame.getPointCloudPose(), viewmtx, projmtx);

            // Check if we detected at least one plane. If so, hide the loading message.
            if (mLoadingMessageSnackbar != null) {
                for (Plane plane : mSession.getAllPlanes()) {
                    if (plane.getType() == com.google.ar.core.Plane.Type.HORIZONTAL_UPWARD_FACING &&
                            plane.getTrackingState() == Plane.TrackingState.TRACKING) {
                        hideLoadingMessage();
                        break;
                    }
                }
            }

            // Visualize planes.
            mPlaneRenderer.drawPlanes(mSession.getAllPlanes(), frame.getPose(), projmtx);

            // Visualize anchors created by touch.
            float scaleFactor = 1.0f;
            for (PlaneAttachment planeAttachment : mTouches) {
                if (!planeAttachment.isTracking()) {
                    continue;
                }
                // Get the current combined pose of an Anchor and Plane in world space. The Anchor
                // and Plane poses are updated during calls to session.update() as ARCore refines
                // its estimate of the world.
                planeAttachment.getPose().toMatrix(mAnchorMatrix, 0);

                // Update and draw the model.
                if (mVirtualObject != null) {
                    mVirtualObject.updateModelMatrix(mAnchorMatrix, ASSET_SCALE * scaleFactor);
                    mVirtualObject.draw(viewmtx, projmtx, lightIntensity);

                    // If we haven't yet showing the attribution toast, do it now.
                    if (!mShowedAttributionToast) {
                        showAttributionToast();
                    }
                }
            }

        } catch (Throwable t) {
            // Avoid crashing the application due to unhandled exceptions.
            Log.e(TAG, "Exception on the OpenGL thread", t);
        }
    }

    private void showAttributionToast() {
        mShowedAttributionToast = true;
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                // NOTE: we use a toast for showing attribution in this sample because it's the
                // simplest way to accomplish this. In your app, you are not required to use
                // a toast. You can display this attribution information in the most appropriate
                // way for your application.
                Toast.makeText(HelloArActivity.this, mAttributionText, Toast.LENGTH_LONG).show();
            }
        });
    }

    private void showLoadingMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingMessageSnackbar = Snackbar.make(
                    HelloArActivity.this.findViewById(android.R.id.content),
                    "Searching for surfaces...", Snackbar.LENGTH_INDEFINITE);
                mLoadingMessageSnackbar.getView().setBackgroundColor(0xbf323232);
                mLoadingMessageSnackbar.show();
            }
        });
    }

    private void hideLoadingMessage() {
        runOnUiThread(new Runnable() {
            @Override
            public void run() {
                mLoadingMessageSnackbar.dismiss();
                mLoadingMessageSnackbar = null;
            }
        });
    }

    // NOTE: this runs on the background thread.
    private void parseAsset(byte[] assetData) {
        Log.d(TAG, "Got asset response (" + assetData.length + " bytes). Parsing.");
        String assetBody = new String(assetData, Charset.forName("UTF-8"));
        Log.d(TAG, assetBody);
        try {
            JSONObject response = new JSONObject(assetBody);
            String displayName = response.getString("displayName");
            String authorName = response.getString("authorName");
            Log.d(TAG, "Display name: " + displayName);
            Log.d(TAG, "Author name: " + authorName);
            mAttributionText = displayName + " by " + authorName;

            // The asset may have several formats (OBJ, GLTF, FBX, etc). We will look for the OBJ format.
            JSONArray formats = response.getJSONArray("formats");
            boolean foundObjFormat = false;
            for (int i = 0; i < formats.length(); i++) {
                JSONObject format = formats.getJSONObject(i);
                if (format.getString("formatType").equals("OBJ")) {
                    // Found the OBJ format. The format gives us the URL of the data files that we should
                    // download (which include the OBJ file, the MTL file and the textures). We will now
                    // request those files.
                    requestDataFiles(format);
                    foundObjFormat = true;
                    break;
                }
            }
            if (!foundObjFormat) {
                // If this happens, it's because the asset doesn't have a representation in the OBJ
                // format. Since this simple sample code can only parse OBJ, we can't proceed.
                // But other formats might be available, so if your client supports multiple formats,
                // you could still try a different format instead.
                Log.e(TAG, "Could not find OBJ format in asset.");
                return;
            }
        } catch (JSONException jsonException) {
            Log.e(TAG, "JSON parsing error while processing response: " + jsonException);
            jsonException.printStackTrace();
        }
    }

    // Requests the data files for the OBJ format.
    // NOTE: this runs on the background thread.
    private void requestDataFiles(JSONObject objFormat) throws JSONException {
        // objFormat has the list of data files for the OBJ format (OBJ file, MTL file, textures).
        // We will use a AsyncFileDownloader to download all those files.
        mFileDownloader = new AsyncFileDownloader();

        // The "root file" is the OBJ.
        JSONObject rootFile = objFormat.getJSONObject("root");
        mFileDownloader.add(rootFile.getString("relativePath"), rootFile.getString("url"));

        // The "resource files" are the MTL file and textures.
        JSONArray resources = objFormat.getJSONArray("resources");
        for (int i = 0; i < resources.length(); i++) {
            JSONObject resourceFile = resources.getJSONObject(i);
            String path = resourceFile.getString("relativePath");
            String url = resourceFile.getString("url");
            // For this example, we only care about OBJ and PNG files.
            if (path.toLowerCase().endsWith(".obj") || path.toLowerCase().endsWith(".png")) {
                mFileDownloader.add(path, url);
            }
        }

        // Now start downloading the data files. When this is done, the callback will call
        // processDataFiles().
        Log.d(TAG, "Starting to download data files, # files: " + mFileDownloader.getEntryCount());
        mFileDownloader.start(mBackgroundThreadHandler, new AsyncFileDownloader.CompletionListener() {
            @Override
            public void onPolyDownloadFinished(AsyncFileDownloader downloader) {
                if (downloader.isError()) {
                    Log.e(TAG, "Failed to download data files for asset.");
                    return;
                }
                // Signal to the GL thread that download is complete, so it can go ahead and
                // import the model.
                Log.d(TAG, "Download complete, ready to import model.");
                mReadyToImport = true;
            }
        });
    }

    // NOTE: this runs on the background thread.
    private void handleRequestFailure(int statusCode, String message, Exception exception) {
        // NOTE: because this is a simple sample, we don't have any real error handling logic
        // other than just printing the error. In an actual app, this is where you would take
        // appropriate action according to your app's use case. You could, for example, surface
        // the error to the user or retry the request later.
        Log.e(TAG, "Request failed. Status code " + statusCode + ", message: " + message +
            ((exception != null) ? ", exception: " + exception : ""));
        if (exception != null) exception.printStackTrace();
    }
}
