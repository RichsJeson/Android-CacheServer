package com.richsjeson.cache;

import android.app.Service;
import android.content.Intent;
import android.graphics.Bitmap;
import android.os.Bundle;
import android.os.IBinder;
import android.os.Parcelable;
import android.os.RemoteException;
import android.support.annotation.Nullable;

import java.io.Serializable;

/**
 * Created by richsjeson on 16-3-23.
 * @see <p>缓存服务框架</p>
 */
public class CacheService extends Service {

    private static CacheManager cacheManager;

    private static Bundle bundle;


    public CacheService(){
        cacheManager=CacheManager.getInstance(this.getApplicationContext());
    }


    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return  cacheProvider;
    }

    private static CacheProvider.Stub cacheProvider=new CacheProvider.Stub(){

        @Override
        public void init() throws RemoteException {

        }

        @Override
        public void put(String key, Bundle value) throws RemoteException {
            cacheManager.put(key,value.get(key));
        }

        @Override
        public Bundle get(String key, Bundle value) throws RemoteException {
            bundle=new Bundle();
            if(cacheManager.get(key) instanceof Bitmap){
                bundle.putParcelable(key, (Parcelable) cacheManager.get(key));
            }else{
                bundle.putSerializable(key, (Serializable) cacheManager.get(key));
            }
            return bundle;
        }

        @Override
        public void memoryAll() throws RemoteException {
            cacheManager.memoryAll();
        }

        @Override
        public void diskMemoryAll() throws RemoteException {
            cacheManager.diskMemoryAll();
        }
    };
}
