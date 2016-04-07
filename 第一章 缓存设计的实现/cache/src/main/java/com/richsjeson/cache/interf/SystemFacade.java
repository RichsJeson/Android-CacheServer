package com.richsjeson.cache.interf;

import android.net.NetworkInfo;

/**
 * Created by richsjeson on 16-1-24.
 * @see <p>通讯的系统策略类</p>
 */
public interface SystemFacade {
    /**
     * @see System#currentTimeMillis()
     */
    public long currentTimeMillis();

    /**
     * @return 网络状态判定.
     */
    public NetworkInfo getActiveNetworkInfo();

    public boolean isActiveNetworkMetered();

    /**
     * @see <p></p>是否开启漫游
     */
    public boolean isNetworkRoaming();

    /**
     * @return @see 网络最大限速
     */
    public Long getMaxBytesOverMobile();
}
