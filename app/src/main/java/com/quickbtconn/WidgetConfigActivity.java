package com.quickbtconn;

import android.Manifest;
import android.annotation.SuppressLint;
import android.annotation.TargetApi;
import android.appwidget.AppWidgetManager;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.companion.AssociationInfo;
import android.companion.AssociationRequest;
import android.companion.BluetoothDeviceFilter;
import android.companion.CompanionDeviceManager;
import android.content.Intent;
import android.content.IntentSender;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.material.textfield.MaterialAutoCompleteTextView;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public class WidgetConfigActivity extends AppCompatActivity {
    private static final int REQUEST_BLUETOOTH = 11;

    private int widgetId = AppWidgetManager.INVALID_APPWIDGET_ID;
    private MaterialAutoCompleteTextView dropdown;
    private MaterialAutoCompleteTextView iconDropdown;
    private TextView status;
    private Button save;
    private RadioGroup styles;
    private DeviceItem pendingDevice;
    private int pendingStyle;
    private String pendingIcon;
    private DeviceItem selected;
    private String selectedIcon = DeviceIcons.AUTO;
    private final List<DeviceItem> devices = new ArrayList<>();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        setContentView(R.layout.activity_config);
        widgetId = getIntent().getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID);
        if (widgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish();
            return;
        }
        dropdown = findViewById(R.id.device_dropdown);
        iconDropdown = findViewById(R.id.icon_dropdown);
        status = findViewById(R.id.device_status);
        save = findViewById(R.id.save_button);
        styles = findViewById(R.id.style_group);
        save.setOnClickListener(this::saveWidget);
        restoreStyle();
        setupIconPicker();

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
                && !BluetoothConnector.hasPermission(this)) {
            status.setText(R.string.permission_needed);
            save.setEnabled(false);
            requestPermissions(new String[]{Manifest.permission.BLUETOOTH_CONNECT}, REQUEST_BLUETOOTH);
        } else {
            loadDevices();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(requestCode, permissions, results);
        if (requestCode == REQUEST_BLUETOOTH && BluetoothConnector.hasPermission(this)) loadDevices();
        else if (requestCode == REQUEST_BLUETOOTH) {
            status.setText(R.string.permission_needed);
            save.setEnabled(false);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == 12 && resultCode != RESULT_OK && !isFinishing()) {
            associationFailed("未确认设备，无法保存这个小组件。");
        }
    }

    private void loadDevices() {
        devices.clear();
        BluetoothAdapter adapter = BluetoothConnector.adapter(this);
        if (adapter == null) {
            status.setText("此设备不支持蓝牙。");
            save.setEnabled(false);
            return;
        }
        if (!adapter.isEnabled()) {
            status.setText("请先开启蓝牙，然后重新打开小组件设置。");
            save.setEnabled(false);
            return;
        }
        try {
            Set<BluetoothDevice> bonded = adapter.getBondedDevices();
            for (BluetoothDevice device : bonded) devices.add(new DeviceItem(device));
        } catch (SecurityException exception) {
            status.setText(R.string.permission_needed);
        }
        devices.sort(Comparator.comparing(item -> item.name.toLowerCase(Locale.getDefault())));
        ArrayAdapter<DeviceItem> list = new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, devices);
        dropdown.setAdapter(list);
        dropdown.setOnItemClickListener((parent, view, position, id) -> selected = devices.get(position));
        selectSavedDevice();
        save.setEnabled(!devices.isEmpty());
        if (devices.isEmpty()) status.setText(R.string.no_devices);
        else status.setText("共 " + devices.size() + " 台已配对设备；● 表示当前已连接。");
    }

    private void selectSavedDevice() {
        if (devices.isEmpty()) return;
        String saved = WidgetStore.address(this, widgetId);
        DeviceItem pick = null;
        if (saved != null) {
            for (DeviceItem item : devices) {
                if (saved.equals(item.address)) {
                    pick = item;
                    break;
                }
            }
        }
        if (pick == null) pick = devices.get(0);
        selected = pick;
        dropdown.setText(pick.toString(), false);
    }

    private void setupIconPicker() {
        String[] entries = getResources().getStringArray(R.array.icon_entries);
        iconDropdown.setAdapter(new ArrayAdapter<>(this,
                android.R.layout.simple_list_item_1, entries));
        iconDropdown.setOnItemClickListener((parent, view, position, id) ->
                selectedIcon = DeviceIcons.KEYS[position]);
        String key = WidgetStore.icon(this, widgetId);
        int index = 0;
        for (int i = 0; i < DeviceIcons.KEYS.length; i++) {
            if (DeviceIcons.KEYS[i].equals(key)) {
                index = i;
                break;
            }
        }
        selectedIcon = DeviceIcons.KEYS[index];
        iconDropdown.setText(entries[index], false);
    }

    private void restoreStyle() {
        int style = WidgetStore.style(this, widgetId);
        styles.check(style == WidgetStore.STYLE_CLEAR ? R.id.style_clear
                : style == WidgetStore.STYLE_BLUE ? R.id.style_blue : R.id.style_dark);
    }

    private void saveWidget(View ignored) {
        DeviceItem item = selected;
        if (item == null) {
            Toast.makeText(this, R.string.no_devices, Toast.LENGTH_LONG).show();
            return;
        }
        int checked = styles.getCheckedRadioButtonId();
        pendingStyle = checked == R.id.style_blue ? WidgetStore.STYLE_BLUE
                : checked == R.id.style_clear ? WidgetStore.STYLE_CLEAR : WidgetStore.STYLE_DARK;
        pendingIcon = selectedIcon;
        pendingDevice = item;
        if (Build.VERSION.SDK_INT >= 37 && item.type != BluetoothDevice.DEVICE_TYPE_LE
                && !isAssociated(item.address)) {
            requestAssociation(item.address);
        } else {
            completeSave();
        }
    }

    private boolean isAssociated(String address) {
        if (Build.VERSION.SDK_INT < 33) return false;
        CompanionDeviceManager manager = getSystemService(CompanionDeviceManager.class);
        if (manager == null) return false;
        for (AssociationInfo info : manager.getMyAssociations()) {
            if (info.getDeviceMacAddress() != null
                    && address.equalsIgnoreCase(info.getDeviceMacAddress().toString())) return true;
        }
        return false;
    }

    @TargetApi(33)
    private void requestAssociation(String address) {
        if (!getPackageManager().hasSystemFeature(PackageManager.FEATURE_COMPANION_DEVICE_SETUP)) {
            Toast.makeText(this, "系统不支持配套设备授权，将使用兼容连接方式。", Toast.LENGTH_LONG).show();
            completeSave();
            return;
        }
        save.setEnabled(false);
        status.setText("请在系统弹窗中确认此设备，以允许小组件主动连接。");
        AssociationRequest request = new AssociationRequest.Builder()
                .addDeviceFilter(new BluetoothDeviceFilter.Builder().setAddress(address).build())
                .setSingleDevice(true)
                .build();
        CompanionDeviceManager manager = getSystemService(CompanionDeviceManager.class);
        manager.associate(request, getMainExecutor(), new CompanionDeviceManager.Callback() {
            @Override
            public void onAssociationPending(IntentSender launcher) {
                try {
                    startIntentSenderForResult(launcher, 12, null, 0, 0, 0);
                } catch (IntentSender.SendIntentException exception) {
                    associationFailed("无法打开设备确认窗口。");
                }
            }

            @Override
            public void onAssociationCreated(AssociationInfo associationInfo) {
                completeSave();
            }

            @Override
            public void onFailure(CharSequence error) {
                associationFailed(error == null ? "设备关联失败。" : error.toString());
            }
        });
    }

    private void associationFailed(String message) {
        save.setEnabled(true);
        status.setText(message);
        Toast.makeText(this, message, Toast.LENGTH_LONG).show();
    }

    private void completeSave() {
        if (pendingDevice == null || isFinishing()) return;
        WidgetStore.save(this, widgetId, pendingDevice.address, pendingDevice.name,
                pendingStyle, pendingDevice.connected, pendingIcon);
        BluetoothWidgetProvider.update(this, AppWidgetManager.getInstance(this), widgetId,
                pendingDevice.connected, false);

        Intent result = new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widgetId);
        setResult(RESULT_OK, result);
        finish();
    }

    private final class DeviceItem {
        final String name;
        final String address;
        final boolean connected;
        final int type;

        @SuppressLint("MissingPermission") // Activity checks BLUETOOTH_CONNECT before loading devices.
        DeviceItem(BluetoothDevice device) {
            String candidate;
            try { candidate = device.getName(); }
            catch (SecurityException exception) { candidate = null; }
            name = candidate == null || candidate.trim().isEmpty() ? "未命名设备" : candidate;
            address = device.getAddress();
            type = device.getType();
            connected = BluetoothConnector.isConnected(WidgetConfigActivity.this, device);
        }

        @Override public String toString() {
            return (connected ? "●  " : "○  ") + name + "  ·  " + address;
        }
    }
}
