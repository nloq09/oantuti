# Organizer final validation dashboard

Implemented 2026-10-22 for `PROMPT-REDESIGN-ORGANIZER-FINAL-VALIDATION-DASHBOARD.md`.

## Layout and behavior

- One content heading, `Kiểm thử chính thức`; tournament name stays in the shared header. Removed the redundant management-back link and coding-deadline card. The dedicated page suppresses the shell's duplicate metrics/attention blocks.
- **Tiến độ**: total, queued, running, completed. **Kết quả**: passed, failed, infrastructure error. Completed includes terminal infrastructure outcomes, which still require attention; the collapsed calculation help explains this distinction.
- Emergency count appears only when positive. Failure/infra filter actions appear only when the corresponding current count is positive. Zero issues use a small success line, not a warning card.
- Next action distinguishes pending/running, emergencies, unapplied results, completed results, issues, check-in and terminal phases. It shows the published check-in date without duplicating the header countdown. No automatic check-in/start mutation.
- Compact filters search username/display name on the server. The current DTO does not provide a bot name, so no invented bot-name search. Historical browsing is explicit and read-only in this dashboard.
- Cards prioritize player, language/version, overall result and completed-step count. Friendly checkmarked stages live under `Xem chi tiết`; failed rows are highlighted. Reason codes map to safe explanations, including unknown-code fallback; raw stack traces are not printed.
- Validation ID, source/artifact hash and timestamps are inside a second closed `Thông tin kỹ thuật` disclosure. No worker/execution/lease data is fabricated where the DTO lacks it.
- Retry appears only for INFRA_ERROR + explicit canRetry + open server window. Emergency grant remains a separate capability. Both require a reason and use the existing revision-checked, authorized mutation API. No bulk retry.
- Pagination uses server total and disappears for a single page. An empty page after filtering/count changes returns to the last valid page.
- Auto refresh every 3 seconds, age label, manual update and retry after read errors. Active text entry defers background repaint. Reason/deadline drafts and expanded details survive refresh. Mutations cannot double-submit.
- Summary groups, filters, cards and test rows wrap on small screens; technical hashes break within their container.

## Files

Changed:

- `client/js/tournament/final-validation-dashboard.js`: controller, filters/search/history, refresh, mutations, pagination.
- `client/js/tournament/organizer-shell.js`: hide duplicate shell metrics only on the dedicated validation dashboard.
- `client/css/tournament.css`: scoped dashboard layout and responsive rules.
- `server/src/main/java/com/rpsbattle/api/controller/FinalValidationController.java`: opt-in dashboard read mode.
- `server/src/main/java/com/rpsbattle/tournament/coding/FinalValidationService.java`: manager authorization and input validation for read mode.
- `server/src/main/java/com/rpsbattle/tournament/coding/FinalValidationStore.java`: current-cutoff aggregate, literal server search, count-before-page query, typed UUID projection and bounded page details under a repeatable-read transaction.
- `server/src/test/java/com/rpsbattle/tournament/OrganizerMetricsApiTest.java`: current/history/search/paging/scope/privacy/authorization regressions.

Added:

- `client/js/tournament/organizer-validation-view.js`: summary, next action, cards, details, action forms and pagination.
- `client/tests/organizer-validation-dashboard.test.js`: renderer/action/summary/detail/pagination cases.
- `client/tests/organizer-validation-controller.test.js`: real page-controller integration with mocked transports.
- `server/src/test/java/com/rpsbattle/tournament/OrganizerValidationPostgresTest.java`: production-database coverage of the new API contracts, reusing fixtures.
- This report.

## API compatibility

Existing GET `/api/tournaments/{id}/final-validations` keeps its legacy response/behavior unless `view=dashboard` is supplied.

Dashboard query: `view=dashboard&page=0&status=FAILED&search=alice&history=false`.

- Optional status: QUEUED, RUNNING, PASSED, FAILED, INFRA_ERROR, EMERGENCY_OPEN.
- Search: up to 100 characters, case-insensitive literal username/display-name match; bound parameters, not SQL concatenation of user input.
- Default current-only: latest freeze per person, excluding superseded timeline-extension validations. Explicit history includes older records in the list.
- Added response metadata: total, page, size (25), history, summary, serverNow. Existing revision/retryWindowOpen and item DTO retained.
- Summary remains current-only and unfiltered even while browsing history/search. It adds unapplied result count so the UI cannot claim operational completion before server application.
- Existing manager permission, no-store caching, source privacy, action reason/revision/deadline rules remain in force. No database migration, GameEngine change, Player UI change or final-validation execution change.

## Verification

- Client: **402 PASS, 0 FAIL, 0 SKIPPED**.
- Backend: **47 PASS, 0 FAIL, 0 SKIPPED**: FinalValidationApiTest (36), OrganizerMetricsApiTest (9), PostgreSQL dashboard contracts (2).
- PostgreSQL tests ran through Docker, including current/history counts, literal search, filtering before paging, scope and authorization. H2 and PostgreSQL use the same tested read implementation.
- `:bootJar`: PASS; `git diff --check`: PASS.
- Browser smoke and desktop/mobile screenshots: **SKIPPED**. The browser tool previously rejected URL authority for this local app. No alternative browser/CDP workaround was used. DOM tests do not verify actual visual layout; responsive CSS remains unverified visually.

Ignored local logs: `server/build/local-run/organizer-validation-client.txt`, `organizer-validation-final-backend.txt`, `organizer-validation-build.txt`.
