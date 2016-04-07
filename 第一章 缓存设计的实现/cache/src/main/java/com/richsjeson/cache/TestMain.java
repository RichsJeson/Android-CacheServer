package com.richsjeson.cache;

import com.richsjeson.cache.test.EmployeeCache;

/**
 * Created by richsjeson on 16-3-22.
 */
public class TestMain {

    public static void main(String []args){

        EmployeeCache cache = EmployeeCache.getInstance();
        for (int i = 0; i < 60000; i++) {
            System.out.println("执行操作");
            cache.getEmployee(String.valueOf(i));
        }
        cache.clearCache();

    }



}
