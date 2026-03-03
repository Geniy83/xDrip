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

public class DashxAppReceiver extends BroadcastReceiver {

    private static final String TAG = "Dashx";
    private static final boolean debug = false;
    private static final boolean d = false;
    private static SharedPreferences prefs;
    private static final Object lock = new Object();

    @Override
    public void onReceive(final Context context, final Intent intent) {
        new Thread() {
            @Override
            public void run() {
                PowerManager.WakeLock wl = JoH.getWakeLock("dashx-receiver", 60000);
                synchronized (lock) {
                    try {

                        UserError.Log.d(TAG, "dashx onReceiver: " + intent.getAction());
                        JoH.benchmark(null);
                        // check source
                        if (prefs == null)
                            prefs = PreferenceManager.getDefaultSharedPreferences(context);

                        final Bundle bundle = intent.getExtras();
                        //  BundleScrubber.scrub(bundle);
                        final String action = intent.getAction();


                        if ((bundle != null) && (debug)) {
                            UserError.Log.d(TAG, "Action: " + action);
                            JoH.dumpBundle(bundle, TAG);
                        }

                        if (action == null) return;

                        switch (action) {
                            case Intents.DashX:

                                // in future this could have its own data source perhaps instead of follower
                                if (!Home.get_follower() && DexCollectionType.getDexCollectionType() != DexCollectionType.DashxAppReceiver &&
                                        !Pref.getBooleanDefaultFalse("external_blukon_algorithm")) {
                                    UserError.Log.e(TAG, "Received DashX data but we are not a follower or DashX receiver");
                                    return;
                                }

                                if (!Home.get_follower()) {
                                    if (!Sensor.isActive()) {
                                        // warn about problems running without a sensor record
                                        Home.toaststaticnext("Please use: Start Sensor from the menu for best results!");
                                    }
                                }

                                if (bundle == null) break;

                                UserError.Log.d(TAG, "Receiving DashX broadcast");

                                final String collection = bundle.getString("collection");
                                if (collection == null) return;

                                switch (collection) {

                                    case "entries":
                                        final String data = bundle.getString("data");

                                        if ((data != null) && (data.length() > 0)) {
                                            try {
                                                final JSONArray json_array = new JSONArray(data);
                                                final JSONObject json_object = json_array.getJSONObject(0);
                                                final String type = json_object.getString("type");
                                                switch (type) {
                                                    case "sgv":
                                                        double slope = 0;
                                                        try {
                                                            slope = BgReading.slopefromName(json_object.getString("direction"));
                                                        } catch (JSONException e) {
                                                            //
                                                        }
                                                        bgReadingInsertFromData(context, json_object.getLong("date"),
                                                                json_object.getDouble("sgv"), slope, true);

                                                        break;
                                                    default:
                                                        UserError.Log.e(TAG, "Unknown entries type: " + type);
                                                }
                                            } catch (JSONException e) {
                                                UserError.Log.e(TAG, "Got JSON exception: " + e);
                                            }

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
                    }
                } // lock
            }
        }.start();
    }

    public static BgReading bgReadingInsertFromData(Context context, long timestamp, double sgv, double slope, boolean do_notification) {
        UserError.Log.d(TAG, "DasX bgReadingInsertFromData called timestamp = " + timestamp + " bg = " + sgv + " time =" + JoH.dateTimeText(timestamp));
        Sensor.createDefaultIfMissing();

        double value = GlucoseSmoother.getSmoothedValueForInterApp(context, GlucoseSmoother.SOURCE_DASHX, timestamp, sgv);
        return BgReading.bgReadingInsertLibre2(value, timestamp, sgv);
    }
}
