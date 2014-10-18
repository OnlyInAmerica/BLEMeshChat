package pro.dbro.ble.model;

import net.simonvt.schematic.annotation.Database;
import net.simonvt.schematic.annotation.Table;

/**
 * SQL Database definition.
 *
 * Created by davidbrodsky on 7/28/14.
 */
@Database(version = ChatDatabase.DATABASE_VERSION)
public class ChatDatabase {

    public static final int DATABASE_VERSION = 1;

    /** Table Definition        Reference Name                        SQL Tablename */
    @Table(PeerTable.class)     public static final String  PEERS      = "peers";
    @Table(MessageTable.class)  public static final String  MESSAGES   = "msgs";
}