package com.richsjeson.cache.interf;

import com.richsjeson.cache.disk.CacheEntry;

import java.io.File;
import java.io.IOException;

/**
 * Created by richsjeson on 16-3-16.
 * @see <p>磁盘缓存策略模型</p>
 */
public interface DiskFacade {

    /**
     * Check if has this key
     *
     * @param key
     * @return
     */
    public boolean has(String key);

    /**
     * open disk cache
     *
     * @throws java.io.IOException
     */
    public void open() throws IOException;

    /**
     * clear all data
     *
     * @throws java.io.IOException
     */
    public void clear() throws IOException;

    /**
     * close the cache
     *
     * @throws java.io.IOException
     */
    public void close() throws IOException;

    /**
     * flush data to dish
     */
    public void flush() throws IOException;

    /**
     * @param key
     * @return
     * @throws java.io.IOException
     */
    public CacheEntry getEntry(String key) throws IOException;

    /**
     * begin edit an {@CacheEntry }
     *
     * @param key
     * @return
     * @throws java.io.IOException
     */
    public CacheEntry editor(String key) throws IOException;

    /**
     * abort edit
     *
     * @param cacheEntry
     */
    public void abort(CacheEntry cacheEntry);

    /**
     * abort edit by key
     *
     * @param key
     */
    public void abort(String key);

    /**
     * abort edit by key
     */
    public void commit(CacheEntry cacheEntry) throws IOException;

    /**
     * delete if key exist, under edit can not be deleted
     *
     * @param key
     * @return
     */
    public boolean delete(String key) throws IOException;

    public long getCapacity();

    public long getSize();

    public File getDirectory();
}
