package com.richsjeson.cache;

import java.io.Serializable;

/**
 * Created by richsjeson on 16-3-22.
 */
public class UserSSO implements Serializable {
    private String userName;
    private String passwd;

    public String getUserName() {
        return userName;
    }

    public void setUserName(String userName) {
        this.userName = userName;
    }

    public String getPasswd() {
        return passwd;
    }

    public void setPasswd(String passwd) {
        this.passwd = passwd;
    }

    @Override
    public String toString() {
        return "userName:="+userName+",password=:"+passwd;

    }
}
