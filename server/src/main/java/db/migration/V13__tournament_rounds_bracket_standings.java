package db.migration;
import org.flywaydb.core.api.migration.*;
import java.util.*;
/** Keep historical migrations intact; generated constraint names differ between PostgreSQL and H2. */
public class V13__tournament_rounds_bracket_standings extends BaseJavaMigration {
    @Override public Integer getChecksum(){return 130001;}
    @Override public void migrate(Context context) throws Exception {
        var connection=context.getConnection();var constraints=new ArrayList<String>();
        String query="SELECT tc.constraint_name,cc.check_clause FROM information_schema.table_constraints tc JOIN information_schema.check_constraints cc ON tc.constraint_schema=cc.constraint_schema AND tc.constraint_name=cc.constraint_name WHERE lower(tc.table_schema)=lower(?) AND lower(tc.table_name)='tournaments' AND tc.constraint_type='CHECK'";
        try(var sql=connection.prepareStatement(query)) {
            sql.setString(1,connection.getSchema());try(var rows=sql.executeQuery()) {
                while(rows.next()) {String clause=rows.getString(2).toLowerCase(Locale.ROOT);
                    if((clause.contains("format")&&clause.contains("'round_robin'")) || (clause.contains("max_participants")&&!clause.contains("is not null")))constraints.add(rows.getString(1));
                }
            }
        }
        if(constraints.size()!=2)throw new IllegalStateException("Expected tournament format and capacity checks");
        try(var sql=connection.createStatement()) {
            for(String constraint:constraints)sql.execute("ALTER TABLE tournaments DROP CONSTRAINT \""+constraint.replace("\"","\"\"")+"\"");
            sql.execute("ALTER TABLE tournaments ADD CONSTRAINT ck_tournaments_format CHECK (format IN ('ROUND_ROBIN','SINGLE_ELIMINATION'))");
            sql.execute("ALTER TABLE tournaments ADD CONSTRAINT ck_tournaments_capacity CHECK (max_participants BETWEEN 2 AND 32)");
            sql.execute("ALTER TABLE tournaments ADD COLUMN structure_json TEXT");
        }
    }
}
