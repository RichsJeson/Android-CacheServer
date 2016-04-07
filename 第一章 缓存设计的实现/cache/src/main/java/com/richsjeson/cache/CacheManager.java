package com.richsjeson.cache;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Environment;
import android.test.mock.MockContext;
import android.util.Log;

import com.richsjeson.cache.disk.DiskLruCache;
import com.richsjeson.cache.interf.CacheFacade;
import com.richsjeson.cache.interf.DiskFacade;
import com.richsjeson.cache.interf.SystemFacade;
import com.richsjeson.cache.memory.LruCache;
import com.richsjeson.cache.utils.BitmapUtils;
import com.richsjeson.cache.utils.FileUtils;
import com.richsjeson.cache.utils.MemoryUtils;
import com.richsjeson.cache.utils.StringUtils;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;


/**
 * Created by richsjeson on 16-3-16.
 * @see <p>缓存管理
 * 执行操作：
 * 1.将常用数据倒入缓存。
 * 2.如果缓存中，key所对应的数据不存在，则从本地DiskCache中取出数据倒入缓存。
 * 3.清除所有缓存.当APP退出时，执行该操作。
 * 一级缓存处理方式：
 *  1）经常读取（read读）
 *  2）经常数据变更（write写，数据写入首次往二级缓存（DiskCache中写入））
 * 执行方式：
 *  1.先存入磁盘缓存中,当读取数据时，将磁盘缓存中的数据倒入到内存缓存中,内存缓存根据LRU策略进行缓存管理。（可持久化的缓存）
 *  2.多久执行一次缓存更新？假定时间，读取缓存。
 *  3.当用户读取缓存时，也读取磁盘缓存中的缓存记录，如果用户缓存中的时间戳为最新，则视为文件缓存中的缓存
 * </p>
 */
public class CacheManager implements SystemFacade {

    private Context mContext;

    private CacheFacade mLRUCache;

    private static CacheManager manager;
    /**
     * 硬盘缓存核心类。
     */
    private DiskFacade mDiskLruCache;

    private int memoryTotal;

    private ConnectivityManager connectivity ;

    private CacheManager(Context mContext){
        //从虚拟机中获取应分配的缓存大小
        memoryTotal= (int) Runtime.getRuntime().maxMemory();

        if(memoryTotal<=0){
            memoryTotal=2*1024*1024;
        }
        //给LRU分配缓存。
        mLRUCache=new LruCache(memoryTotal / 2);
        this.mContext=mContext;
        try {
            mDiskLruCache= DiskLruCache.open(getDiskCacheDir(mContext, "cache_priv"), 1, 2, 2 * 1024 * 1024 * 1024L);
        } catch (IOException e) {
            e.printStackTrace();
        }
        connectivity=(ConnectivityManager) mContext.getSystemService(MockContext.CONNECTIVITY_SERVICE);
    }


    public static CacheManager getInstance(Context context){

        if(manager==null){
            synchronized (CacheManager.class){
                return manager=new CacheManager(context);
            }
        }
        return null;
    }

    public synchronized void put(String key, Object value) {
        //如果缓存中的数据已存在,那么就更新内存缓存
        if (mLRUCache.getEntry(key) != null) {
            MemoryUtils.put(mLRUCache,key,value);
        }
        //根据策略进行put,如果是图片，则将图片直接存放到DiskLruCache中，当要获取图片时，从DiskLruCache中获取,此bitmap是根据分辨率下的压缩后的大小
        if(value instanceof  String){
            //将数据写入文件
            StringUtils.put(mDiskLruCache,key, (String) value);
        }else if(value instanceof Bitmap){
            //要将Bitmap数据流写入文件
            BitmapUtils.put(mDiskLruCache,key, (Bitmap) value);
        }
    }


    public synchronized Object get(String key) {
        //如果缓存中的数据已存在,那么就更新内存缓存
        if (mLRUCache.getEntry(key) != null) {
            return MemoryUtils.get(mLRUCache, key);
        }else {
            //根据策略进行put,如果是图片，则将图片直接存放到DiskLruCache中，当要获取图片时，从DiskLruCache中获取,此bitmap是根据分辨率下的压缩后的大小
            Object ins=StringUtils.get(mDiskLruCache,key);
            if(ins != null && ins instanceof InputStream){
                try {
                    String content= FileUtils.readString((InputStream) ins);
                    put(key,content);
                    return  content;
                } catch (IOException e) {
                    e.printStackTrace();
                    return null;
                }
            }else{
                return BitmapUtils.get(mDiskLruCache,key);
            }
        }

    }


    public void memoryAll() {
        mLRUCache.memoryAll();
//        try {
//            mDiskLruCache.clear();
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
    }


    public void diskMemoryAll(){
        try {
            mDiskLruCache.clear();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * 获取当前应用程序的版本号。
     */
    public int getAppVersion(Context context) {
        try {
            PackageInfo info = context.getPackageManager().getPackageInfo(context.getPackageName(),
                    0);
            return info.versionCode;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
        return 1;
    }

    /**
     * 根据传入的uniqueName获取硬盘缓存的路径地址。
     */
    public File getDiskCacheDir(Context context, String uniqueName) {
        String cachePath;
        if (Environment.MEDIA_MOUNTED.equals(Environment.getExternalStorageState())
                || !Environment.isExternalStorageRemovable()) {
            cachePath = context.getExternalCacheDir().getPath();
        } else {
            cachePath = context.getCacheDir().getPath();
        }
        return new File(cachePath + File.separator + uniqueName);
    }

    @Override
    public long currentTimeMillis() {
        //获取当前系统的时间
        return System.currentTimeMillis();
    }

    @Override
    public NetworkInfo getActiveNetworkInfo() {
        //获取当前系统状态
        if (connectivity == null) {
            Log.w(this.getClass().getName(), "couldn't get connectivity manager");
            return null;
        }

        final NetworkInfo activeInfo = connectivity.getActiveNetworkInfo();
        if (activeInfo == null ) {
            Log.v(this.getClass().getName(), "network is not available");
        }
        return activeInfo;
    }

    @Override
    public boolean isActiveNetworkMetered() {
        //是否超过流量的上限
        return false;
    }

    @Override
    public boolean isNetworkRoaming() {
        //是否漫游
        return false;
    }

    @Override
    public Long getMaxBytesOverMobile() {
        return null;
    }



}
