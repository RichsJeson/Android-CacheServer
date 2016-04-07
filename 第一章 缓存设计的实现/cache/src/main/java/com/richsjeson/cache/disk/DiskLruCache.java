/*
 * Copyright (C) 2011 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.richsjeson.cache.disk;

import android.util.Log;

import com.richsjeson.cache.interf.DiskFacade;
import com.richsjeson.cache.utils.FileUtils;

import java.io.BufferedInputStream;
import java.io.BufferedWriter;
import java.io.Closeable;
import java.io.EOFException;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Writer;
import java.lang.reflect.Array;
import java.nio.charset.Charset;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

/**
 * @see <p>磁盘缓存管理</p>
 */
public final class DiskLruCache implements Closeable,DiskFacade {
    static final String JOURNAL_FILE = "journal";
    static final String JOURNAL_FILE_TMP = "journal.tmp";
    static final String MAGIC = "com.richsjeson.DiskLruCache";
    static final String JOURNAL_FILE_BACKUP = "journal.bkp";
    static final String VERSION_1 = "1";
    static final long ANY_SEQUENCE_NUMBER = -1;
    private static final String CLEAN = "CLEAN";
    private static final String DIRTY = "DIRTY";
    private static final String REMOVE = "REMOVE";
    private static final String READ = "READ";

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    private static final int IO_BUFFER_SIZE = 8 * 1024;

    /** This cache uses a single background thread to evict entries. */
    private final ExecutorService executorService = new ThreadPoolExecutor(0, 1,
            60L, TimeUnit.SECONDS, new LinkedBlockingQueue<Runnable>());

    private final File directory;
    private final File journalFile;
    private final File journalFileTmp;
    private final int appVersion;
    private final long maxSize;
    private final int valueCount;
    private long size = 0;
    private Writer journalWriter;
    private final LinkedHashMap<String, CacheEntry> lruEntries
            = new LinkedHashMap<String, CacheEntry>(0, 0.75f, true);
    private int redundantOpCount;

    /**
     * To differentiate between old and current snapshots, each entry is given
     * a sequence number each time an edit is committed. A snapshot is stale if
     * its sequence number is not equal to its entry's sequence number.
     */
    private long nextSequenceNumber = 0;

    /* From java.util.Arrays */
    @SuppressWarnings("unchecked")
    private static <T> T[] copyOfRange(T[] original, int start, int end) {
        final int originalLength = original.length; // For exception priority compatibility.
        if (start > end) {
            throw new IllegalArgumentException();
        }
        if (start < 0 || start > originalLength) {
            throw new ArrayIndexOutOfBoundsException();
        }
        final int resultLength = end - start;
        final int copyLength = Math.min(resultLength, originalLength - start);
        final T[] result = (T[]) Array
                .newInstance(original.getClass().getComponentType(), resultLength);
        System.arraycopy(original, start, result, 0, copyLength);
        return result;
    }


    private final Callable<Void> cleanupCallable = new Callable<Void>() {
        @Override public Void call() throws Exception {
            synchronized (DiskLruCache.this) {
                if (journalWriter == null) {
                    return null; // closed
                }
                trimToSize();
                if (journalRebuildRequired()) {
                    rebuildJournal();
                    redundantOpCount = 0;
                }
            }
            return null;
        }
    };

    private DiskLruCache(File directory, int appVersion, int valueCount, long maxSize) {
        this.directory = directory;
        this.appVersion = appVersion;
        this.journalFile = new File(directory, JOURNAL_FILE);
        this.journalFileTmp = new File(directory, JOURNAL_FILE_TMP);
        this.valueCount = valueCount;
        this.maxSize = maxSize;
    }

    /**
     * Opens the cache in {@code directory}, creating a cache if none exists
     * there.
     *
     * @param directory a writable directory
     * @param appVersion
     * @param valueCount the number of values per cache entry. Must be positive.
     * @param maxSize the maximum number of bytes this cache should use to store
     * @throws IOException if reading or writing the cache directory fails
     */
    public static DiskLruCache open(File directory, int appVersion, int valueCount, long maxSize)
            throws IOException {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }
        if (valueCount <= 0) {
            throw new IllegalArgumentException("valueCount <= 0");
        }
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxFileCount <= 0");
        }

        File backupFile = new File(directory, JOURNAL_FILE_BACKUP);
        if (backupFile.exists()) {
            File journalFile = new File(directory, JOURNAL_FILE);
            // If journal file also exists just delete backup file.
            if (journalFile.exists()) {
                backupFile.delete();
            } else {
                renameTo(backupFile, journalFile, false);
            }
        }

        // prefer to pick up where we left off
        DiskLruCache cache = new DiskLruCache(directory, appVersion, valueCount, maxSize);
        if (cache.journalFile.exists()) {
            Log.i("com.richsjeson.cache.disk.DiskLruCache","cache.journalFile.exists");
            try {
                cache.readJournal();
                cache.processJournal();
                cache.journalWriter = new BufferedWriter(new FileWriter(cache.journalFile, true),
                        IO_BUFFER_SIZE);
                return cache;
            } catch (IOException journalIsCorrupt) {
                Log.e("DiskLruCache ", directory + " is corrupt: "
                        + journalIsCorrupt.getMessage() + ", removing");
                cache.delete();
            }
        }
        Log.i("com.richsjeson.cache.disk.DiskLruCache","repl");
        // create a new empty cache
        directory.mkdirs();
        cache = new DiskLruCache(directory, appVersion, valueCount, maxSize);
        cache.rebuildJournal();
        return cache;
    }


    private static void renameTo(File from, File to, boolean deleteDestination) throws IOException {
        if (false==deleteDestination) {
            deleteIfExists(to);
        }
        if (!from.renameTo(to)) {
            throw new IOException();
        }
    }
    /**
     * @see <p>读取journal文件</p>
     * @throws IOException
     */
    private void readJournal() {
        try {
            InputStream in = new BufferedInputStream(new FileInputStream(journalFile), IO_BUFFER_SIZE);
            try {
                String magic = FileUtils.readAsciiLine(in);
                String version = FileUtils.readAsciiLine(in);
                String appVersionString = FileUtils.readAsciiLine(in);
                String valueCountString = FileUtils.readAsciiLine(in);
                String blank = FileUtils.readAsciiLine(in);
                if (!MAGIC.equals(magic)
                        || !VERSION_1.equals(version)
                        || !Integer.toString(appVersion).equals(appVersionString)
                        || !Integer.toString(valueCount).equals(valueCountString)
                        || !"".equals(blank)) {
                    throw new IOException("unexpected journal header: ["
                            + magic + ", " + version + ", " + valueCountString + ", " + blank + "]");
                }

                while (true) {
                    try {
                        readJournalLine(FileUtils.readAsciiLine(in));
                    } catch (EOFException endOfJournal) {
                        break;
                    }
                }
            } finally {
                FileUtils.closeQuietly(in);
            }
        }catch (Exception e){
            e.printStackTrace();
        }
    }

    /**
     * @see <p>读取journalLine：读取文件中的内容且已去除一些杂质的内容</p>
     * @param line
     * @throws IOException
     */
    private void readJournalLine(String line) throws IOException {
        String[] parts = line.split(" ");
        if (parts.length < 2) {
            throw new IOException("unexpected journal line: " + line);
        }

        String key = parts[1];
        if (parts[0].equals(REMOVE) && parts.length == 2) {
            lruEntries.remove(key);
            return;
        }

        CacheEntry entry = lruEntries.get(key);
        if (entry == null) {
            entry = new CacheEntry(this,key);
            lruEntries.put(key, entry);
        }
        if (parts[0].equals(CLEAN) && parts.length == 2 + valueCount) {
            entry.setmSize(Long.parseLong(parts[2]));
        } else if (parts[0].equals(DIRTY)) {
            // skip
        } else if (parts[0].equals(READ)) {
            // this work was already done by calling mLruEntries.get()
        } else {
            throw new IOException("unexpected journal line: " + line);
        }
    }

    /**
     * @see <p>
     *     计算初始尺寸，并收集垃圾作为打开的一部分
         高速缓存。脏条目被假定为不一致，将被删除。
     * </p>
     */
    private void processJournal() throws IOException {
        FileUtils.deleteIfExists(journalFileTmp);
        for (Iterator<CacheEntry> i = lruEntries.values().iterator(); i.hasNext(); ) {
            CacheEntry cacheEntry = i.next();
            if (!cacheEntry.ismIsEditor()) {
                size += cacheEntry.getmSize();
            } else {
                cacheEntry.delete();
                i.remove();
            }
        }
    }

    /**
     * Creates a new journal that omits redundant information. This replaces the
     * current journal if it exists.
     */
    private synchronized void rebuildJournal() throws IOException {
        if (journalWriter != null) {
            journalWriter.close();
        }

        Writer writer = new BufferedWriter(new FileWriter(journalFileTmp), IO_BUFFER_SIZE);
        writer.write(MAGIC);
        writer.write("\n");
        writer.write(VERSION_1);
        writer.write("\n");
        writer.write(Integer.toString(1));
        writer.write("\n");
        writer.write("\n");

        for (CacheEntry cacheEntry : lruEntries.values()) {
            if (cacheEntry.ismIsEditor()) {
                writer.write(DIRTY + ' ' + cacheEntry.getmKey() + " " + cacheEntry.getmSize() + '\n');
            } else {
                writer.write(CLEAN + ' ' + cacheEntry.getmKey() + " " + cacheEntry.getmSize() + '\n');
            }
        }

        writer.close();
        journalFileTmp.renameTo(journalFile);
        journalWriter = new BufferedWriter(new FileWriter(journalFile, true), IO_BUFFER_SIZE);
    }

    private static void deleteIfExists(File file) throws IOException {
        if (file.exists() && !file.delete()) {
            throw new IOException();
        }
    }

    public synchronized boolean delete(String key) throws IOException {

        checkNotClosed();
        validateKey(key);
        CacheEntry cacheEntry = lruEntries.get(key);
        if (cacheEntry == null) {
            return false;
        }

        // delete at once
        cacheEntry.delete();
        size -= cacheEntry.getmSize();
        cacheEntry.setmSize(0);
        lruEntries.remove(key);
        journalWriter.write(REMOVE + ' ' + FileUtils.generate(cacheEntry.getmKey()) + ' ' + cacheEntry.getmSize() + '\n');
        return true;
    }

    /**
     * Returns the maximum number of bytes that this cache should use to store
     * its data.
     */
    public long maxSize() {
        return maxSize;
    }

    /**
     * Returns the number of bytes currently being used to store the values in
     * this cache. This may be greater than the max size if a background
     * deletion is pending.
     */
    public synchronized long size() {
        return size;
    }



    /**
     * We only rebuild the journal when it will halve the size of the journal
     * and eliminate at least 2000 ops.
     */
    private boolean journalRebuildRequired() {
        final int REDUNDANT_OP_COMPACT_THRESHOLD = 2000;
        return redundantOpCount >= REDUNDANT_OP_COMPACT_THRESHOLD
                && redundantOpCount >= lruEntries.size();
    }
    /**
     * Returns true if this cache has been closed.
     */
    public boolean isClosed() {
        return journalWriter == null;
    }

    private void checkNotClosed() {
        if (journalWriter == null) {
            throw new IllegalStateException("cache is closed");
        }
    }

    /**
     *@see <p>同步数据到DISK 缓存中</p>
     */
    public synchronized void flush() throws IOException {
        checkNotClosed();
        trimToSize();
        journalWriter.flush();

    }

    @Override
    public CacheEntry getEntry(String key) throws IOException {
        checkNotClosed();
        validateKey(key);
        CacheEntry cacheEntry = lruEntries.get(key);
        if (cacheEntry == null) {
            CacheEntry ce=new CacheEntry(this,key);
            lruEntries.put(key,ce);
        }

        trimToSize();
        journalWriter.write(READ + ' ' + cacheEntry.getmKey() + ' ' + cacheEntry.getmSize() + '\n');
        return cacheEntry;
    }

    @Override
    public CacheEntry editor(String key) throws IOException {
        checkNotClosed();
        validateKey(key);
        CacheEntry cacheEntry = lruEntries.get(key);
        if (cacheEntry == null) {
            cacheEntry = new CacheEntry(this, FileUtils.generate(key));
            lruEntries.put(key, cacheEntry);
        }
        //表明当前在写入，写入时，将key生成为MD5的格式，提交时，Value的值为MD5模块下的路径的值
        journalWriter.write(DIRTY + ' ' + cacheEntry.getmKey() + ' ' + cacheEntry.getmSize() + '\n');
        return cacheEntry;
    }

    @Override
    public void abort(CacheEntry cacheEntry) {
        try {
            cacheEntry.abort();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void abort(String key) {
        CacheEntry cacheEntry = lruEntries.get(key);
        if (cacheEntry != null) {
            try {
                cacheEntry.abort();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    @Override
    public void commit(CacheEntry cacheEntry) throws IOException {
        size += cacheEntry.getmSize() - cacheEntry.getmSize();
        journalWriter.write(CLEAN + ' ' + cacheEntry.getmKey() + ' ' + cacheEntry.getmSize() + '\n');
        trimToSize();
    }


    @Override
    public long getCapacity() {
        return maxSize;
    }

    @Override
    public long getSize() {
        return size;
    }

    @Override
    public File getDirectory() {
        return directory;
    }


    @Override
    public boolean has(String key) {
        return lruEntries.containsKey(key);
    }

    @Override
    public void open() throws IOException {

    }

    @Override
    public void clear() throws IOException {
        // abort edit
        for (CacheEntry cacheEntry : new ArrayList<CacheEntry>(lruEntries.values())) {
            if (cacheEntry.ismIsEditor()) {
                cacheEntry.abort();
            }
        }
        lruEntries.clear();
        size = 0;
        // rebuild
        FileUtils.deleteDirectory(directory);
        rebuildJournal();
    }

    /**
     *@see <p>关闭此缓存。存储的值将保持在文件系统中。</p>
     */
    public synchronized void close() throws IOException {
        if (isClosed()) {
            return; // already closed
        }
        for (CacheEntry cacheEntry : new ArrayList<CacheEntry>(lruEntries.values())) {
            if (cacheEntry.ismIsEditor()) {
                cacheEntry.abort();
            }
        }
        trimToSize();
        rebuildJournal();
        journalWriter.close();
        journalWriter = null;
    }

    /**
     * remove files from list, delete files
     */
    private synchronized void trimToSize() {

        while (size > maxSize) {
            Log.d("", "should trim, current is: %s"+ size);
            Map.Entry<String, CacheEntry> toEvict = lruEntries.entrySet().iterator().next();
            String key = toEvict.getKey();
            CacheEntry cacheEntry = toEvict.getValue();
            lruEntries.remove(key);
            size -= cacheEntry.getmSize();
            try {
                journalWriter.write(REMOVE + ' ' + FileUtils.generate(cacheEntry.getmKey()) + ' ' + cacheEntry.getmSize() + '\n');

                if (lruEntries.containsKey(cacheEntry.getmKey())) {
                    return;
                }
                cacheEntry.delete();
            } catch (IOException e) {
                e.printStackTrace();
            }

        }
    }

    /**
     * Closes the cache and deletes all of its stored values. This will delete
     * all files in the cache directory including files that weren't created by
     * the cache.
     * @see <p>关闭缓存和删除其所有的存储值。这将删除缓存目录包括不是由*高速缓存中创建的文件*所有文件。</p>
     */
    public void delete() throws IOException {
        close();
        //使用linux命令快速删除该目录
        FileUtils.deleteDirectory(directory);

    }
    //校验key是否合法
    private void validateKey(String key) {
        if (key.contains(" ") || key.contains("\n") || key.contains("\r")) {
            throw new IllegalArgumentException(
                    "keys must not contain spaces or newlines: \"" + key + "\"");
        }
    }

    /**
     * @see <p>从流中取出数据,写入文件</p>
     * @param in
     * @return
     * @throws IOException
     */
    private static String inputStreamToString(InputStream in) throws IOException {
        return FileUtils.readFully(new InputStreamReader(in, UTF_8));
    }

}