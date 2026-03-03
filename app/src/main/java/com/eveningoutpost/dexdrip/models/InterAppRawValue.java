package com.eveningoutpost.dexdrip.models;

import android.provider.BaseColumns;

import com.activeandroid.annotation.Column;
import com.activeandroid.annotation.Table;
import com.activeandroid.query.Select;

import java.util.ArrayList;
import java.util.List;

/**
 * Stores raw glucose readings from inter-app data sources (DashX, Ottai, AnytimeCT3, etc.)
 * for use by the universal smoothing algorithm. Each source has its own stream of points.
 */
@Table(name = "InterAppRawValue", id = BaseColumns._ID)
public class InterAppRawValue extends PlusModel {

    static final String[] schema = {
            "CREATE TABLE IF NOT EXISTS InterAppRawValue (_id INTEGER PRIMARY KEY AUTOINCREMENT, source TEXT, ts INTEGER, glucose REAL);",
            "CREATE INDEX IF NOT EXISTS index_InterAppRawValue_source_ts on InterAppRawValue(source, ts);"
    };

    @Column(name = "source", index = true)
    public String source;

    @Column(name = "ts", index = true)
    public long timestamp;

    @Column(name = "glucose", index = false)
    public double glucose;

    /**
     * Ensures the table exists. Call from migrations or before first use.
     */
    public static void updateDB() {
        PlusModel.fixUpTable(schema, false);
    }

    /**
     * Saves a raw reading for the given source. Call this for each new reading before smoothing.
     */
    public static void add(String source, long timestamp, double glucose) {
        updateDB();
        InterAppRawValue row = new InterAppRawValue();
        row.source = source;
        row.timestamp = timestamp;
        row.glucose = glucose;
        row.save();
    }

    /**
     * Returns all raw readings for the given source with timestamp >= sinceTimestamp, ordered by time ascending.
     */
    public static List<InterAppRawValue> getRecentForSource(String source, long sinceTimestamp) {
        updateDB();
        return new Select()
                .from(InterAppRawValue.class)
                .where("source = ?", source)
                .where("ts >= ?", sinceTimestamp)
                .orderBy("ts asc")
                .execute();
    }
}
