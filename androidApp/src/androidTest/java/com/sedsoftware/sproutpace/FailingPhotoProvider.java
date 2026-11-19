package com.sedsoftware.sproutpace;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.OpenableColumns;
import java.io.FileOutputStream;
import java.io.IOException;

/** Runs in the test APK's own process, without dependencies supplied by the target APK. */
public final class FailingPhotoProvider extends ContentProvider {
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/jpeg"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor cursor = new MatrixCursor(new String[] { OpenableColumns.DISPLAY_NAME });
        cursor.addRow(new Object[] { "broken.jpg" });
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws java.io.FileNotFoundException {
        try {
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createReliablePipe();
            new Thread(() -> {
                try {
                    FileOutputStream output = new FileOutputStream(pipe[1].getFileDescriptor());
                    boolean slow = "slow".equals(uri.getLastPathSegment());
                    byte[] bytes = new byte[8192];
                    java.util.Arrays.fill(bytes, (byte) 9);
                    for (int i = 0; i < (slow ? 1000 : 8); i++) {
                        output.write(bytes);
                        if (slow) Thread.sleep(20);
                    }
                    pipe[1].closeWithError("injected_photo_read_failure");
                } catch (IOException | InterruptedException failure) {
                    try { pipe[1].close(); } catch (IOException ignored) { }
                }
            }).start();
            return pipe[0];
        } catch (IOException failure) {
            throw new java.io.FileNotFoundException(failure.toString());
        }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { throw new UnsupportedOperationException(); }
    @Override public int delete(Uri uri, String selection, String[] args) { throw new UnsupportedOperationException(); }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) {
        throw new UnsupportedOperationException();
    }
}
