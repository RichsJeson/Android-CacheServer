package com.richsjeson.cache.memory;

import com.richsjeson.cache.interf.CacheFacade;
import com.richsjeson.cache.utils.FileUtils;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;

import java.io.IOException;

import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;

import java.lang.ref.ReferenceQueue;
import java.lang.ref.WeakReference;

/**
 * Created by richsjeson on 16-3-17.
 * @see <p>内存缓存
 * 1)数据序列化
 * 2)数据的反序列化
 * </p>
 */
public class MemoryEntry{
    /**
     * @serialField <p>key的值</p>
     */
    private String mKey;
    /**
     * @serialField  <p>是否发生异常</p>
     */
    private boolean hasErrors;
    /**
     * @serialField  <p>缓存的类型</p>
     */
    private String mType;
    /**
     * @serialField  <p>该文件是否被修改</p>
     */
    private boolean mIsEditor=false;

    private  CacheFacade facade;
    /**
     * @serialField  <p>使用序列化操作</p>
     */
    private  ByteArrayOutputStream mByteOutStream;

    private  ObjectOutputStream mObjectStream;

    /**
     * @serialField  <p>使用序列化操作</p>
     */
    private  ByteArrayInputStream mByteInputStream;

    private  ObjectInputStream mInputStream;

    private String obj;

    public MemoryEntry(CacheFacade facade,String mKey){
        this.mKey=mKey;
        this.facade=facade;
    }


    public String getmKey() {
        return mKey;
    }

    public void setmKey(String mKey) {
        this.mKey = mKey;
    }

    /**
     * @see <p>执行事务的提交</p>
     */
    public void commit() throws Exception {
        if (!mIsEditor) {
            throw new IOException("MemoryEntry has been closed.");
        }
        if (hasErrors) {
            facade.delete(mKey);
        }else{
            abort();
        }

        mIsEditor = false;
    }

    /**
     * @see <p>执行数据序列化</p>
     * @param obj
     * @return
     * @throws IOException
     */
    public void newOutputStream(Object obj) throws IOException {

        synchronized (facade) {
            if (!mIsEditor) {
                mIsEditor=true;
            }
            try {
                mByteOutStream = new ByteArrayOutputStream();
                mObjectStream = new ObjectOutputStream(
                        mByteOutStream);
                mObjectStream.writeObject(obj);
                this.obj=java.net.URLEncoder.encode(mByteOutStream.toString("ISO-8859-1"), "UTF-8");
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                FileUtils.closeQuietly(mByteOutStream);
                FileUtils.closeQuietly(mObjectStream);
            }
        }
    }


    /**
     * @see <p>执行反序列化</p>
     * @param
     * @return
     * @throws IOException
     */
    public Object newInputStream() throws IOException {

        synchronized (facade) {
            if (!mIsEditor) {
                mIsEditor=true;
            }
            try {
                String redStr = java.net.URLDecoder.decode(this.obj, "UTF-8");
                mByteInputStream = new ByteArrayInputStream(
                        redStr.getBytes("ISO-8859-1"));
                mInputStream= new ObjectInputStream(
                        mByteInputStream);
               return mInputStream.readObject();
            }catch (Exception e){
                e.printStackTrace();
            }finally {
                FileUtils.closeQuietly(mByteInputStream);
                FileUtils.closeQuietly(mInputStream);
            }
        }
        return null;
    }


    /**
     * @see <p>执行事务的回滚</p>
     */
    public void abort() throws IOException {
        if (!mIsEditor) {
            throw new IOException("CacheEntry has been closed.");
        }
    }

}
