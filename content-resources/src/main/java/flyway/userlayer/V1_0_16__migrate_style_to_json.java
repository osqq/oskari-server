package flyway.userlayer;

import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.util.UserDataStyleMigrator;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;
import java.sql.Connection;

/**
 * Migrate fields JSONObject to JSONArray from user_layer table
 */
public class V1_0_16__migrate_style_to_json implements JdbcMigration {
    private static final Logger LOG = LogFactory.getLogger(V1_0_16__migrate_style_to_json.class);
    private static final String STYLE_TABLE = "user_layer_style";
    private static final String LAYER_TABLE = "user_layer";

    public void migrate(Connection connection) throws Exception {
        int count = UserDataStyleMigrator.migrateStyles(connection, LAYER_TABLE, STYLE_TABLE);
        LOG.info("Migrated", count, "styles from table:", STYLE_TABLE, "to table:", LAYER_TABLE);
    }
}
