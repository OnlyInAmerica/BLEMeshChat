package pro.dbro.ble;

import android.app.Application;

import com.facebook.stetho.Stetho;

import timber.log.Timber;

/**
 * Created by davidbrodsky on 4/17/15.
 */
public class ChatApp extends Application {

    @Override public void onCreate() {
        super.onCreate();

        if (BuildConfig.DEBUG) {
            Timber.plant(new Timber.DebugTree());

            Stetho.initialize(
                    Stetho.newInitializerBuilder(this)
                            .enableDumpapp(
                                    Stetho.defaultDumperPluginsProvider(this))
                            .enableWebKitInspector(
                                    Stetho.defaultInspectorModulesProvider(this))
                            .build());
        }

        // If we abandon Timber logging in this app, enable below line
        // to enable Timber logging in sdk
        //Logging.forceLogging();
    }
}
