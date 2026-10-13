package com.eveningoutpost.dexdrip.utils.jobs;

import androidx.annotation.NonNull;

import com.eveningoutpost.dexdrip.models.JoH;
import com.eveningoutpost.dexdrip.models.UserError;
import com.eveningoutpost.dexdrip.utilitymodels.Constants;
import com.eveningoutpost.dexdrip.utilitymodels.UpdateActivity;
import com.eveningoutpost.dexdrip.xdrip;
import com.evernote.android.job.Job;
import com.evernote.android.job.JobRequest;

/**
 * Periodic job that checks GitHub for a new xDrip version twice a day.
 *
 * This only triggers the check; the actual network request is additionally throttled and
 * gated inside UpdateActivity.checkForAnUpdate() (12h frequency + the user's auto-update setting),
 * so this job and the daily/sync triggers share one throttle and cannot cause extra checks.
 */
public class UpdateCheckJob extends Job {

    public static final String TAG = "xDrip-UpdateCheck";

    @Override
    @NonNull
    protected Result onRunJob(@NonNull final Job.Params params) {
        try {
            UpdateActivity.checkForAnUpdate(xdrip.getAppContext());
        } catch (Exception e) {
            UserError.Log.e(TAG, "Exception during update check: " + e);
        }
        return Result.SUCCESS;
    }

    public static void schedule() {
        if (JoH.pratelimit("update-check-job-schedule", 60000)) {
            UserError.Log.uel(TAG, JoH.dateTimeText(JoH.tsl()) + " Update check job scheduled");
            new JobRequest.Builder(TAG)
                    .setPeriodic(Constants.HOUR_IN_MS * 12, Constants.HOUR_IN_MS) // every 12h, 1h flex -> ~twice a day
                    .setRequiredNetworkType(JobRequest.NetworkType.CONNECTED)
                    .setRequiresBatteryNotLow(true)
                    .setUpdateCurrent(true)
                    .build()
                    .schedule();
        }
    }
}
