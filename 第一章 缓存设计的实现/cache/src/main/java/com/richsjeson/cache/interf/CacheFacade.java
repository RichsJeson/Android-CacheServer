package com.richsjeson.cache.interf;
import com.richsjeson.cache.memory.MemoryEntry;

/**
 * Created by richsjeson on 16-3-16.
 */
public interface CacheFacade {
    /**
     * @see <p>缓存策略类</p>
     */
    public Object put(String key);
    /**
     * @see <p>获取缓存中的键值</p>
     */
    public MemoryEntry getEntry(String key);
    /**
     * @see <p>清除所有的缓存</p>
     */
    public void memoryAll();

    //删除当前key所对应的数据
    public void delete(String key) throws InterruptedException, Exception;

    /**
     * @see <p>执行回调</p>
     * @param memoryEntry
     */
    void abort(MemoryEntry memoryEntry);

    /**
     * @see <p>获取数据</p>
     * @param mKey
     * @return
     */
    MemoryEntry editor(String mKey);
    /**
     * @see <p>是否存在</p>
     * @param key
     * @return
     */
    boolean has(String key);
}
