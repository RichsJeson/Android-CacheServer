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
import com.richsjeson.cache.disk.DiskLruCache;
import com.richsjeson.cache.interf.CacheFacade;
import com.richsjeson.cache.memory.LruCache;
import com.richsjeson.cache.memory.MemoryEntry;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * @see <p></p>
 */
public final class MemoryUtils {


	private MemoryUtils() {
	}

	/**
	 * @see <p>保存数据至文件</p>
	 * @param facade
	 * @param value
	 * @param  mKey
	 */
	public static void put(CacheFacade facade,String mKey,Object value){
		try {
			MemoryEntry cacheEntry =facade.editor(mKey);
			cacheEntry.newOutputStream(value);
		} catch (IOException e) {
			e.printStackTrace();
		}
	}

	/**
	 * @see <p>根据Key,查找日志表记录，并从key所对应的缓存文件读取数据</p>
	 * @param
	 * @param key
	 * @return
	 */
	public static Object get(CacheFacade facade,String key){
		try{
			if (!facade.has(key)) {
				return null;
			}
			try {
				MemoryEntry cacheEntry = facade.getEntry(key);
				return cacheEntry.newInputStream();
			} catch (IOException e) {
				e.printStackTrace();
			}
		}catch (Exception e){
			e.printStackTrace();
		}
		return null;
	}
}
