package com.quickbtconn;

import android.app.PendingIntent;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothProfile;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.drawable.AdaptiveIconDrawable;
import android.graphics.drawable.ColorDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.widget.RemoteViews;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;

public class BluetoothWidgetProvider extends AppWidgetProvider {
    static final String ACTION_CONNECT = "com.quickbtconn.action.CONNECT";
    private static final int BREATH_DIM = 0x4DFFFFFF; // 30% 白色,呼吸的暗端
    private static final int FRAME_MS = 200;
    private static final int FRAME_COUNT = 40; // 8 秒连接窗口
    private static final int DISCONNECT_POLLS = 10;
    private static final ExecutorService WORKER = Executors.newCachedThreadPool();

    @Override
    public void onUpdate(Context context, AppWidgetManager manager, int[] ids) {
        for (int id : ids) update(context, manager, id, WidgetStore.connected(context, id), false);
    }

    @Override
    public void onDeleted(Context context, int[] ids) {
        for (int id : ids) WidgetStore.delete(context, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context context, AppWidgetManager manager, int id,
            Bundle options) {
        update(context, manager, id, WidgetStore.connected(context, id), false);
    }

    @Override
    public void onReceive(Context context, Intent intent) {
        super.onReceive(context, intent);
        String action = intent.getAction();
        if (ACTION_CONNECT.equals(action)) {
            int id = intent.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,
                    AppWidgetManager.INVALID_APPWIDGET_ID);
            toggle(context, id);
            return;
        }
        if (BluetoothAdapter.ACTION_STATE_CHANGED.equals(action)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            if (state == BluetoothAdapter.STATE_OFF || state == BluetoothAdapter.STATE_TURNING_OFF) {
                updateAll(context, null, false);
            }
            return;
        }
        BluetoothDevice device = readDevice(intent);
        if (device == null) return;
        if (BluetoothDevice.ACTION_ACL_CONNECTED.equals(action)) {
            updateAll(context, device.getAddress(), true);
        } else if (BluetoothDevice.ACTION_ACL_DISCONNECTED.equals(action)) {
            updateAll(context, device.getAddress(), false);
        } else if (intent.hasExtra(BluetoothProfile.EXTRA_STATE)) {
            int state = intent.getIntExtra(BluetoothProfile.EXTRA_STATE, BluetoothProfile.STATE_DISCONNECTED);
            if (state == BluetoothProfile.STATE_CONNECTED) updateAll(context, device.getAddress(), true);
            if (state == BluetoothProfile.STATE_DISCONNECTED
                    && !BluetoothConnector.isConnected(context, device)) {
                updateAll(context, device.getAddress(), false);
            }
        }
    }

    private void toggle(Context originalContext, int id) {
        Context context = originalContext.getApplicationContext();
        String address = WidgetStore.address(context, id);
        BluetoothAdapter adapter = BluetoothConnector.adapter(context);
        if (address == null || adapter == null || !adapter.isEnabled() || !BluetoothConnector.hasPermission(context)) {
            WidgetStore.setConnected(context, id, false);
            update(context, AppWidgetManager.getInstance(context), id, false, false);
            return;
        }
        final BluetoothDevice device;
        try {
            device = adapter.getRemoteDevice(address);
        } catch (IllegalArgumentException | SecurityException exception) {
            update(context, AppWidgetManager.getInstance(context), id, false, false);
            return;
        }

        PendingResult pending = goAsync();
        WORKER.execute(() -> {
            AppWidgetManager manager = AppWidgetManager.getInstance(context);
            try {
                if (BluetoothConnector.isConnected(context, device)) {
                    disconnectDevice(context, manager, id, device);
                } else {
                    connectDevice(context, manager, id, device);
                }
            } finally {
                pending.finish();
            }
        });
    }

    private static void connectDevice(Context context, AppWidgetManager manager, int id, BluetoothDevice device) {
        AtomicBoolean signalled = new AtomicBoolean(false);
        try {
            BluetoothConnector.connect(context, device, connected -> {
                if (connected) {
                    signalled.set(true);
                    WidgetStore.setConnected(context, id, true);
                    update(context, manager, id, true, false);
                } else if (signalled.get()) {
                    WidgetStore.setConnected(context, id, false);
                    update(context, manager, id, false, false);
                }
            });
            // 只发一次切换到呼吸状态的更新;ViewFlipper 在启动器进程内自行播放动画,
            // 轮询期间不再刷新界面,避免逐帧 updateAppWidget 造成的卡顿。
            update(context, manager, id, false, true);
            for (int frame = 0; frame < FRAME_COUNT && !signalled.get(); frame++) {
                if (BluetoothConnector.isConnected(context, device)) {
                    signalled.set(true);
                    break;
                }
                SystemClock.sleep(FRAME_MS);
            }
            boolean connected = signalled.get() || BluetoothConnector.isConnected(context, device);
            WidgetStore.setConnected(context, id, connected);
            update(context, manager, id, connected, false);
        } catch (RuntimeException exception) {
            WidgetStore.setConnected(context, id, false);
            update(context, manager, id, false, false);
        }
    }

    private static void disconnectDevice(Context context, AppWidgetManager manager, int id, BluetoothDevice device) {
        BluetoothConnector.disconnect(context, device);
        for (int frame = 0; frame < DISCONNECT_POLLS; frame++) {
            if (!BluetoothConnector.isConnected(context, device)) break;
            SystemClock.sleep(400);
        }
        boolean connected = BluetoothConnector.isConnected(context, device);
        WidgetStore.setConnected(context, id, connected);
        update(context, manager, id, connected, false);
    }

    static void update(Context context, AppWidgetManager manager, int id, boolean connected, boolean connecting) {
        if (!WidgetStore.configured(context, id)) return;
        int style = WidgetStore.style(context, id);
        RemoteViews views = new RemoteViews(context.getPackageName(),
                style == WidgetStore.STYLE_CLEAR ? R.layout.widget_clear : R.layout.widget_shaped);
        if (style != WidgetStore.STYLE_CLEAR) {
            views.setImageViewBitmap(R.id.widget_background,
                    shapedBackground(context, manager, id, style));
        }
        String state = connected ? "已连接" : connecting ? "正在连接" : "未连接";
        int iconRes = DeviceIcons.resolve(WidgetStore.icon(context, id), WidgetStore.name(context, id));
        views.setViewVisibility(R.id.widget_icon, connecting ? View.GONE : View.VISIBLE);
        views.setViewVisibility(R.id.widget_breath, connecting ? View.VISIBLE : View.GONE);
        views.setImageViewResource(R.id.widget_icon, iconRes);
        views.setInt(R.id.widget_icon, "setColorFilter",
                connected || connecting ? Color.WHITE : Color.rgb(115, 119, 128));
        if (connecting) {
            views.setImageViewResource(R.id.widget_breath_a, iconRes);
            views.setImageViewResource(R.id.widget_breath_b, iconRes);
            views.setInt(R.id.widget_breath_a, "setColorFilter", Color.WHITE);
            views.setInt(R.id.widget_breath_b, "setColorFilter", BREATH_DIM);
        }
        views.setContentDescription(R.id.widget_icon, WidgetStore.name(context, id) + "，" + state);
        views.setContentDescription(R.id.widget_breath, WidgetStore.name(context, id) + "，" + state);

        Intent click = new Intent(context, BluetoothWidgetProvider.class)
                .setAction(ACTION_CONNECT)
                .setData(Uri.parse("quickbtconn://connect/" + id))
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, id)
                .addFlags(Intent.FLAG_RECEIVER_FOREGROUND);
        PendingIntent pendingIntent = PendingIntent.getBroadcast(context, id, click,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        views.setOnClickPendingIntent(R.id.widget_root, pendingIntent);
        manager.updateAppWidget(id, views);
    }

    private static Bitmap shapedBackground(Context context, AppWidgetManager manager, int id, int style) {
        Bundle options = manager.getAppWidgetOptions(id);
        int spanDp = Math.min(options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 0),
                options.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, 0));
        if (spanDp <= 0) spanDp = 64;
        float density = context.getResources().getDisplayMetrics().density;
        int size = Math.round((spanDp - 12) * density); // 12dp = 布局四周内边距
        if (size <= 0) size = Math.round(52 * density);
        // AdaptiveIconDrawable 的 bounds 即可见蒙版区域：mask 缩放到整个 bounds，
        // 图层内部自动外扩，绘制结果与系统应用图标形状一致并铺满小组件。
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        int color = style == WidgetStore.STYLE_BLUE
                ? context.getColor(R.color.bg_blue) : context.getColor(R.color.bg_dark);
        AdaptiveIconDrawable shape = new AdaptiveIconDrawable(
                new ColorDrawable(color), new ColorDrawable(Color.TRANSPARENT));
        shape.setBounds(0, 0, size, size);
        shape.draw(canvas);
        return bitmap;
    }

    private static void updateAll(Context context, String address, boolean connected) {
        AppWidgetManager manager = AppWidgetManager.getInstance(context);
        int[] ids = manager.getAppWidgetIds(new ComponentName(context, BluetoothWidgetProvider.class));
        for (int id : ids) {
            String saved = WidgetStore.address(context, id);
            if (address == null || address.equals(saved)) {
                WidgetStore.setConnected(context, id, connected);
                update(context, manager, id, connected, false);
            }
        }
    }

    private static BluetoothDevice readDevice(Intent intent) {
        if (Build.VERSION.SDK_INT >= 33) {
            return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE, BluetoothDevice.class);
        }
        //noinspection deprecation
        return intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
    }
}
