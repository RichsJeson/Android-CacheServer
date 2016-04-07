package com.richsjeson.cache;

import android.test.mock.MockContext;
import android.util.Log;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mock;

import static org.junit.Assert.*;

/**
 * Created by richsjeson on 16-3-22.
 */
public class CacheManagerTest {
    public CacheManager cacheManager;
    @Before
    public void setUp() throws Exception {

        cacheManager= CacheManager.getInstance(new MockContext());
    }

    @After
    public void tearDown() throws Exception {
//        cacheManager.memoryAll();
//        cacheManager=null;

    }

    @Test
    public void testGetInstance() throws Exception {
        int maxTotal=(int) Runtime.getRuntime().maxMemory();
        System.out.println(this.getClass().getName() + ".maxTotal:=%d" + maxTotal);
    }

    @Test
    public void testPut() throws Exception {
        UserSSO userSSO=new UserSSO();
        userSSO.setPasswd("bbsda");
        userSSO.setUserName("hello");
        cacheManager.put("123", userSSO);
        cacheManager.put("133", userSSO);
        cacheManager.put("134",userSSO);
        cacheManager.put("153",userSSO);
        cacheManager.put("121",userSSO);
        cacheManager.put("111",userSSO);
    }

    @Test
    public void testGet() throws Exception {
        testPut();
        System.gc();
        System.out.println("cacheManager:="+cacheManager);
        UserSSO obj= (UserSSO) cacheManager.get("123");
        UserSSO objdd= (UserSSO) cacheManager.get("153");
        UserSSO objs= (UserSSO) cacheManager.get("111");
        System.out.println("obj:="+obj.toString());
//        cacheManager.memoryAll();
    }

    @Test
    public void testMemoryAll() throws Exception {

    }

    @Test
    public void testGetAppVersion() throws Exception {

    }

    @Test
    public void testGetDiskCacheDir() throws Exception {

    }

    @Test
    public void testCurrentTimeMillis() throws Exception {

    }

    @Test
    public void testGetActiveNetworkInfo() throws Exception {

    }

    @Test
    public void testIsActiveNetworkMetered() throws Exception {

    }

    @Test
    public void testIsNetworkRoaming() throws Exception {

    }

    @Test
    public void testGetMaxBytesOverMobile() throws Exception {

    }
}