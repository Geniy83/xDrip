package com.eveningoutpost.dexdrip;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.PowerManager;
import android.preference.PreferenceManager;
import com.eveningoutpost.dexdrip.models.BgReading;
import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.Sensor;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Intents;
import com.eveningoutpost.dexdrip.utilitymodels.Pref;
import com.eveningoutpost.dexdrip.utils.DexCollectionType;
import com.eveningoutpost.dexdrip.utils.GlucoseSmoother;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.Iterator;

public class DashxAppReceiver extends BroadcastReceiver {

    private static final String TAG = "Dashx";
    private static final int DATA_PREVIEW_MAX = 280;
    private static final boolean debug = false;
    private static SharedPreferences prefs;
    private static final Object lock = new Object();

    private static String jsonObjectKeyList(JSONObject o) {
        final StringBuilder sb = new StringBuilder();
        for (Iterator<String> it = o.keys(); it.hasNext(); ) {
            if (sb.length() > 0) {
                sb.append(", ");
            }
            sb.append(it.next());
        }
        return sb.toString();
    }

    private static String previewString(String s, int maxChars) {
        if (s == null) {
            return "null";
        }
        if (s.length() <= maxChars) {
            return s;
        }
        return s.substring(0, maxChars) + "… (" + s.length() + " chars total)";
    }

    /** Logs action, flags, optional package/component; full extras key list with safe values. */
    private static void logIntentForDashx(Intent intent) {
        if (intent == null) {
            UserError.Log.w(TAG, "logIntentForDashx: intent is null");
            return;
        }
        final StringBuilder sb = new StringBuilder();
        sb.append("intent action=").append(intent.getAction());
        sb.append(" flags=0x").append(Integer.toHexString(intent.getFlags()));
        if (intent.getPackage() != null) {
            sb.append(" pkg=").append(intent.getPackage());
        }
        if (intent.getComponent() != null) {
            sb.append(" cmp=").append(intent.getComponent().flattenToShortString());
        }
        if (intent.getDataString() != null) {
            sb.append(" data=").append(previewString(intent.getDataString(), 120));
        }
        UserError.Log.d(TAG, sb.toString());

        final Bundle bundle = intent.getExtras();
        if (bundle == null) {
            UserError.Log.d(TAG, "intent extras: <none>");
            return;
        }
        UserError.Log.d(TAG, "intent extras keys (" + bundle.size() + "): " + bundle.keySet());
        for (String key : bundle.keySet()) {
            final Object value = bundle.get(key);
            if (value == null) {
                UserError.Log.d(TAG, "  extra[\"" + key + "\"] = null");
            } else if ("data".equals(key) && value instanceof String) {
                final String data = (String) value;
                UserError.Log.d(TAG, "  extra[\"data\"] length=" + data.length() + " preview=" + previewString(data, DATA_PREVIEW_MAX));
            } else {
                UserError.Log.d(TAG, "  extra[\"" + key + "\"] = " + value + " (" + value.getClass().getSimpleName() + ")");
            }
        }
    }

    @Override
    public void onReceive(final Context context, final Intent intent) {
        new Thread() {
            @Override
            public void run() {
                PowerManager.WakeLock wl = JoH.getWakeLock("dashx-receiver", 60000);
                synchronized (lock) {
                    try {

                        UserError.Log.d(TAG, "onReceive start (worker thread)");
                        logIntentForDashx(intent);
                        JoH.benchmark(null);
                        // check source
                        if (prefs == null)
                            prefs = PreferenceManager.getDefaultSharedPreferences(context);

                        final Bundle bundle = intent.getExtras();
                        final String action = intent.getAction();

                        if (debug && bundle != null) {
                            JoH.dumpBundle(bundle, TAG);
                        }

                        if (action == null) {
                            UserError.Log.w(TAG, "abort: intent action is null");
                            return;
                        }

                        switch (action) {
                            case Intents.DashX:

                                // in future this could have its own data source perhaps instead of follower
                                final boolean follower = Home.get_follower();
                                final DexCollectionType coll = DexCollectionType.getDexCollectionType();
                                final boolean blukon = Pref.getBooleanDefaultFalse("external_blukon_algorithm");
                                UserError.Log.d(TAG, "DashX action: follower=" + follower + " dex_collection_type=" + coll + " external_blukon_algorithm=" + blukon);

                                if (!follower && coll != DexCollectionType.DashxAppReceiver && !blukon) {
                                    UserError.Log.e(TAG, "Received DashX data but we are not a follower or DashX receiver");
                                    return;
                                }

                                if (!Home.get_follower()) {
                                    if (!Sensor.isActive()) {
                                        // warn about problems running without a sensor record
                                        UserError.Log.d(TAG, "sensor inactive: showing start-sensor toast");
                                        Home.toaststaticnext("Please use: Start Sensor from the menu for best results!");
                                    }
                                }

                                if (bundle == null) {
                                    UserError.Log.w(TAG, "abort: DashX intent has no extras bundle");
                                    break;
                                }

                                UserError.Log.d(TAG, "parsing DashX payload");

                                final String collection = bundle.getString("collection");
                                if (collection == null) {
                                    UserError.Log.w(TAG, "abort: missing string extra \"collection\"");
                                    return;
                                }
                                UserError.Log.d(TAG, "collection=\"" + collection + "\"");

                                switch (collection) {

                                    case "entries":
                                        final String data = bundle.getString("data");

                                        if (data == null || data.length() == 0) {
                                            UserError.Log.w(TAG, "entries: missing or empty \"data\" string");
                                            break;
                                        }
                                        try {
                                            final JSONArray json_array = new JSONArray(data);
                                            UserError.Log.d(TAG, "entries JSON array length=" + json_array.length());
                                            if (json_array.length() == 0) {
                                                UserError.Log.w(TAG, "entries: empty JSON array");
                                                break;
                                            }
                                            if (json_array.length() > 1) {
                                                UserError.Log.d(TAG, "entries: multiple array elements; using index 0 only (count=" + json_array.length() + ")");
                                            }
                                            final JSONObject json_object = json_array.getJSONObject(0);
                                            UserError.Log.d(TAG, "entries[0] keys: " + jsonObjectKeyList(json_object));
                                            final String type = json_object.getString("type");
                                            UserError.Log.d(TAG, "entries[0] type=\"" + type + "\"");
                                            switch (type) {
                                                case "sgv":
                                                    double slope = 0;
                                                    String direction = null;
                                                    try {
                                                        direction = json_object.getString("direction");
                                                        slope = BgReading.slopefromName(direction);
                                                    } catch (JSONException e) {
                                                        UserError.Log.d(TAG, "sgv: no direction or parse failed: " + e.getMessage());
                                                    }
                                                    final long dateMs = json_object.getLong("date");
                                                    final double sgv = json_object.getDouble("sgv");
                                                    UserError.Log.d(TAG, "sgv: date=" + dateMs + " (" + JoH.dateTimeText(dateMs) + ") sgv=" + sgv + " direction=" + direction + " slope=" + slope);
                                                    bgReadingInsertFromData(context, dateMs, sgv, slope, true);

                                                    break;
                                                default:
                                                    UserError.Log.e(TAG, "Unknown entries type: " + type);
                                            }
                                        } catch (JSONException e) {
                                            UserError.Log.e(TAG, "Got JSON exception: " + e);
                                        }

                                        break;

                                    default:
                                        UserError.Log.d(TAG, "Unprocessed collection: " + collection);

                                }

                                break;

                            default:
                                UserError.Log.e(TAG, "Unknown action! " + action);
                                break;
                        }

                    } catch (Exception e) {
                        UserError.Log.e(TAG, "Caught Exception handling intent", e );
                    }finally {
                        JoH.benchmark("DashX process");
                        JoH.releaseWakeLock(wl);
                        UserError.Log.d(TAG, "onReceive finished");
                    }
                } // lock
            }
        }.start();
    }

    public static BgReading bgReadingInsertFromData(Context context, long timestamp, double sgv, double slope, boolean do_notification) {
        UserError.Log.d(TAG, "bgReadingInsertFromData timestamp=" + timestamp + " sgv=" + sgv + " slope=" + slope + " notify=" + do_notification + " time=" + JoH.dateTimeText(timestamp));
        Sensor.createDefaultIfMissing();

        double value = GlucoseSmoother.getSmoothedValueForInterApp(context, GlucoseSmoother.SOURCE_DASHX, timestamp, sgv);
        return BgReading.bgReadingInsertLibre2(value, timestamp, sgv);
    }
}
