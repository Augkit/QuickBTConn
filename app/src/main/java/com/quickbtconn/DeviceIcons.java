package com.quickbtconn;

import java.util.Locale;

final class DeviceIcons {
    static final String AUTO = "auto";
    /** 顺序必须与 icon_entries 字符串数组一致。 */
    static final String[] KEYS = {
            AUTO, "bluetooth", "earbuds", "headset", "watch", "speaker", "keyboard", "mouse", "car"
    };

    private DeviceIcons() {}

    static int drawable(String key) {
        switch (key) {
            case "earbuds": return R.drawable.ic_earbuds;
            case "headset": return R.drawable.ic_headset;
            case "watch": return R.drawable.ic_watch;
            case "speaker": return R.drawable.ic_speaker;
            case "keyboard": return R.drawable.ic_keyboard;
            case "mouse": return R.drawable.ic_mouse;
            case "car": return R.drawable.ic_car;
            default: return R.drawable.ic_bluetooth;
        }
    }

    static int resolve(String key, String deviceName) {
        return drawable(AUTO.equals(key) ? autoKey(deviceName) : key);
    }

    /** 按设备名猜测图标,识别不了退回蓝牙图标。 */
    private static String autoKey(String deviceName) {
        if (deviceName == null) return "bluetooth";
        String name = deviceName.toLowerCase(Locale.ROOT);
        if (match(name, "airpod", "buds", "earbud", "pods", "耳机")) return "earbuds";
        if (match(name, "watch", "手表", "手环")) return "watch";
        if (match(name, "keyboard", "keychron", "mx keys", "键盘")) return "keyboard";
        if (match(name, "mouse", "mx master", "mx anywhere", "鼠标")) return "mouse";
        if (match(name, "car", "audi", "bmw", "benz", "mercedes", "volkswagen", "volvo",
                "tesla", "toyota", "honda", "ford", "porsche", "cadillac", "buick",
                "chevrolet", "byd", "zeekr", "车载")) return "car";
        if (match(name, "speaker", "soundlink", "soundbar", "jbl", "boom", "pill", "xtreme",
                "partybox", "音箱", "音响")) return "speaker";
        if (match(name, "headphone", "headset", "wh-", "1000xm", "quietcomfort", "arctis",
                "hyperx", "studio", "头戴")) return "headset";
        return "bluetooth";
    }

    private static boolean match(String name, String... keywords) {
        for (String keyword : keywords) if (name.contains(keyword)) return true;
        return false;
    }
}
