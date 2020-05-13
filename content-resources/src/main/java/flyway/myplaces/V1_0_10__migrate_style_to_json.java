package flyway.myplaces;

import fi.nls.oskari.log.LogFactory;
import fi.nls.oskari.log.Logger;
import fi.nls.oskari.util.UserDataStyleMigrator;
import org.flywaydb.core.api.migration.jdbc.JdbcMigration;

import java.sql.Connection;

public class V1_0_10__migrate_style_to_json implements JdbcMigration {
    private static final Logger LOG = LogFactory.getLogger(V1_0_10__migrate_style_to_json.class);
    private static final String TABLE = "categories";

    public void migrate(Connection connection) throws Exception {
        int count = UserDataStyleMigrator.migrateStyles(connection, TABLE, TABLE);
        LOG.info("Migrated", count, "styles to json in table:", TABLE);
    }
}
