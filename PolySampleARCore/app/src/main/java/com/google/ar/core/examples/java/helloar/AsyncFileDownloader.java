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

package com.google.ar.core.examples.java.helloar;

import android.os.Handler;
import android.support.annotation.IntDef;
import android.util.Log;

import java.util.ArrayList;

/** Convenience class that asynchronously downloads a set of files. */
public class AsyncFileDownloader {
  private static final String TAG = "PolySample";

  // Possible states we can be in.
  @IntDef({STATE_NOT_STARTED, STATE_DOWNLOADING, STATE_SUCCESS, STATE_ERROR})
  public @interface State {}
  private static final int STATE_NOT_STARTED = 0;
  private static final int STATE_DOWNLOADING = 1;
  private static final int STATE_SUCCESS = 2;
  private static final int STATE_ERROR = 3;

  // Current state.
  private @State int state = STATE_NOT_STARTED;

  // The completion listener we call when we finish downloading all the files (or when there is
  // a failure).
  private CompletionListener listener;

  // The handler on which we call the completion listener.
  private Handler handler;

  // The files to download.
  private final ArrayList<Entry> entries = new ArrayList<>();

  /** Callback called when download is complete. */
  public interface CompletionListener {
    /**
     * Callback invoked when all downloads complete, or when there's a failure.
     * @param downloader The downloader. Use {@link #isError()} to determine if there was
     *                   an error or not. If there was no error, you can access the files with
     *                   {@link #getEntry(int)}.
     */
    void onPolyDownloadFinished(AsyncFileDownloader downloader);
  }

  /** Creates an AsyncFileDownloader, initially with no files. */
  public AsyncFileDownloader() {}

  /**
   * Adds a file to download.
   *
   * Can only be called before {@link #start} is called.
   * @param fileName The name of the file.
   * @param url The URL to download the file from.
   */
  public void add(String fileName, String url) {
    if (state != STATE_NOT_STARTED) {
      throw new IllegalStateException("Can't add files to AsyncFileDownloader after starting.");
    }
    entries.add(new Entry(fileName, url));
  }

  /**
   * Starts asynchronously downloading all requested files.
   * @param handler The handler on which to call the callback.
   * @param completionListener The callback to call when download completes, or when there is
   *                           an error.
   */
  public void start(Handler handler, CompletionListener completionListener) {
    if (state != STATE_NOT_STARTED) {
      throw new IllegalStateException("AsyncFileDownloader had already been started.");
    }
    this.handler = handler;
    this.listener = completionListener;
    state = STATE_DOWNLOADING;
    // For each requested file, create an AsyncHttpRequest object to request it.
    for (final Entry entry : entries) {
      AsyncHttpRequest request = new AsyncHttpRequest(entry.url, handler,
          new AsyncHttpRequest.CompletionListener() {
        @Override
        public void onHttpRequestSuccess(byte[] responseBody) {
          if (state != STATE_DOWNLOADING) return;
          Log.d(TAG, "Finished downloading " + entry.fileName + " from " + entry.url);
          entry.contents = responseBody;
          if (areAllEntriesDone()) {
            state = STATE_SUCCESS;
            invokeCompletionCallback();
          }
        }
        @Override
        public void onHttpRequestFailure(int statusCode, String message, Exception exception) {
          if (state != STATE_DOWNLOADING) return;
          Log.e(TAG, "Error downloading " + entry.fileName + " from " + entry.url + ". Status " +
              statusCode + ", message: " + message + (exception != null ? exception : ""));
          state = STATE_ERROR;
          invokeCompletionCallback();
        }
      });
      request.send();
    }
  }

  /** Returns whether or not there was an error downloading the files. */
  public boolean isError() {
    return state == STATE_ERROR;
  }

  /** Returns the number of files in this object. */
  public int getEntryCount() {
    return entries.size();
  }

  /** Returns the given file. */
  public Entry getEntry(int index) {
    return entries.get(index);
  }

  // Returns true if all entries have been downloaded (all entries have contents).
  private boolean areAllEntriesDone() {
    for (Entry entry : entries) {
      if (entry.contents == null) return false;
    }
    return true;
  }

  // Invokes the completion callback.
  private void invokeCompletionCallback() {
    handler.post(new Runnable() {
      @Override
      public void run() {
        listener.onPolyDownloadFinished(AsyncFileDownloader.this);
      }
    });
  }

  /** Represents each file entry in the downloader. */
  public static class Entry {
    /** The name of the file. */
    public final String fileName;
    /** The URL where the file is to be fetched from. */
    public final String url;
    /** The contents of the file, if it has already been fetched. Otherwise, null. */
    public byte[] contents;
    public Entry(String fileName, String url) {
      this.fileName = fileName;
      this.url = url;
    }
  }
}
