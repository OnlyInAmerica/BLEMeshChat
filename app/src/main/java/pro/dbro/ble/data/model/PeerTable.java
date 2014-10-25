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
public interface PeerTable {

    /** SQL type        Modifiers                   Reference Name            SQL Column Name */
    @DataType(INTEGER)  @PrimaryKey @AutoIncrement  String id               = "_id";
    @DataType(TEXT)     @NotNull                    String alias            = "alias";
    @DataType(TEXT)     @NotNull                    String lastSeenDate     = "last_seen";
    @DataType(BLOB)     @NotNull                    String pubKey           = "pk";
    @DataType(BLOB)                                 String secKey           = "sk";
    @DataType(BLOB)                                 String rawPkt           = "pkt";

}
