// Copyright 2017 Google Inc. All rights reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     https://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
package com.example.polysample;

import javax.microedition.khronos.egl.EGLConfig;
import javax.microedition.khronos.opengles.GL10;

import android.opengl.GLES20;
import android.opengl.GLSurfaceView;
import android.opengl.Matrix;
import android.util.Log;

/**
 * Renderer responsible for rendering the contents of our GLSurfaceView.
 */
public class MyGLRenderer implements GLSurfaceView.Renderer {
  private static final String TAG = "PolySample";

  // Camera field of view angle, in degrees (vertical).
  private static final float FOV_Y = 60;

  // Near clipping plane.
  private static final float NEAR_CLIP = 0.1f;

  // Far clipping plane.
  private static final float FAR_CLIP = 1000f;

  // Model spin speed in degrees per second.
  private static final float MODEL_ROTATION_SPEED_DPS = 45.0f;

  // Camera position and orientation:
  private static final float EYE_X = 0;
  private static final float EYE_Y = 3;
  private static final float EYE_Z = -10;
  private static final float TARGET_X = 0;
  private static final float TARGET_Y = 0;
  private static final float TARGET_Z = 0;
  private static final float UP_X = 0;
  private static final float UP_Y = 1;
  private static final float UP_Z = 0;

  // Model matrix. Transforms object space into world space.
  private final float[] modelMatrix = new float[16];

  // View matrix. Transforms world space into eye space.
  private final float[] viewMatrix = new float[16];

  // Projection matrix. Transforms eye space into clip space.
  private final float[] projMatrix = new float[16];

  // Model View Projection matrix (product of projection, view and model matrices).
  private final float[] mvpMatrix = new float[16];

  // Temporary matrix for calculations.
  private final float[] tmpMatrix = new float[16];

  // The shader we use to draw the object.
  private MyShader myShader;

  // If true, we are ready to render the object. If false, the object isn't available yet.
  private boolean readyToRender = false;

  // Handle of the VBO that stores the vertex positions of the object.
  private int positionsVbo;

  // Handle of the VBO that stores the color information for the object.
  private int colorsVbo;

  // Handle of the IBO that stores the sequence of indices we use to draw the object.
  private int ibo;

  // Number of indices present in the IBO.
  private int indexCount;

  // Time (as given by System.currentTimeMillis) when the last frame was rendered.
  private long lastFrameTime;

  // The current model rotation angle, in degrees. This angle is increased each frame to create
  // the spinning animation.
  private float angleDegrees;

  // The RawObject to render. This is set by the main thread when the object is ready to render,
  // and is consumed by the GL thread. Once set, this is never modified.
  private volatile RawObject objectToRender;

  @Override
  public void onSurfaceCreated(GL10 unused, EGLConfig config) {
    GLES20.glClearColor(0.0f, 0.15f, 0.15f, 1.0f);
    GLES20.glEnable(GLES20.GL_DEPTH_TEST);
    lastFrameTime = System.currentTimeMillis();
    myShader = new MyShader();
  }

  @Override
  public void onDrawFrame(GL10 unused) {
    // Update the spin animation.
    long now = System.currentTimeMillis();
    float deltaT = Math.min((now - lastFrameTime) * 0.001f, 0.1f);
    lastFrameTime = now;
    angleDegrees += deltaT * MODEL_ROTATION_SPEED_DPS;

    // Draw background color.
    GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT | GLES20.GL_DEPTH_BUFFER_BIT);

    // Make a model matrix that rotates the model about the Y axis so it appears to spin.
    Matrix.setRotateM(modelMatrix, 0, angleDegrees, 0, 1, 0);

    // Set the camera position (View matrix)
    Matrix.setLookAtM(viewMatrix, 0,
        // Camera position.
        EYE_X, EYE_Y, EYE_Z,
        // Point that the camera is looking at.
        TARGET_X, TARGET_Y, TARGET_Z,
        // The vector that defines which way is up.
        UP_X, UP_Y, UP_Z);

    // Calculate the MVP matrix (model-view-projection) by multiplying the model, view, and
    // projection matrices together.
    Matrix.multiplyMM(tmpMatrix, 0, viewMatrix, 0, modelMatrix, 0);  // V * M
    Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, tmpMatrix, 0);  // P * V * M

    // objectToRender is volatile, so we capture it in a local variable.
    RawObject obj = objectToRender;

    if (readyToRender) {
      // We're ready to render, so just render using our existing VBOs and IBO.
      myShader.render(mvpMatrix, indexCount, ibo, positionsVbo, colorsVbo);
    } else if (obj != null) {
      // The object is ready, but we haven't consumed it yet. We need to create the VBOs and IBO
      // to render the object.
      indexCount = obj.indexCount;
      ibo = MyGLUtils.createIbo(obj.indices);
      positionsVbo = MyGLUtils.createVbo(obj.positions);
      colorsVbo = MyGLUtils.createVbo(obj.colors);
      // Now we're ready to render the object.
      readyToRender = true;
      Log.d(TAG, "VBOs/IBO created. Now ready to render object.");
    }
  }

  @Override
  public void onSurfaceChanged(GL10 unused, int width, int height) {
    GLES20.glViewport(0, 0, width, height);
    float aspectRatio = (float) width / height;
    // Recompute the projection matrix, because it depends on the aspect ration of the display.
    Matrix.perspectiveM(projMatrix, 0, FOV_Y, aspectRatio, NEAR_CLIP, FAR_CLIP);
  }

  // Can be called on any thread.
  public void setRawObjectToRender(RawObject rawObject) {
    if (objectToRender != null) throw new RuntimeException("Already had object.");
    // It's safe to set objectToRender from a different thread. It's marked as volatile, and
    // the GL thread will notice it on the next frame.
    objectToRender = rawObject;
    Log.d(TAG, "Received raw object to render.");
  }
}
