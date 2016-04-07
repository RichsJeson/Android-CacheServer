// CacheProvider.aidl
package com.richsjeson.cache;

// Declare any non-default types here with import statements
/**
*
* @see <p>cache 接口</p>
**/
interface CacheProvider {
   /**
   * @see <p>执行初始化配置</p>
   **/
   void init();
   /**
   * @see <p>执行Put请求</p>
   **/
   void put(in String key,in Bundle value);
   /**
     * @see <p>执行get请求</p>
   **/
   Bundle get(in String key,in Bundle value);

   void memoryAll();

   void diskMemoryAll();

}
