package org.bitcoinj.utils;
import android.content.Context;
import android.content.SharedPreferences;

/**
 * Android保存文件工具类
 * @author zm
 * 
 */
public class SharedPreferencesUtils {

	private SharedPreferences preferences;

    private static SharedPreferencesUtils spUtils;

    public SharedPreferencesUtils(Context context, String fileName) {
        preferences = context.getSharedPreferences(fileName, Context.MODE_PRIVATE);
    }
    public SharedPreferences getSharedPreferences(){
    	return preferences;
    }


    public synchronized static SharedPreferencesUtils getDefaultPreferences(Context context){
        if(spUtils == null){
            spUtils = new SharedPreferencesUtils(context, "default");
        }
        return spUtils;
    }

    /**
     * *************** get ******************
     */

    public String get(String key, String defalutValue) {
        return preferences.getString(key, defalutValue);
    }

    public boolean get(String key, boolean defalutValue) {
        return preferences.getBoolean(key, defalutValue);
    }

    public float get(String key, float defalutValue) {
        return preferences.getFloat(key, defalutValue);
    }

    public int getInt(String key, int defalutValue) {
        return preferences.getInt(key, defalutValue);
    }

    public long get(String key, long defalutValue) {
        return preferences.getLong(key, defalutValue);
    }

    /**
     * *************** put ******************
     */
    public void put(String key, String value) {
        if (value == null) {
            preferences.edit().remove(key).commit();
        } else {
            preferences.edit().putString(key, value).commit();
        }
    }

    public void put(String key, boolean value) {
        preferences.edit().putBoolean(key, value).commit();
    }

    public void put(String key, float value) {
        preferences.edit().putFloat(key, value).commit();
    }

    public void put(String key, long value) {
        preferences.edit().putLong(key, value).commit();
    }

    public void putInt(String key, int value) {
        preferences.edit().putInt(key, value).commit();
    }
    
}
