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

import android.opengl.GLES20;
import android.util.Log;

/**
 * Our simple unlit shader.
 *
 * This shader renders geometry and colors as given by an IBO and VBOs.
 */
public class MyShader {
  private static final String TAG = "PolySample";

  // Source code for the vertex shader.
  private static final String VERTEX_SHADER_SOURCE =
      // MVP matrix (combined model, view and projection matrices).
      "uniform mat4 uMVPMatrix;\n" +
      // Position attribute (position of the vertex in world space).
      "attribute vec4 aPosition;\n" +
      // Vertex color attribute.
      "attribute vec4 aColor;\n" +
      // Vertex color varying (used to pass the vertex color to the fragment shader).
      "varying vec4 vColor;\n" +
      "void main() {\n" +
      "  vColor = aColor;\n" +
         // aPosition is in world space. Multiplying it by the MVP matrix will convert it
         // to a screen position, which is what we should write to gl_Position.
      "  gl_Position = uMVPMatrix * aPosition;\n" +
      "}\n";

  private static final String FRAGMENT_SHADER_SOURCE =
      "precision mediump float;\n" +
      // Color (received from vertex shader).
      "varying vec4 vColor;\n" +
      "void main() {\n" +
         // Since this is a simple unlit shader, we just set the fragment color to the color
         // we got from the vertex shader.
      "  gl_FragColor = vColor;\n" +
      "}\n";

  // Handle to the program (vertex shader + fragment shader).
  private int program;
  // Handle to the uMVPMatrix uniform, which we use to feed the MVP matrix into the shader.
  private int mvpMatrixHandle;
  // Handle to the aPosition attribute, which we use to feed positions into the shader.
  private int positionHandle;
  // Handle to the aColor attribute, which we use to feed vertex colors into the shader.
  private int colorHandle;

  /** Creates the shader. This will compile and link the shader. */
  public MyShader() {
    // Compile and link the shader program.
    int vertexShader = MyGLUtils.loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER_SOURCE);
    int fragmentShader = MyGLUtils.loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER_SOURCE);
    program = GLES20.glCreateProgram();
    GLES20.glAttachShader(program, vertexShader);
    GLES20.glAttachShader(program, fragmentShader);
    GLES20.glLinkProgram(program);
    MyGLUtils.checkGlError("link program");

    // Get the handles to our shader parameters.
    GLES20.glUseProgram(program);
    positionHandle = GLES20.glGetAttribLocation(program, "aPosition");
    colorHandle = GLES20.glGetAttribLocation(program, "aColor");
    mvpMatrixHandle = GLES20.glGetUniformLocation(program, "uMVPMatrix");
    GLES20.glUseProgram(0);
    MyGLUtils.checkGlError("get handles");
  }

  public void render(float[] mvpMatrix, int numIndices, int ibo, int positionVbo, int colorsVbo) {
    GLES20.glUseProgram(program);

    // Set up to feed positions to shader from positions VBO.
    GLES20.glEnableVertexAttribArray(positionHandle);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, positionVbo);
    GLES20.glVertexAttribPointer(positionHandle, MyGLUtils.COORDS_PER_VERTEX, GLES20.GL_FLOAT, false,
        /* stride */ 0, /* offset in positionVbo */ 0);

    // Set up to feed colors to shader from color VBO.
    GLES20.glEnableVertexAttribArray(colorHandle);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, colorsVbo);
    GLES20.glVertexAttribPointer(colorHandle, MyGLUtils.NUM_COLOR_COMPONENTS, GLES20.GL_FLOAT, false,
        /* stride */ 0, /* offset in colorVbo */ 0);

    // Feed MVP matrix uniform to shader.
    GLES20.glUniformMatrix4fv(mvpMatrixHandle, 1, false, mvpMatrix, 0);

    // Bind IBO and render the triangles.
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibo);
    GLES20.glDrawElements(GLES20.GL_TRIANGLES, numIndices, GLES20.GL_UNSIGNED_SHORT,
        /* offset in ibo */ 0);
    MyGLUtils.checkGlError("render");

    // Clean up.
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    GLES20.glDisableVertexAttribArray(positionHandle);
    GLES20.glDisableVertexAttribArray(colorHandle);
    GLES20.glUseProgram(0);
  }
}
