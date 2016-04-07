package com.richsjeson.cache.disk;

import android.util.Log;

import com.richsjeson.cache.interf.DiskFacade;
import com.richsjeson.cache.utils.FileUtils;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FilterOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;

/**
 * Created by richsjeson on 16-3-16.
 * @see <p>磁盘缓存实体类
 * 1.读取流
 * 2.存储流
 * 3.存储标记
 * </p>
 */
public class CacheEntry{

    private static final Charset UTF_8 = Charset.forName("UTF-8");
    /**
     * @serialField  <p>字符串</p>
     */
    private final String mKey;
    /**
     * @serialField  <p>执行策略</p>
     */
    private DiskFacade facade;
    /**
     * @serialField  <p>是否发生异常</p>
     */
    private boolean hasErrors;
    /**
     * @serialField  <p>缓存的类型</p>
     */
    private String type;
    /**
     * @serialField  <p>该文件是否被修改</p>
     */
    private boolean mIsEditor=false;
    /**
     * @see <p>获取缓存文件的大小</p>
     */
    private long mSize;
    /**
     * @see <p></p>
     */
    private long oldSize;

    public CacheEntry(DiskFacade facade,String mKey){

        this.mKey=mKey;
        this.facade=facade;
    }

    private static  String inputStreamToString(InputStream in) throws IOException {
        return FileUtils.readFully(new InputStreamReader(in,UTF_8));
    }

    /**
     * Returns an unbuffered input stream to read the last committed value,
     * or null if no value has been committed.
     */
    public InputStream newInputStream(int index) throws IOException {

        synchronized (facade) {
            if (!mIsEditor) {
                mIsEditor=true;
            }
            return new FileInputStream(getCleanFile(index));
        }
    }

    public OutputStream newOutputStream(int index) throws FileNotFoundException {
        synchronized (facade) {
            if (!mIsEditor) {
                mIsEditor=true;
            }
            return new FaultHidingOutputStream(new FileOutputStream(getDirtyFile(index)));
        }
    }


    /**
     * @see <p>设置value值到缓存中</p>
     */
    public CacheEntry set(int index, String key) throws IOException {
        Writer writer = null;
        try {
            writer = new OutputStreamWriter(newOutputStream(index), UTF_8);
            writer.write(key);
        } finally {
            FileUtils.closeQuietly(writer);
        }
        return this;
    }


    /**
     * Returns the last committed value as a string, or null if no value
     * has been committed.
     */
    public String getString(int index) throws IOException {
        InputStream in = newInputStream(index);
        return in != null ? inputStreamToString(in) : null;
    }


    /**
     * @see <p>执行事务的提交</p>
     */
    public void commit() throws IOException {
        if (!mIsEditor) {
            throw new IOException("CacheEntry has been closed.");
        }
        if (hasErrors) {
            facade.delete(mKey);
        } else {
            //获取操作，取出临时的文件
            File dirty =getDirtyFile(0) ;
            if (dirty.exists()) {
                File clean = getCacheFile();
                dirty.renameTo(clean);
                oldSize = mSize;
                mSize = clean.length();
                facade.commit(this);
            } else {
                abort();
            }
        }
        mIsEditor = false;
    }

    /**
     * @see <p>执行事务的回滚</p>
     */
    public void abort() throws IOException {
        if (!mIsEditor) {
            throw new IOException("CacheEntry has been closed.");
        }
        //移除文件
        FileUtils.deleteIfExists(getTempFile());
        facade.abort(this);
    }
    /**
     * @see <p>获取一个文件条目</p>
     * @param i
     * @return
     */
    private File getCleanFile(int i) {
        Log.i(this.getClass().getName(),"mKey");
        return new File(facade.getDirectory(), mKey + "." + "_" + i);
    }

    public File getDirtyFile(int i) {
        return new File(facade.getDirectory(), mKey+ "." + i + ".tmp");
    }

    /**
     * @see <p>执行删除操作</p>
     */
    public boolean delete() throws IOException {

        if (mIsEditor) {
            throw new IOException("Try to delete an cache entry that has been being editing.");
        }
        FileUtils.deleteIfExists(getCacheFile());
        FileUtils.deleteIfExists(getTempFile());
        return true;
    }

    public boolean isReadable() {
        return getCacheFile().exists();
    }

    /**
     * Returns an unbuffered input stream to read the last committed value,
     * or null if no value has been committed.
     */
    public InputStream getInputStream() throws IOException {
        synchronized (facade) {
            if (!isReadable()) {
                return null;
            }
            return new FileInputStream(getCacheFile());
        }
    }

    /**
     * @see <p>获取操作</p>
     */
    private class FaultHidingOutputStream extends FilterOutputStream {
        private FaultHidingOutputStream(OutputStream out) {
            super(out);
        }

        @Override
        public void write(int oneByte) {
            try {
                out.write(oneByte);
            } catch (IOException e) {
                hasErrors = true;
            }
        }

        @Override
        public void write(byte[] buffer, int offset, int length) {
            try {
                out.write(buffer, offset, length);
            } catch (IOException e) {
                hasErrors = true;
            }
        }

        @Override
        public void close() {
            try {
                out.close();
            } catch (IOException e) {
                hasErrors = true;
            }
        }

        @Override
        public void flush() {
            try {
                out.flush();
            } catch (IOException e) {
                hasErrors = true;
            }
        }
    }

    /**
     * @see <p>获取临时文件</p>
     * @return
     */
    public File getTempFile() {
        return new File(facade.getDirectory(), mKey + ".tmp");
    }

    /**
     * @see <p>获取缓存文件</p>
     * @return
     */
    public File getCacheFile() {
        Log.i(this.getClass().getName(),"mkey:="+mKey);
        return new File(facade.getDirectory(), mKey);
    }

    public String getmKey() {
        return mKey;
    }

    public long getmSize() {
        return mSize;
    }

    public String getType() {
        return type;
    }

    public void setmSize(long mSize) {
        this.mSize = mSize;
    }
    public boolean ismIsEditor() {
        return mIsEditor;
    }

    public void setmIsEditor(boolean mIsEditor) {
        this.mIsEditor = mIsEditor;
    }
}
