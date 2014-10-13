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
package pro.dbro.ble.ble;

import android.bluetooth.BluetoothGattCharacteristic;

import java.util.UUID;

/** MeshChat UUIDs */
public class GATT {

    /** Service */
    public static final UUID SERVICE_UUID = UUID.fromString("96F22BCA-F08C-43F9-BF7D-EEBC579C94D2");

    /** Characteristic */
    public static final UUID IDENTITY_UUID = UUID.fromString("21C7DE8E-B0D0-4A41-9B22-78221277E2AA");
    public static final UUID MESSAGES_UUID = UUID.fromString("A109B433-96A0-463A-A070-542C5A15E177");

    public static final BluetoothGattCharacteristic IDENTITY_CHARACTERISTIC =
            new BluetoothGattCharacteristic(IDENTITY_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);

    public static final BluetoothGattCharacteristic MESSAGES_CHARACTERISTIC =
            new BluetoothGattCharacteristic(IDENTITY_UUID, BluetoothGattCharacteristic.PROPERTY_READ, BluetoothGattCharacteristic.PERMISSION_READ);
}
