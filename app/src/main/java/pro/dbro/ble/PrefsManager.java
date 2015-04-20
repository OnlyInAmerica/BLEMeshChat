package pro.dbro.ble;

import android.content.Context;


/**
 * Created by davidbrodsky on 9/21/14.
 */
public class PrefsManager {

    /** SharedPreferences store names */
    private static final String APP_PREFS = "prefs";

    /** SharedPreferences keys */
    private static final String APP_STATUS = "status";

    public static int getStatus(Context context) {
        return context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE)
                      .getInt(APP_STATUS, 0);
    }

    public static void setStatus(Context context, int status) {
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit()
               .putInt(APP_STATUS, status)
               .commit();
    }

    public static void clearState(Context context) {
        context.getSharedPreferences(APP_PREFS, Context.MODE_PRIVATE).edit().clear().apply();
    }
}
