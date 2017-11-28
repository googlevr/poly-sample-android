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
import android.content.Context;
import android.opengl.GLSurfaceView;
import android.util.AttributeSet;

/**
 * Surface view that renders our scene.
 */
public class MyGLSurfaceView extends GLSurfaceView {
  // The renderer responsible for rendering the contents of this view.
  private final MyGLRenderer renderer;

  public MyGLSurfaceView(Context context) {
    this(context, null);
  }

  public MyGLSurfaceView(Context context, AttributeSet attributeSet) {
    super(context, attributeSet);
    // We want OpenGL ES 2.
    setEGLContextClientVersion(2);
    renderer = new MyGLRenderer();
    setRenderer(renderer);
  }

  public MyGLRenderer getRenderer() {
    return renderer;
  }
}
