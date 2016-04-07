package com.richsjeson.cache.memory;

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
 *
 *
 */



import android.graphics.Bitmap;
import com.richsjeson.cache.interf.CacheFacade;

import java.io.IOException;
import java.lang.ref.ReferenceQueue;

import java.lang.ref.WeakReference;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Static library version of {@link android.util.LruCache}. Used to write apps
 * that run on API levels prior to 12. When running on API level 12 or above,
 * this implementation is still used; it does not try to switch to the
 * framework's implementation. See the framework SDK documentation for a class
 * overview.
 *
 * @see <p>使用系统自带的LRU缓存，来管理整个应用的内存处理情况</p>
 *
 * 1）用户信息缓存
 * 2）图片信息缓存
 * 3）下载信息缓存
 * 4）常用的一些变量 缓存，命中率过高的键值对直接从缓存中读取。命中低的缓存通过LRU算法自动清理缓存。
 * 5）当缓存达到一定的情况时，自动清除缓存。
 */
public class LruCache implements CacheFacade {

    private final LinkedHashMap<String, MemoryEntry> map;
    /** Size of this cache in units. Not necessarily the number of elements. */
    private int size;
    private int maxSize;

    private int putCount;
    private int createCount;
    private int evictionCount;
    private int hitCount;
    private int missCount;
    /**
     * @serialField  <p>将对象放入引用队列，等待垃圾回收</p>
     */
    private ReferenceQueue<MemoryEntry> mQueue = new ReferenceQueue<MemoryEntry>();

    /**
     * @param maxSize for caches that do not override {@link #sizeOf}, this is
     *     the maximum number of entries in the cache. For all other caches,
     *     this is the maximum sum of the sizes of the entries in this cache.
     */
    public LruCache(final int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }
        this.maxSize = maxSize;
        this.size=size();
        this.map = new LinkedHashMap<String, MemoryEntry>(0, 0.75f, true){

            @Override
            protected boolean removeEldestEntry(Entry<String, MemoryEntry> eldest) {
                //当缓存占满时，立即从缓存中将未使用的数据移除
                if(size()<maxSize){
                    return false;
                }else{
                    return true;
                }
            }
        };
    }


    private class WeakReferenceMemory extends WeakReference<MemoryEntry> {

        private String key;

        public String getKey() {
            return key;
        }

        public WeakReferenceMemory(MemoryEntry memoryEntry, ReferenceQueue<MemoryEntry> queue) {
            super(memoryEntry, queue);
            this.key=memoryEntry.getmKey();
        }

    }
    /**
     * Sets the size of the cache.
     *
     * @param maxSize The new maximum size.
     */
    public void resize(int maxSize) {
        if (maxSize <= 0) {
            throw new IllegalArgumentException("maxSize <= 0");
        }

        synchronized (this) {
            this.maxSize = maxSize;
        }
        trimToSize(maxSize);
    }

    @Override
    public Object put(String key) {
        return null;
    }

    /**
     * Returns the value for {@code key} if it exists in the cache or can be
     * created by {@code #create}. If a value was returned, it is moved to the
     * head of the queue. This returns null if a value is not cached and cannot
     * be created.
     */
    public final MemoryEntry getEntry(String key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        MemoryEntry mapValue;
        synchronized (this) {
            mapValue = map.get(key);
            if (mapValue != null) {
                hitCount++;
                return mapValue;
            }
            missCount++;
        }

        /*
         * Attempt to create a value. This may take a long time, and the map
         * may be different when create() returns. If a conflicting value was
         * added to the map while create() was working, we leave that value in
         * the map and release the created value.
         */

        MemoryEntry createdValue = create(key);
        if (createdValue == null) {
            return null;
        }

        synchronized (this) {
            createCount++;
            mapValue = map.put(key, createdValue);

            if (mapValue != null) {
                // There was a conflict so undo that last put
                map.put(key, mapValue);
            } else {
                size += safeSizeOf(key, createdValue);
            }
        }

        if (mapValue != null) {
            entryRemoved(false, key, createdValue, mapValue);
            return mapValue;
        } else {
            trimToSize(maxSize);
            return createdValue;
        }
    }

    @Override
    public void memoryAll() {
        trimToSize(-1);
    }

    @Override
    public void delete(String key) throws Exception {
        MemoryEntry cacheEntry = map.get(key);
        if (cacheEntry == null) {
            throw new Exception("cache entry is null,i can't delete");
        }else{
            remove(key);
        }
    }
    @Override
    public void abort(MemoryEntry memoryEntry) {
        try {
            memoryEntry.abort();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    @Override
    public MemoryEntry editor(String key) {
        MemoryEntry cacheEntry = map.get(key);
        if (cacheEntry == null) {
            cacheEntry = new MemoryEntry(this, key);
            put(key, cacheEntry);
        }
        return cacheEntry;
    }

    @Override
    public boolean has(String key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        MemoryEntry mapValue;
        synchronized (this) {
            mapValue = map.get(key);
            if (mapValue != null) {
                return true;
            }else{
                return false;
            }
        }
    }

    /**
     * Caches {@code value} for {@code key}. The value is moved to the head of
     * the queue.
     *
     * @return the previous value mapped by {@code key}.
     */
    public final Object put(String key, MemoryEntry value) {
        if (key == null || value == null) {
            throw new NullPointerException("key == null || value == null");
        }

        Object previous;
        synchronized (this) {
            putCount++;
            size += safeSizeOf(key, value);
            previous = map.put(key, value);
            if (previous != null) {
                size -= safeSizeOf(key, previous);
            }
        }

        if (previous != null) {
            entryRemoved(false, key, previous, value);
        }

        trimToSize(maxSize);
        return previous;
    }

    /**
     * Remove the eldest entries until the total of remaining entries is at or
     * below the requested size.
     *
     * @param maxSize the maximum size of the cache before returning. May be -1
     *            to evict even 0-sized elements.
     */
    public void trimToSize(int maxSize) {
        while (true) {
            Object key;
            Object value;
            synchronized (this) {
                if (size < 0 || (map.isEmpty() && size != 0)) {
                    throw new IllegalStateException(getClass().getName()
                            + ".sizeOf() is reporting inconsistent results!");
                }

                if (size <= maxSize || map.isEmpty()) {
                    break;
                }
                //如果size>=maxSize的话,则移除，最后一条缓存(最近未使用的缓存键值)
                Map.Entry<String, MemoryEntry> toEvict = map.entrySet().iterator().next();
                key = toEvict.getKey();
                value = toEvict.getValue();
                map.remove(key);
                size -= safeSizeOf(key, value);
                evictionCount++;
                //将当前指针回收
                toEvict=null;
            }

            entryRemoved(true, key, value, null);
        }
    }

    /**
     * Removes the entry for {@code key} if it exists.
     *
     * @return the previous value mapped by {@code key}.
     */
    public final Object remove(Object key) {
        if (key == null) {
            throw new NullPointerException("key == null");
        }

        Object previous;
        synchronized (this) {
            previous = map.remove(key);
            if (previous != null) {
                size -= safeSizeOf(key, previous);
            }
        }

        if (previous != null) {
            entryRemoved(false, key, previous, null);
        }

        return previous;
    }

    /**
     * Called for entries that have been evicted or removed. This method is
     * invoked when a value is evicted to make space, removed by a call to
     * {@link #remove}, or replaced by a call to {@link #put}. The default
     * implementation does nothing.
     *
     * <p>The method is called without synchronization: other threads may
     * access the cache while this method is executing.
     *
     * @param evicted true if the entry is being removed to make space, false
     *     if the removal was caused by a {@link #put} or {@link #remove}.
     * @param newValue the new value for {@code key}, if it exists. If non-null,
     *     this removal was caused by a {@link #put}. Otherwise it was caused by
     *     an eviction or a {@link #remove}.
     */
    protected void entryRemoved(boolean evicted, Object key, Object oldValue, Object newValue) {

        //执行内存回收的操作
        WeakReferenceMemory weakRefrence=null;
        while((weakRefrence=(WeakReferenceMemory)mQueue.poll()) !=null){
            MemoryEntry entry = (MemoryEntry) map.get(key);
            map.remove(entry);
            System.out.println("对象ID : " + entry.getmKey() + "已经被JVM回收");

        }

    }

    /**
     * Called after a cache miss to compute a value for the corresponding key.
     * Returns the computed value or null if no value can be computed. The
     * default implementation returns null.
     *
     * <p>The method is called without synchronization: other threads may
     * access the cache while this method is executing.
     *
     * <p>If a value for {@code key} exists in the cache when this method
     * returns, the created value will be released with {@link #entryRemoved}
     * and discarded. This can occur when multiple threads request the same key
     * at the same time (causing multiple values to be created), or when one
     * thread calls {@link #put} while another is creating a value for the same
     * key.
     */
    protected MemoryEntry create(Object key) {
        return null;
    }

    private int safeSizeOf(Object key, Object value) {
        int result = sizeOf(key, value);
        if (result < 0) {
            throw new IllegalStateException("Negative size: " + key + "=" + value);
        }
        return result;
    }

    /**
     * Returns the size of the entry for {@code key} and {@code value} in
     * user-defined units.  The default implementation returns 1 so that size
     * is the number of entries and max size is the maximum number of entries.
     *
     * <p>An entry's size must not change while it is in the cache.
     */
    protected int sizeOf(Object key, Object value) {
        if(value instanceof Bitmap){
            Bitmap bitmap= (Bitmap) value;
            return  bitmap.getRowBytes() * bitmap.getHeight();
        }
        return 1;
    }

    /**
     * -1 will evict 0-sized elements
     */
    public final void evictAll() {
        trimToSize(-1);
    }

    /**
     * For caches that do not override {@link #sizeOf}, this returns the number
     * of entries in the cache. For all other caches, this returns the sum of
     * the sizes of the entries in this cache.
     */
    public synchronized final int size() {
        return size;
    }

    /**
     * For caches that do not override {@link #sizeOf}, this returns the maximum
     * number of entries in the cache. For all other caches, this returns the
     * maximum sum of the sizes of the entries in this cache.
     */
    public synchronized final int maxSize() {
        return maxSize;
    }

    /**
     * Returns the number of times {@link #} returned a value that was
     * already present in the cache.
     */
    public synchronized final int hitCount() {
        return hitCount;
    }

    /**
     * Returns the number of times {@link #} returned null or required a new
     * value to be created.
     */
    public synchronized final int missCount() {
        return missCount;
    }

    /**
     * Returns the number of times {@link #create(Object)} returned a value.
     */
    public synchronized final int createCount() {
        return createCount;
    }

    /**
     * Returns the number of times {@link #put} was called.
     */
    public synchronized final int putCount() {
        return putCount;
    }

    /**
     * Returns the number of values that have been evicted.
     */
    public synchronized final int evictionCount() {
        return evictionCount;
    }

    /**
     * Returns a copy of the current contents of the cache, ordered from least
     * recently accessed to most recently accessed.
     */
    public synchronized final Map<Object, Object> snapshot() {
        return new LinkedHashMap<Object, Object>(map);
    }

    @Override
    public synchronized final String toString() {
        int accesses = hitCount + missCount;
        int hitPercent = accesses != 0 ? (100 * hitCount / accesses) : 0;
        return String.format("LruCache[maxSize=%d,hits=%d,misses=%d,hitRate=%d%%]",
                maxSize, hitCount, missCount, hitPercent);
    }


}