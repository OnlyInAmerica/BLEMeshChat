package pro.dbro.ble.protocol;

import android.support.annotation.NonNull;

/**
 * Created by davidbrodsky on 10/20/14.
 */
public interface Protocol {

    /** Outgoing
     *
     * Serialize Protocol Objects to raw transmission data
    **/
    public byte[] createIdentityResponse(@NonNull OwnedIdentity ownedIdentity);

    public byte[] createPublicMessageResponse(@NonNull OwnedIdentity ownedIdentity, String body);

    /** Incoming
     *
     * Deserialize raw transmission data into Protocol Objects
     */

    public Identity consumeIdentityResponse(@NonNull byte[] identity);

    public Message consumeMessageResponse(@NonNull byte[] message);
}
