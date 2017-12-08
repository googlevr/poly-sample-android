# Poly API - Android Sample

Copyright (c) 2017 Google Inc. All rights reserved.

This is a sample project showing how to invoke the
[Poly API](https://developers.google.com/poly) from
an Android app.

**Note about rendering:** The focus of this sample is showing how to call the
Poly API, not showing how to render models. The renderers included in the
samples are very basic and only meant as a proof of concept, so they won't
work with all assets. See the notes under each sample below for information
about what kinds of assets each sample can render.

## Get your API Key

Before proceeding, you must obtain your API Key from Google Cloud Console
as decribed in the [documentation](https://developers.google.com/poly/develop).
This API Key will allow you to make calls to the Poly API.

## Basic Sample

1. Install [Android Studio](https://developer.android.com/studio/index.html).
1. Open Android Studio.
1. Choose **Import Project** from the startup menu.
1. Import the `PolySampleAndroid` directory.
1. Open the `PolyApi.java` file.
1. Enter your API Key in the `API_KEY` variable.
1. Click **Run**.

Depending on which Android SDK modules you have installed, you may need
to download additional build tool versions and/or platforms. Android Studio
will prompt you as needed during the import process.

Note: The basic renderer included in this sample only supports Blocks
objects. It uses the diffuse material color encoded in the MTL file as
the color of the material. It does not support lighting or textures.

## ARCore Sample

The ARCore sample in this package is based on the sample for the
[Google AR SDK for Android](https://github.com/google-ar/arcore-android-sdk).
The modification to that sample is that instead of loading an OBJ file from
disk, this sample loads the OBJ file using the Poly API.

To run the sample:

1. Follow the setup steps for ARCore as explained in the
   [ARCore Getting Started guide](https://developers.google.com/ar/develop/java/getting-started).
1. Open Android Studio.
1. Choose **Import Project** from the startup menu.
1. Import the `PolySampleARCore` directory.
1. Open the `PolyApi.java` file.
1. Enter your API Key in the `API_KEY` variable.
1. Ensure that your ARCore-compatible phone is plugged into your computer.
1. Click **Run**.

Note: The renderer included in this sample only supports objects with
exactly one texture.

## License

For license information, refer to the `LICENSE` file.

