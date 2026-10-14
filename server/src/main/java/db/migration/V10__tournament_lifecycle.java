package db.migration;

import org.flywaydb.core.api.migration.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.zip.CRC32;

/** V4's unnamed check has different generated names in PostgreSQL and H2. Never rewrite V4. */
public class V10__tournament_lifecycle extends BaseJavaMigration {
    private static final String CHECKS="SELECT tc.constraint_name, cc.check_clause FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc ON tc.constraint_schema=cc.constraint_schema AND tc.constraint_name=cc.constraint_name WHERE lower(tc.table_schema)=lower(?) AND lower(tc.table_name)='tournaments' AND tc.constraint_type='CHECK'";
    private static final String STATUS="ALTER TABLE tournaments ADD CONSTRAINT ck_tournaments_lifecycle CHECK (status IN ('DRAFT','REGISTRATION','CHECK_IN','READY','RUNNING','PAUSED','FINISHED','CANCELLED','RECOVERY_REQUIRED'))";
    private static final List<String> TIME=List.of(
        "ALTER TABLE tournaments ADD COLUMN status_changed_at TIMESTAMP WITH TIME ZONE",
        "UPDATE tournaments SET status_changed_at=COALESCE(finished_at,started_at,created_at)",
        "ALTER TABLE tournaments ALTER COLUMN status_changed_at SET NOT NULL",
        "ALTER TABLE tournaments ALTER COLUMN status_changed_at SET DEFAULT CURRENT_TIMESTAMP");
    @Override public Integer getChecksum() {
        var checksum=new CRC32();checksum.update((CHECKS+STATUS+String.join(";",TIME)+"drop-status-enum-check-only-v2").getBytes(StandardCharsets.UTF_8));return (int)checksum.getValue();
    }
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();var constraints=new ArrayList<String>();
        try(var query=connection.prepareStatement(CHECKS)) {
            query.setString(1,connection.getSchema());
            try(var rows=query.executeQuery()) {
                while(rows.next()) {
                    String clause=rows.getString(2);
                    // PostgreSQL also reports synthetic NOT NULL checks, which cannot be dropped by name.
                    if(java.util.regex.Pattern.compile("(?i)\\bstatus\\b").matcher(clause).find() && clause.contains("'DRAFT'") && clause.contains("'RUNNING'"))constraints.add(rows.getString(1));
                }
            }
        }
        if(constraints.isEmpty())throw new IllegalStateException("Expected existing tournament status constraint");
        try(var sql=connection.createStatement()) {
            for(var constraint:constraints)sql.execute("ALTER TABLE tournaments DROP CONSTRAINT \""+constraint.replace("\"","\"\"")+"\"");
            sql.execute(STATUS);for(var statement:TIME)sql.execute(statement);
        }
    }
}
