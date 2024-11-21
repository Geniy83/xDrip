package com.eveningoutpost.dexdrip.services;

import static com.eveningoutpost.dexdrip.cgm.medtrum.Medtrum.getDeviceInfoStringFromLegacy;

import android.annotation.TargetApi;
import android.app.AlarmManager;
import android.app.PendingIntent;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.preference.PreferenceManager;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import androidx.annotation.Nullable;

import com.eveningoutpost.dexdrip.BluetoothScan;
import com.eveningoutpost.dexdrip.R;
import com.eveningoutpost.dexdrip.g5model.BluetoothServices;
import com.eveningoutpost.dexdrip.g5model.Extensions;
import com.eveningoutpost.dexdrip.g5model.Transmitter;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.utilitymodels.StatusItem;

import java.io.UnsupportedEncodingException;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.UUID;

import lecho.lib.hellocharts.util.ChartUtils;
import lombok.Getter;
import lombok.Setter;
import lombok.val;

/**
 * POCTech collector
 * Created by Evgeniy on 10/11/2024.
 */


public class PoctechCollectionService extends G5BaseService {

    public final static String TAG = PoctechCollectionService.class.getSimpleName();

    public static boolean keep_running = false;
    private static volatile String transmitterID;
    private static volatile String transmitterMAC;

    private BluetoothAdapter bluetoothAdapter;
    private BluetoothLeScanner bluetoothLeScanner;
    private boolean scanning = false;
    private Handler handler = new Handler();





    @Override
    public void onCreate() {
        super.onCreate();
        if (keep_running) {
            UserError.Log.d(TAG, "Вызов метода onCreate()");
            initializeBluetoothAdapter();
            startScanning(20000);
        }

    }


    private void initializeBluetoothAdapter() {
        BluetoothManager bluetoothManager = (BluetoothManager) getSystemService(BLUETOOTH_SERVICE);
        if (bluetoothManager != null) {
            bluetoothAdapter = bluetoothManager.getAdapter();
            bluetoothLeScanner = bluetoothAdapter.getBluetoothLeScanner();
        }
    }

    public void startScanning(long scanPeriod) {
        if (bluetoothAdapter == null || !bluetoothAdapter.isEnabled()) {
            UserError.Log.d(TAG, "Bluetooth не включен");
            return;
        }
        scanning = true;
        bluetoothLeScanner.startScan(scanCallback);

        handler.postDelayed(this::stopScanning, scanPeriod);
    }

    public void stopScanning() {
        if (scanning) {
            bluetoothLeScanner.stopScan(scanCallback);
            scanning = false;
            UserError.Log.d(TAG, "Сканирование остановлено");
        }
    }

    private final ScanCallback scanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            UserError.Log.d(TAG, "Найдено устройство: " + device.getName() + ", Адрес: " + device.getAddress());
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            super.onBatchScanResults(results);
            for (ScanResult result : results) {
                BluetoothDevice device = result.getDevice();
                UserError.Log.d(TAG, "Найдено устройство: " + device.getName() + ", Адрес: " + device.getAddress());
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            super.onScanFailed(errorCode);
            UserError.Log.d(TAG, "Ошибка сканирования: " + errorCode);
        }
    };


    //Чтение модели трансмиттера из строки ввода в меню
    private static void init_tx_id() {
        val TXID_PREF = "poct_txid";
        val txid = Pref.getString(TXID_PREF, "NULL");
        val txid_filtered = txid.trim();
        transmitterID = txid_filtered;
        if (!txid.equals(txid_filtered)) {
            Pref.setString(TXID_PREF, txid_filtered);
            UserError.Log.wtf(TAG, "Had to fix invalid txid: :" + txid + ": -> :" + txid_filtered + ":");
        }
    }

    public static String getTransmitterID() {
        if (transmitterID == null) {
            init_tx_id();
        }
        return transmitterID;
    }


    // Вкладка состояния трансмиттера Poctech
    public static List<StatusItem> megaStatus() {
        final List<StatusItem> l = new ArrayList<>();
        l.add(new StatusItem("Phone Service State", lastState));
        final String tx_id = Pref.getStringDefaultBlank("poct_txid");
        l.add(new StatusItem("Transmitter ID", tx_id));
        return  l;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

}