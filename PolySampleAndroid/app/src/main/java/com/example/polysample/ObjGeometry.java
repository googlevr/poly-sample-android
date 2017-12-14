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

import java.util.ArrayList;

/**
 * Representation of an object's geometry, extracted from an OBJ file.
 */
public class ObjGeometry {
  /** Symbolic constant used in place of an index to indicate a missing component. */
  public static final int MISSING = -1;

  /** Represents a face of the object. */
  public static class Face {
    /** Name of the material with which the face should be drawn. */
    public String materialName;
    /** Vertices that make up the face. */
    public FaceVertex[] faceVertices;
    /** Creates a face. */
    public Face(FaceVertex[] faceVertices, String materialName) {
      this.faceVertices = faceVertices;
      this.materialName = materialName;
    }
  }

  /** Represents each of the vertices in a face of the object. */
  public static class FaceVertex {
    /** Index of the vertex. For use with {@link ObjGeometry#getVertex(int)}. */
    public int vertexIndex;
    /** Index of the texture coordinates. Use with {@link ObjGeometry#getTexCoord(int)}. */
    public int texCoordIndex;
    /** Index of the normal. Use with {@link ObjGeometry#getNormal(int)}. */
    public int normalIndex;
    /** Creates a FaceVertex.*/
    public FaceVertex(int vertexIndex, int texCoordIndex, int normalIndex) {
      this.vertexIndex = vertexIndex;
      this.texCoordIndex = texCoordIndex;
      this.normalIndex = normalIndex;
    }
  }

  /** Represents a vector with 3 float components (x, y and z). Can be position or direction. */
  public static class Vec3 {
    public float x;
    public float y;
    public float z;
    public Vec3(float x, float y, float z) {
      this.x = x;
      this.y = y;
      this.z = z;
    }
    public Vec3(Vec3 other) {
      this.x = other.x;
      this.y = other.y;
      this.z = other.z;
    }

    @Override
    public String toString() {
      return String.format("(%.3f, %.3f, %.3f)", x, y, z);
    }
  }

  /** Represents texture coordinates with 2 float components (u and v). */
  public static class TexCoords {
    public float u;
    public float v;

    public TexCoords(float u, float v) {
      this.u = u;
      this.v = v;
    }
  }

  // Object's vertices, as laid out in the OBJ file.
  private ArrayList<Vec3> vertices = new ArrayList<>();
  // Object's normals, as laid out in the OBJ file.
  private ArrayList<Vec3> normals = new ArrayList<>();
  // Object's texture coordinates, as laid out in the OBJ file.
  private ArrayList<TexCoords> texCoords = new ArrayList<>();
  // Object's faces, as laid out in the OBJ file.
  private ArrayList<Face> faces = new ArrayList<>();

  // Minimum coordinates of the object's axis-aligned bounding box.
  private Vec3 boundsMin = null;
  // Maximum coordinates of the object's axis-aligned bounding box.
  private Vec3 boundsMax = null;

  /**
   * Parses the given OBJ file.
   *
   * @return A {@link com.example.polysample.ObjGeometry} representing the object.
   * @throws ObjParseException if there is an error parsing the file.
   */
  public static ObjGeometry parse(String objFile) throws ObjParseException {
    ObjGeometry result = new ObjGeometry();
    String currentMaterialName = null;
    int lineNo = 0;
    try {
      String[] lines = objFile.split("\n");
      for (lineNo = 1; lineNo <= lines.length; lineNo++) {
        String line = lines[lineNo - 1].trim();
        int indexOfSpace = line.indexOf(' ');
        String verb = indexOfSpace >= 0 ? line.substring(0, indexOfSpace) : line;
        String args = indexOfSpace >= 0 ? line.substring(indexOfSpace).trim() : "";
        if (verb.equals("v")) {
          // Vertex.
          Vec3 vertex = parseVec3(args);
          result.vertices.add(vertex);
          result.encapsulateInBounds(vertex);
        } else if (verb.equals("vt")) {
          // Texture coordinates.
          result.texCoords.add(parseTexCoords(args));
        } else if (verb.equals("vn")) {
          // Vertex normal.
          result.normals.add(parseVec3(args));
        } else if (verb.equals("f")) {
          // Face.
          result.faces.add(parseFace(args, currentMaterialName));
        } else if (verb.equals("usemtl")) {
          // Use given material.
          currentMaterialName = args;
        }
      }
      if (result.vertices.size() <= 0) {
        throw new Exception("Did not find any vertices in OBJ file.");
      }
      return result;
    } catch (Exception ex) {
      throw new ObjParseException("Failed to parse OBJ, line #" + lineNo, ex);
    }
  }

  /** Returns the vertex at the given index. */
  public Vec3 getVertex(int index) {
    return vertices.get(index);
  }

  /** Returns the number of vertices in the object. */
  public int getVertexCount() {
    return vertices.size();
  }

  /** Returns the normal at the given index. */
  public Vec3 getNormal(int index) {
    return normals.get(index);
  }

  /** Returns the number of normals in the object. */
  public int getNormalCount() {
    return normals.size();
  }

  /** Returns the texture coordinates at the given index. */
  public TexCoords getTexCoord(int index) {
    return texCoords.get(index);
  }

  /** Gets the number of texture coordinates in the object. */
  public int getTexCoordCount() {
    return texCoords.size();
  }

  /** Returns the face at the given index. */
  public Face getFace(int index) {
    return faces.get(index);
  }

  /** Returns the number of faces in the object. */
  public int getFaceCount() {
    return faces.size();
  }

  /** Returns the minimum coordinates of the object's axis-aligned bounding box. */
  public Vec3 getBoundsMin() { return boundsMin; }

  /** Returns the maximum coordinates of the object's axis-aligned bounding box. */
  public Vec3 getBoundsMax() { return boundsMax; }

  /** Returns the center of the object's axis-aligned bounding box. */
  public Vec3 getBoundsCenter() {
    return new Vec3((boundsMin.x + boundsMax.x) / 2f, (boundsMin.y + boundsMax.y) / 2f,
        (boundsMin.z + boundsMax.z) / 2f);
  }

  /** Returns the size of the object's axis-aligned bounding box. */
  public Vec3 getBoundsSize() {
    return new Vec3(boundsMax.x - boundsMin.x, boundsMax.y - boundsMin.y, boundsMax.z - boundsMin.z);
  }

  private ObjGeometry() {}

  private static Vec3 parseVec3(String s) {
    String[] parts = s.trim().split(" +");
    if (parts.length != 3) throw new RuntimeException("Vec3 doesn't have 3 components.");
    return new Vec3(Float.parseFloat(parts[0]),
        Float.parseFloat(parts[1]), Float.parseFloat(parts[2]));
  }

  private static TexCoords parseTexCoords(String s) {
    String[] parts = s.trim().split(" +");
    if (parts.length < 2) throw new RuntimeException("Tex coords has < 2 components.");
    return new TexCoords(Float.parseFloat(parts[0]), Float.parseFloat(parts[1]));
  }

  private static Face parseFace(String s, String materialName) {
    String[] parts = s.trim().split(" +");
    if (parts.length < 3) throw new RuntimeException("Face must have at least 3 vertices.");
    FaceVertex[] faceVertices = new FaceVertex[parts.length];
    for (int i = 0; i < faceVertices.length; i++) {
      faceVertices[i] = parseFaceVertex(parts[i]);
    }
    return new Face(faceVertices, materialName);
  }

  private static FaceVertex parseFaceVertex(String s) {
    String[] parts = s.split("/");
    if (parts.length == 0) throw new RuntimeException("FaceVertex must have a face index.");
    int vertexIndex = Integer.parseInt(parts[0]);
    int texCoordIndex = parts.length >= 2 ? tryParseInt(parts[1], MISSING) : MISSING;
    int normalIndex = parts.length >= 3 ? tryParseInt(parts[2], MISSING) : MISSING;
    // Subtract 1 from all indices because OBJ indices start at 1 and ours start at 0.
    return new FaceVertex(
        vertexIndex == MISSING ? MISSING : vertexIndex - 1,
        texCoordIndex == MISSING ? MISSING : texCoordIndex - 1,
        normalIndex == MISSING ? MISSING : normalIndex - 1);
  }

  private static int tryParseInt(String s, int defaultValue) {
    try {
      return Integer.parseInt(s);
    } catch (NumberFormatException ex) {
      return defaultValue;
    }
  }

  private void encapsulateInBounds(Vec3 vertex) {
    if (boundsMin == null) {
      boundsMin = new Vec3(vertex);
    } else {
      boundsMin.x = Math.min(boundsMin.x, vertex.x);
      boundsMin.y = Math.min(boundsMin.y, vertex.y);
      boundsMin.z = Math.min(boundsMin.z, vertex.z);
    }
    if (boundsMax == null) {
      boundsMax = new Vec3(vertex);
    } else {
      boundsMax.x = Math.max(boundsMax.x, vertex.x);
      boundsMax.y = Math.max(boundsMax.y, vertex.y);
      boundsMax.z = Math.max(boundsMax.z, vertex.z);
    }
  }

  public static class ObjParseException extends Exception {
    public ObjParseException(String message, Exception cause) {
      super(message, cause);
    }
  }
}
