/*
 * Copyright (C) 2013 youten
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package pro.dbro.ble.transport.ble;

import android.bluetooth.BluetoothManager;
import android.content.Context;
import android.content.pm.PackageManager;

/**
 * Util for Bluetooth Low Energy
 */
public class BLEUtil {
    public static final String TAG = "BLEUtil";

    private BLEUtil() {
        // Util
    }

    /** check if BLE Supported device */
    public static boolean isBLESupported(Context context) {
        return context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE);
    }

    /** get BluetoothManager */
    public static BluetoothManager getManager(Context context) {
        BluetoothManager manager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        return manager;
    }

    /**
     * Return whether Bluetooth is currently enabled. If request is true, prompt
     * for permission to enable. If so host should be configured to evaluate the result
     * via a call to {@link android.app.Activity#onActivityResult(int, int, android.content.Intent)}
    */
    public static boolean isBluetoothEnabled(Context context) {
        boolean enabled = getManager(context).getAdapter().isEnabled();
        return enabled;
    }



}
