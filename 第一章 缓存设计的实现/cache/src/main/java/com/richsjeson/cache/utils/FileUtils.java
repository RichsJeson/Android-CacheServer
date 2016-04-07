package com.richsjeson.cache.utils;

import android.content.Context;
import android.os.Environment;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.StringWriter;
import java.math.BigInteger;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;

/**
 * Created by richsjeson on 16-3-16.
 * @see <p>文件操作类</p>
 */
public class FileUtils {

    private static final String HASH_ALGORITHM = "MD5";
    private static final int RADIX = 10 + 26; // 10 digits + 26 letters

    /**
     *@see <p>执行读的方法</p>
     */
    public static String readFully(Reader reader) throws IOException {
        try {
            StringWriter writer = new StringWriter();
            char[] buffer = new char[1024];
            int count;
            while ((count = reader.read(buffer)) != -1) {
                writer.write(buffer, 0, count);
            }
            return writer.toString();
        } finally {
            reader.close();
        }
    }


    /**
     *@see <p>执行读的方法</p>
     */
    public static String readString(InputStream reader) throws IOException {
        try {
            ByteArrayOutputStream outSteam = new ByteArrayOutputStream();
            byte[] buffer = new byte[1024];
            int len = -1;
            while ((len = reader.read(buffer)) != -1) {
                outSteam.write(buffer, 0, len);
            }
            return new String(buffer,"UTF-8").trim();
        } finally {
            reader.close();
        }
    }

    /**
     * @see <p>返回ASCII字符，但不包括下一个“\ r\ n”，或
         *“\ n”。</p>
     *
     * @throws java.io.EOFException if the stream is exhausted before the next newline
     *                              character.
     */
    public static String readAsciiLine(InputStream in) throws IOException {
        StringBuilder result = new StringBuilder(80);
        while (true) {
            int c = in.read();
            if (c == -1) {
                throw new EOFException();
            } else if (c == '\n') {
                break;
            }

            result.append((char) c);
        }
        int length = result.length();
        if (length > 0 && result.charAt(length - 1) == '\r') {
            result.setLength(length - 1);
        }
        return result.toString();
    }

    /**
     * Closes 'closeable', ignoring any checked exceptions. Does nothing if 'closeable' is null.
     */
    public static void closeQuietly(Closeable closeable) {
        if (closeable != null) {
            try {
                closeable.close();
            } catch (RuntimeException rethrown) {
                throw rethrown;
            } catch (Exception ignored) {
            }
        }
    }

    /**
     * @see <p>尝试在一个快速的方式来删除目录</p>。
     */
    public static void deleteDirectory(File dir) throws IOException {

        if (!dir.exists()) {
            return;
        }
        final File to = new File(dir.getAbsolutePath() + System.currentTimeMillis());
        dir.renameTo(to);
        if (!dir.exists()) {
            // rebuild
            dir.mkdirs();
        }

        // 尝试使用rm -rf 的操作来删除完整的目录
        if (to.exists()) {
            String deleteCmd = "rm -rf " + to;
            Runtime runtime = Runtime.getRuntime();
            try {
                Process process = runtime.exec(deleteCmd);
                process.waitFor();
            } catch (IOException e) {

            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        if (!to.exists()) {
            return;
        }
        deleteDirectoryRecursively(to);
        if (to.exists()) {
            to.delete();
        }
    }

    /**
     * @see <p>对文件进行授权，chmod 777 文件可读可写</p>
     * @param mode
     * @param path
     */
    public static void chmod(String mode, String path) {
        try {
            String command = "chmod " + mode + " " + path;
            Runtime runtime = Runtime.getRuntime();
            Process process = runtime.exec(command);
            process.waitFor();
        } catch (IOException e) {
            e.printStackTrace();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }


    /**
     * @see <p>执行递归删除</p>
     *
     * @param dir
     * @throws java.io.IOException
     */
    public static void deleteDirectoryRecursively(File dir) throws IOException {
        File[] files = dir.listFiles();
        if (files == null) {
            throw new IllegalArgumentException("not a directory: " + dir);
        }
        for (File file : files) {
            if (file.isDirectory()) {
                deleteDirectoryRecursively(file);
            }
            if (!file.delete()) {
                throw new IOException("failed to delete file: " + file);
            }
        }
    }

    /**
     * @see <p>判断文件是否村子啊，如果存在就删除</p>
     * @param file
     * @throws IOException
     */
    public static void deleteIfExists(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException();
        }
    }

    /**
     * @see <p>将数据写入文件</p>
     * @param filePath
     * @param content
     * @return
     */
    public static boolean writeString(String filePath, String content) {
        File file = new File(filePath);
        if (!file.getParentFile().exists())
            file.getParentFile().mkdirs();

        FileWriter writer = null;
        try {

            writer = new FileWriter(file);
            writer.write(content);

        } catch (IOException e) {
        } finally {
            try {
                if (writer != null) {

                    writer.close();
                    return true;
                }
            } catch (IOException e) {
            }
        }
        return false;
    }

    /**
     * @see <p>根据文件路径读取文件信息</p>
     * @param filePath
     * @return
     */
    public static String readString(String filePath) {
        File file = new File(filePath);
        if (!file.exists())
            return null;

        FileInputStream fileInput = null;
        FileChannel channel = null;
        try {
            fileInput = new FileInputStream(filePath);
            channel = fileInput.getChannel();
            ByteBuffer buffer = ByteBuffer.allocate((int) channel.size());
            channel.read(buffer);

            ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            byteArrayOutputStream.write(buffer.array());
            return byteArrayOutputStream.toString();
        } catch (Exception e) {
        } finally {

            if (fileInput != null) {
                try {
                    fileInput.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (channel != null) {
                try {
                    channel.close();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }
        return null;
    }

    /**
     * @see <p>MD5生成</p>
     * @param key
     * @return
     */

    public static String generate(String key) {
        byte[] md5 = getMD5(key.getBytes());
        BigInteger bi = new BigInteger(md5).abs();
        return bi.toString(RADIX);
    }

    public static void main(String args[]){
        System.out.println("generate:="+generate("/storage/sdcard1/DCIM/Camera/IMG_20160321_104914.jpg"));
        System.out.println("generate:="+generate("6joi79c7l97uxw5wpzahkg0zq"));
    }

    private static byte[] getMD5(byte[] data) {
        byte[] hash = null;
        try {
            MessageDigest digest = MessageDigest.getInstance(HASH_ALGORITHM);
            digest.update(data);
            hash = digest.digest();
        } catch (NoSuchAlgorithmException e) {
            Log.i("FileUtils","MD5键值生成失败");
        }
        return hash;
    }




    /**
     * 根据传入的uniqueName获取硬盘缓存的路径地址。
     */
    public static File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

}
