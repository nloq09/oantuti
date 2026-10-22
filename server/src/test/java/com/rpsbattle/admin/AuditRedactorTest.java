package com.rpsbattle.admin;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AuditRedactorTest {
    final AuditRedactor redactor=new AuditRedactor(new ObjectMapper());
    @Test void nestedJsonSecretsAreRemovedButUsefulChangesRemain(){
        String raw="""
            {"status":"APPROVED","nested":[{"password":"private-pass","worker_credential":"private-worker","Authorization":"Bearer private-header","registrationToken":"private-token","sessionId":"private-session","dbSecret":"private-db","api_key":"private-api"}],"embedded":"{\"password\":\"private-inner\"}"}
            """;
        // Build valid nested JSON without relying on Java text-block escaping.
        try {var json=new ObjectMapper();raw=json.writeValueAsString(java.util.Map.of("status","APPROVED","nested",java.util.Map.of("password","private-pass","registration_token","private-token","workerCredential","private-worker","sessionId","private-session","authorization","Bearer private-header","dbSecret","private-db","api_key","private-api"),"embedded",json.writeValueAsString(java.util.Map.of("password","private-inner"))));}catch(Exception e){throw new AssertionError(e);}
        assertThat(redactor.redact(raw).toString()).contains("APPROVED","REDACTED").doesNotContain("private-");
        assertThat(raw).contains("private-pass");
    }
    @Test void legacyRecordAndReasonStringsRedactAssignmentsBearerAndDatabaseCredentials(){
        assertThat(redactor.redact("status=READY,password=private-pass; token=private-token authorization=Bearer private-header jdbc=postgresql://user:private-db@host/db").toString()).contains("status=READY").doesNotContain("private-");
        assertThat(redactor.redact(null)).isNull();assertThat(redactor.redact("PENDING")).isEqualTo("PENDING");
        assertThat(redactor.redact("{'password':'private-invalid',\"token\":\"private-invalid-token\", broken").toString()).doesNotContain("private-");
        assertThat(redactor.redact("copied wrt_1234567890abcdefghijklmnop").toString()).doesNotContain("wrt_");
    }
}
