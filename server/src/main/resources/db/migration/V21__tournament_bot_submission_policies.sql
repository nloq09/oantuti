CREATE TABLE tournament_bot_policies (
    tournament_id UUID PRIMARY KEY REFERENCES tournaments(id),
    submission_mode VARCHAR(32) NOT NULL CHECK (submission_mode IN ('PRE_UPLOADED_ONLY','CODE_IN_PLATFORM_ONLY','BOTH')),
    allowed_languages VARCHAR(32) NOT NULL CHECK (allowed_languages IN ('CPP','JAVA','PYTHON','CPP,JAVA','CPP,PYTHON','JAVA,PYTHON','CPP,JAVA,PYTHON')),
    policy_json TEXT NOT NULL
);
INSERT INTO tournament_bot_policies(tournament_id,submission_mode,allowed_languages,policy_json)
SELECT id,'PRE_UPLOADED_ONLY','CPP,JAVA,PYTHON','{"submissionMode":"PRE_UPLOADED_ONLY","allowedLanguages":["PYTHON","CPP","JAVA"]}'
FROM tournaments;
