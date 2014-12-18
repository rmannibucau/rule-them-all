package com.github.rmannibucau.rules.internal;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

public class Zips {
    public static void unzip(final File zipFile, final File destination) throws IOException {
        unzip(zipFile, destination, false);
    }

    public static void unzip(final File zipFile, final File destination, final boolean noparent) throws IOException {
        mkdir(destination);
        final InputStream read = new BufferedInputStream(new FileInputStream(zipFile));
        try {
            unzip(read, destination, noparent);
        } finally {
            read.close();
        }
    }

    public static void unzip(final InputStream read, final File destination, final boolean noparent) throws IOException {
        try {
            // Open the ZIP file
            final ZipInputStream in = new ZipInputStream(read);

            ZipEntry entry;

            while ((entry = in.getNextEntry()) != null) {
                String path = entry.getName();
                if (noparent) {
                    path = path.replaceFirst("^[^/]+/", "");
                }
                final File file = new File(destination, path);

                if (entry.isDirectory()) {
                    mkdir(file);
                    continue;
                }

                mkdir(file.getParentFile());
                final FileOutputStream to = new FileOutputStream(file);
                try {
                    copy(in, to);
                } finally {
                    to.close();
                }

                final long lastModified = entry.getTime();
                if (lastModified > 0) {
                    file.setLastModified(lastModified);
                }

            }

            in.close();

        } catch (final IOException e) {
            throw new IOException("Unable to unzip " + read, e);
        }
    }

    public static void copy(final InputStream from, final OutputStream to) throws IOException {
        final byte[] buffer = new byte[1024];
        int length;
        while ((length = from.read(buffer)) != -1) {
            to.write(buffer, 0, length);
        }
        to.flush();
    }

    public static File mkdir(final File file) {
        if (file.exists()) {
            return file;
        }
        if (!file.mkdirs()) {
            throw new IllegalStateException("Cannot mkdir: " + file.getAbsolutePath());
        }
        return file;
    }
}

