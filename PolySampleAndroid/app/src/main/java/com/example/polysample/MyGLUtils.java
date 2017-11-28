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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

public class MyGLUtils {
  private static final String TAG = "PolySample";
  public static final int FLOAT_SIZE = 4;  // sizeof(float) is 4 bytes.
  public static final int SHORT_SIZE = 2;  // sizeof(short) is 2 bytes.

  public static final int COORDS_PER_VERTEX = 3;
  public static final int NUM_COLOR_COMPONENTS = 4; // r, g, b, a

  public static int loadShader(int type, String shaderCode) {
    // create a vertex shader type (GLES20.GL_VERTEX_SHADER)
    // or a fragment shader type (GLES20.GL_FRAGMENT_SHADER)
    int shader = GLES20.glCreateShader(type);

    // add the source code to the shader and compile it
    GLES20.glShaderSource(shader, shaderCode);
    GLES20.glCompileShader(shader);
    MyGLUtils.checkGlError("compile shader");
    int[] status = new int[1];
    GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, status, 0);
    if (status[0] != GLES20.GL_TRUE) {
      Log.e(TAG, "Shader compile error!");
      Log.e(TAG, GLES20.glGetShaderInfoLog(shader));
    }
    return shader;
  }

  public static void checkGlError(String glOperation) {
    int error;
    while ((error = GLES20.glGetError()) != GLES20.GL_NO_ERROR) {
      Log.e(TAG, glOperation + ": glError " + error);
      throw new RuntimeException(glOperation + ": glError " + error);
    }
  }

  public static int createVbo(FloatBuffer data) {
    int[] vbos = new int[1];
    data.position(0);
    GLES20.glGenBuffers(1, vbos, 0);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, vbos[0]);
    GLES20.glBufferData(GLES20.GL_ARRAY_BUFFER, data.capacity() * MyGLUtils.FLOAT_SIZE, data,
        GLES20.GL_STATIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ARRAY_BUFFER, 0);
    return vbos[0];
  }

  public static int createIbo(ShortBuffer data) {
    int[] ibos = new int[1];
    data.position(0);
    GLES20.glGenBuffers(1, ibos, 0);
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, ibos[0]);
    GLES20.glBufferData(GLES20.GL_ELEMENT_ARRAY_BUFFER, data.capacity() * MyGLUtils.SHORT_SIZE,
        data, GLES20.GL_STATIC_DRAW);
    GLES20.glBindBuffer(GLES20.GL_ELEMENT_ARRAY_BUFFER, 0);
    return ibos[0];
  }
}
