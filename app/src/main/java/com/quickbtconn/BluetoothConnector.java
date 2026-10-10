package com.quickbtconn;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothStatusCodes;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;

import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

final class BluetoothConnector {
    interface Callback { void onResult(boolean connected); }

    private static final Map<String, BluetoothGatt> GATT_CONNECTIONS = new ConcurrentHashMap<>();

    private BluetoothConnector() {}

    static boolean hasPermission(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.S
                || context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED;
    }

    static BluetoothAdapter adapter(Context context) {
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        return manager == null ? null : manager.getAdapter();
    }

    static boolean isConnected(Context context, BluetoothDevice device) {
        if (!hasPermission(context) || device == null) return false;
        try {
            Method method = device.getClass().getMethod("isConnected", int.class);
            Object bredr = method.invoke(device, BluetoothDevice.TRANSPORT_BREDR);
            Object le = method.invoke(device, BluetoothDevice.TRANSPORT_LE);
            if (Boolean.TRUE.equals(bredr) || Boolean.TRUE.equals(le)) return true;
        } catch (ReflectiveOperationException | SecurityException ignored) {
            // Hidden APIs can be blocked on older Android versions.
        }
        BluetoothManager manager = context.getSystemService(BluetoothManager.class);
        if (manager != null) {
            try {
                return manager.getConnectionState(device, BluetoothProfile.GATT)
                        == BluetoothProfile.STATE_CONNECTED;
            } catch (SecurityException ignored) { }
        }
        return false;
    }

    @SuppressLint("MissingPermission") // hasPermission() is checked before every Bluetooth call.
    static void connect(Context context, BluetoothDevice device, Callback callback) {
        if (!hasPermission(context) || device == null) {
            callback.onResult(false);
            return;
        }
        if (isConnected(context, device)) {
            callback.onResult(true);
            return;
        }
        if (device.getType() == BluetoothDevice.DEVICE_TYPE_LE) {
            connectGatt(context, device, callback);
            return;
        }
        try {
            if (Build.VERSION.SDK_INT >= 37) {
                int result = device.connect();
                if (result != BluetoothStatusCodes.SUCCESS) callback.onResult(false);
                return;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            callback.onResult(false);
            return;
        }

        // On Android 16 and earlier there is no public universal classic-Bluetooth
        // connect API. Try the vendor-exposed method first, then the public BLE API.
        try {
            Method method = device.getClass().getMethod("connect");
            Object result = method.invoke(device);
            if (!(result instanceof Boolean) || (Boolean) result) {
                return;
            }
        } catch (ReflectiveOperationException | SecurityException ignored) { }

        if (device.getType() == BluetoothDevice.DEVICE_TYPE_DUAL) {
            connectGatt(context, device, callback);
        } else {
            callback.onResult(false);
        }
    }

    @SuppressLint("MissingPermission") // hasPermission() is checked before every Bluetooth call.
    static void disconnect(Context context, BluetoothDevice device) {
        if (!hasPermission(context) || device == null) return;
        // 先释放本应用持有的 GATT 连接,BLE 设备可能因此直接断链。
        BluetoothGatt gatt = GATT_CONNECTIONS.remove(device.getAddress());
        if (gatt != null) {
            try { gatt.disconnect(); } catch (SecurityException ignored) { }
        }
        try {
            if (Build.VERSION.SDK_INT >= 37) {
                device.disconnect();
                return;
            }
        } catch (SecurityException | IllegalArgumentException ignored) {
            return;
        }
        // Android 16 及更早版本只能尽力调用隐藏接口,由系统决定是否放行。
        try {
            Method method = device.getClass().getMethod("disconnect");
            method.invoke(device);
        } catch (ReflectiveOperationException | SecurityException ignored) { }
    }

    private static void connectGatt(Context context, BluetoothDevice device, Callback callback) {
        try {
            BluetoothGatt old = GATT_CONNECTIONS.remove(device.getAddress());
            if (old != null) old.close();
            BluetoothGatt gatt = device.connectGatt(context.getApplicationContext(), false,
                    new BluetoothGattCallback() {
                        @Override
                        public void onConnectionStateChange(BluetoothGatt value, int status, int newState) {
                            if (newState == BluetoothProfile.STATE_CONNECTED) {
                                GATT_CONNECTIONS.put(device.getAddress(), value);
                                callback.onResult(true);
                            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                                GATT_CONNECTIONS.remove(device.getAddress());
                                value.close();
                                callback.onResult(false);
                            }
                        }
                    }, BluetoothDevice.TRANSPORT_AUTO);
            if (gatt != null) GATT_CONNECTIONS.put(device.getAddress(), gatt);
            else callback.onResult(false);
        } catch (SecurityException exception) {
            callback.onResult(false);
        }
    }
}
