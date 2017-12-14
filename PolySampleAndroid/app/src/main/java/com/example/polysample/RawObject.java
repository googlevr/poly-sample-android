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

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.nio.ShortBuffer;

/**
 * Represents a "raw object" in an OpenGL-friendly format.
 *
 * This format is generated from an OBJ + MTL file combination, and contains the buffers
 * necessary to convert it to a combination of an IBO and VBOs for rendering in OpenGL.
 *
 * This class does not do the OpenGL part, because that has to be done by the GL thread.
 * It only converts and stores the information in a convenient way that the GL thread
 * can then use to create the OpenGL objects (IBO, VBOs).
 */
public class RawObject {
  // Buffer with the vertex positions (3 floats per vertex: x, y, z).
  public FloatBuffer positions;
  // Buffer with the vertex colors (4 floats per vertex, RGBA).
  public FloatBuffer colors;
  // Buffer with the vertex normals (3 floats per vertex: x, y, z). Normalized.
  public FloatBuffer normals;
  // Index buffer with the draw order (each index is an unsigned short).
  public ShortBuffer indices;
  // Number of vertices in the buffers.
  public int vertexCount;
  // Number of indices in the index buffer.
  public int indexCount;

  /**
   * Converts an OBJ + MTL combination to raw format.
   * @param geometry The geometry to convert.
   * @param materials The materials library.
   * @param translation The translation to apply to each vertex. Translation is applied BEFORE scale.
   * @param scaleFactor The scale to apply to each vertex. Scale is applied AFTER translation.
   * @return
   */
  public static RawObject convertObjAndMtl(ObjGeometry geometry, MtlLibrary materials,
        ObjGeometry.Vec3 translation, float scaleFactor) {
    RawObject result = new RawObject();

    // First, let's figure out how many entries we will need.
    result.vertexCount = 0;
    result.indexCount = 0;
    for (int i = 0; i < geometry.getFaceCount(); i++) {
      int numVerticesInFace = geometry.getFace(i).faceVertices.length;
      if (numVerticesInFace < 3) continue;
      result.vertexCount += numVerticesInFace;
      // Each n-gon is broken into (n-2) triangles, each of which need 3 indices.
      result.indexCount += 3 * (numVerticesInFace - 2);
    }

    // Allocate the buffers with the exact capacity we will need.
    result.positions = ByteBuffer
        .allocateDirect(MyGLUtils.FLOAT_SIZE * MyGLUtils.COORDS_PER_VERTEX * result.vertexCount)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    result.colors = ByteBuffer
        .allocateDirect(MyGLUtils.FLOAT_SIZE * MyGLUtils.NUM_COLOR_COMPONENTS * result.vertexCount)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    result.normals = ByteBuffer
        .allocateDirect(MyGLUtils.FLOAT_SIZE * MyGLUtils.COORDS_PER_VERTEX * result.vertexCount)
        .order(ByteOrder.nativeOrder())
        .asFloatBuffer();
    result.indices = ByteBuffer
        .allocateDirect(MyGLUtils.SHORT_SIZE * result.indexCount)
        .order(ByteOrder.nativeOrder())
        .asShortBuffer();

    // Start writing the buffers at position 0.
    result.positions.position(0);
    result.colors.position(0);
    result.normals.position(0);
    result.indices.position(0);

    // Now comes the fun part: converting the object.
    short currentVertexIndex = 0;
    for (int i = 0; i < geometry.getFaceCount(); i++) {
      ObjGeometry.Face face = geometry.getFace(i);
      float[] faceColor = materials.getMaterialByName(face.materialName).diffuseColor;
      int numVerticesInFace = face.faceVertices.length;
      if (numVerticesInFace < 3) continue;
      short startVertexIndex = currentVertexIndex;
      for (int j = 0; j < numVerticesInFace; j++) {
        ObjGeometry.FaceVertex faceVertex = face.faceVertices[j];
        ObjGeometry.Vec3 pos = new ObjGeometry.Vec3(geometry.getVertex(faceVertex.vertexIndex));
        ObjGeometry.Vec3 normal;
        if (faceVertex.normalIndex != ObjGeometry.MISSING) {
          normal = geometry.getNormal(faceVertex.normalIndex);
        } else {
          // Missing normal.
          // TODO: recompute.
          normal = new ObjGeometry.Vec3(0, 0, 1);
        }
        translateAndScale(pos, translation, scaleFactor);
        result.positions.put(pos.x).put(pos.y).put(pos.z);
        result.normals.put(normal.x).put(normal.y).put(normal.z);
        result.colors.put(faceColor[0]).put(faceColor[1]).put(faceColor[2]).put(faceColor[3]);
        ++currentVertexIndex;
      }
      // We triangulate the face as a triangle fan with the pivot at [0].
      // So the triangles are 0-1-2, 0-2-3, 0-3-4, ...
      // If the face has numVerticesInFace vertices, it will be represented as
      // (numVerticesInFace - 2) triangles.
      for (int j = 0; j < numVerticesInFace - 2; j++) {
        // This triangle is 0,j+1,j+2.
        result.indices
            // Triangle fan pivot is always the first vertex of the face.
            .put(startVertexIndex)
            // Second vertex of triangle.
            .put((short)(startVertexIndex + j + 1))
            // Third vertex of triangle.
            .put((short)(startVertexIndex + j + 2));
      }
    }
    return result;
  }

  // Translates and scales the given point by the given translation and scale.
  // Translation is applied BEFORE scale.
  private static void translateAndScale(ObjGeometry.Vec3 pointToTransform,
      ObjGeometry.Vec3 translate, float scale) {
    pointToTransform.x = (pointToTransform.x + translate.x) * scale;
    pointToTransform.y = (pointToTransform.y + translate.y) * scale;
    pointToTransform.z = (pointToTransform.z + translate.z) * scale;
  }
}
