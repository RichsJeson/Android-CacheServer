/*******************************************************************************
 * Copyright 2011-2014 Sergey Tarasevich
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *******************************************************************************/
package com.richsjeson.cache.utils;

import com.richsjeson.cache.disk.CacheEntry;
import com.richsjeson.cache.interf.DiskFacade;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @see <p></p>
 */
public final class StringUtils {


	private StringUtils() {
	}

	/**
	 * @see <p>保存数据至文件</p>
	 * @param mDiskLruCache
	 * @param value
	 */
	public static void put(DiskFacade mDiskLruCache,String key,String value){
		try {
			CacheEntry cacheEntry =mDiskLruCache.editor(key);
			if(cacheEntry != null){
				OutputStream os= (OutputStream) cacheEntry.newOutputStream(0);
				try {
					boolean isSuccess = StringUtils.write(os, value);
					if (isSuccess) {
						cacheEntry.commit();
						mDiskLruCache.flush();
					} else {
						cacheEntry.abort();
					}
				}catch (Exception e){
					FileUtils.closeQuietly(os);
				}
			}
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @see <p>根据Key,查找日志表记录，并从key所对应的缓存文件读取数据</p>
	 * @param mDiskLruCache
	 * @param key
	 * @return
	 */
	public static InputStream get(DiskFacade mDiskLruCache,String key){
		try{
//			if (false==mDiskLruCache.has(key)) {
//				return null;
//			}
			try {
				CacheEntry cacheEntry = mDiskLruCache.getEntry(FileUtils.generate(key));
				return cacheEntry.getInputStream();
			} catch (IOException e) {
				e.printStackTrace();
				return null;
			}

		}catch (Exception e){
			e.printStackTrace();
			return null;
		}
	}

	/**
	 * @see <p>保存数据到文件中</p>
	 * @param os
	 * @param content
	 * @return
	 */
	private  static boolean write(OutputStream os,String content){
		try {
			os.write(content.getBytes());
			os.flush();
			return true;
		}catch (Exception e){
			return false;
		}finally {
			FileUtils.closeQuietly(os);
		}
	}


}
