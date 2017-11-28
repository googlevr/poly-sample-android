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

import android.util.Log;

import java.util.HashMap;

/**
 * Represents a material library (read from MTL files).
 *
 * This is a "bare bones" implementation that just stores the diffuse color of the material.
 * If you need more information than this, you could expand this to include texture information,
 * alpha blending, etc.
 */
public class MtlLibrary {
  /**
   * Represents each of the materials in the library.
   */
  public static class Material {
    // Name of the material.
    public String name;
    // For simplicity, we only handle diffuse color in this sample code.
    public float[] diffuseColor = { 1, 1, 1, 1 };
    public Material(String name) {
      this.name = name;
    }
  }

  // Map from material name to Material.
  private HashMap<String, Material> materials = new HashMap<>();

  /** Creates a new (empty) material library. */
  public MtlLibrary() {}

  /** Parses the given MTL file contents and adds the materials to the library. */
  public void parseAndAdd(String mtlFileContents) throws MtlParseException {
    int lineNo = 0;
    try {
      String[] lines = mtlFileContents.split("\n");
      Material currentMaterial = null;
      for (lineNo = 1; lineNo <= lines.length; lineNo++) {
        String line = lines[lineNo - 1].trim();
        int indexOfSpace = line.indexOf(' ');
        String verb = indexOfSpace >= 0 ? line.substring(0, indexOfSpace).trim() : line;
        String args = indexOfSpace >= 0 ? line.substring(indexOfSpace).trim() : "";
        if (verb.equals("newmtl")) {
          // Start of a new material.
          currentMaterial = new Material(args);
          materials.put(currentMaterial.name, currentMaterial);
        } else if (verb.equals("Kd")) {
          // Set the diffuse color of the current material.
          if (currentMaterial == null) {
            throw new MtlParseException("Kd directive must come after newmtl", null);
          }
          String[] parts = args.split(" +");
          if (parts.length < 3) {
            throw new MtlParseException("Kd directive had fewer than 3 components: " + args, null);
          }
          currentMaterial.diffuseColor = new float[] {
            Float.parseFloat(parts[0]), Float.parseFloat(parts[1]), Float.parseFloat(parts[2]), 1
          };
        }
      }
    } catch (Exception ex) {
      throw new MtlParseException("Failed to parse MTL file at line " + lineNo, ex);
    }
  }

  /** Returns the material with the given name. Throws an exception if not found. */
  public Material getMaterialByName(String name) {
    Material material = materials.get(name);
    if (material == null) {
      throw new RuntimeException("Material not found: " + name);
    }
    return material;
  }

  public class MtlParseException extends Exception {
    public MtlParseException(String message, Exception cause) {
      super(message, cause);
    }
  }
}
