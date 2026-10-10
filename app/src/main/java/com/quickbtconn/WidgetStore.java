package com.quickbtconn;

import android.appwidget.AppWidgetManager;
import android.content.Context;
import android.content.SharedPreferences;

final class WidgetStore {
    static final int STYLE_DARK = 0;
    static final int STYLE_BLUE = 1;
    static final int STYLE_CLEAR = 2;

    private static final String PREFS = "bluetooth_widgets";

    private WidgetStore() {}

    static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    static void save(Context context, int id, String address, String name, int style,
            boolean connected, String icon) {
        prefs(context).edit()
                .putString(key(id, "address"), address)
                .putString(key(id, "name"), name)
                .putInt(key(id, "style"), style)
                .putBoolean(key(id, "connected"), connected)
                .putString(key(id, "icon"), icon)
                .apply();
    }

    static String address(Context context, int id) {
        return prefs(context).getString(key(id, "address"), null);
    }

    static String name(Context context, int id) {
        return prefs(context).getString(key(id, "name"), "蓝牙设备");
    }

    static int style(Context context, int id) {
        return prefs(context).getInt(key(id, "style"), STYLE_DARK);
    }

    static String icon(Context context, int id) {
        return prefs(context).getString(key(id, "icon"), DeviceIcons.AUTO);
    }

    static boolean connected(Context context, int id) {
        return prefs(context).getBoolean(key(id, "connected"), false);
    }

    static void setConnected(Context context, int id, boolean value) {
        prefs(context).edit().putBoolean(key(id, "connected"), value).apply();
    }

    static void delete(Context context, int id) {
        SharedPreferences.Editor editor = prefs(context).edit();
        editor.remove(key(id, "address"));
        editor.remove(key(id, "name"));
        editor.remove(key(id, "style"));
        editor.remove(key(id, "connected"));
        editor.remove(key(id, "icon"));
        editor.apply();
    }

    static boolean configured(Context context, int id) {
        return id != AppWidgetManager.INVALID_APPWIDGET_ID && address(context, id) != null;
    }

    private static String key(int id, String field) {
        return "widget_" + id + "_" + field;
    }
}
