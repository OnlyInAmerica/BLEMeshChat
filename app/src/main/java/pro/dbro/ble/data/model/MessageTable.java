package pro.dbro.ble.data.model;

import net.simonvt.schematic.annotation.AutoIncrement;
import net.simonvt.schematic.annotation.DataType;
import net.simonvt.schematic.annotation.NotNull;
import net.simonvt.schematic.annotation.PrimaryKey;

import static net.simonvt.schematic.annotation.DataType.Type.BLOB;
import static net.simonvt.schematic.annotation.DataType.Type.INTEGER;
import static net.simonvt.schematic.annotation.DataType.Type.TEXT;

/**
 * Created by davidbrodsky on 7/28/14.
 */
public interface MessageTable {

    /** SQL type        Modifiers                   Reference Name            SQL Column Name */
    @DataType(INTEGER)  @PrimaryKey @AutoIncrement  String id               = "_id";
    @DataType(TEXT)     @NotNull                    String body             = "body";
    @DataType(INTEGER)                              String peerId           = "p_id";
    @DataType(TEXT)                                 String authoredDate     = "author_date";
    @DataType(TEXT)                                 String receivedDate     = "recv_date";
    @DataType(BLOB)                                 String signature        = "sig";
    @DataType(BLOB)                                 String replySig         = "r_sig";
    @DataType(BLOB)                                 String rawPacket        = "pkt";
}
