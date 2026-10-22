# Player final validation UI

Implemented 2026-10-22 for `PROMPT-REDESIGN-PLAYER-FINAL-VALIDATION-UI.md`.

## Presentation

- `final-validation-view.js`: explicit presentation allowlist, friendly stage labels, `✓ KIỂM THỬ HOÀN TẤT` only for server PASSED + applied, completed-step count, compact checkmarks, distinct running/failure/infrastructure rows.
- `player-view-state.js`: QUEUED/RUNNING/unapplied PASS → WAIT_VALIDATION; applied PASS → WAIT_CHECK_IN; FAILED → failure explanation; INFRA_ERROR → WAIT_INFRA_RETRY. A stale check-in hint in VALIDATING cannot grant the action.
- `player-shell.js`: defer the validation next-action card until after the result; display published check-in date and server-based countdown, no button while waiting. Check-in still requires server capability. Countdown expiry never grants permission.
- `code-workspace.js`, `tournament-code.html`: hide obsolete coding timer/alert during validation, retain dirty-draft/error information, collapse reference documents by default.
- `frozen-submission.js`, `source-view.js`: no displayed source/artifact hashes. Source link is `XEM SOURCE ĐÃ KHÓA` and requires workspace-read capability plus a version. Existing source page fetches an authorized immutable version and never initializes editor mutation handlers.
- `tournament.css`: wrapping rows, two columns on desktop / one on narrow screens, separated cards and collapsed references.
- `tournament-routes.js`: allow the failure explanation anchor.
- `validation-management-labels.js`, dashboard import: keep existing management wording independent of player labels; no Organizer redesign.

Existing player header approved-person count/loading fallback and VALIDATING stepper were already correct and remain covered by regression tests.

## API / technical data

No API, server DTO, database, GameEngine or final validation execution logic changed. The player presentation model excludes sourceHash, artifactHash, job/worker/execution/lease IDs and validation history. These are not rendered (including expandable details); backend audit/integrity/management data remains available. The immutable version identifier remains only in the authorized source navigation URL, not displayed as content.

## Verification

- Full client suite: **391 PASS, 0 FAIL, 0 SKIPPED**.
- Added `player-validation-screen.test.js`: applied PASS, 8/8, order/count of cards, technical-field exclusion, source permission, countdown expiry, QUEUED/RUNNING/unapplied PASS, FAILED, INFRA_ERROR, check-in capability, collapsed documentation.
- Existing actual player-controller refresh test still passes (frozen → queued → running at unchanged revision), plus header, stepper, source and final-flow regressions.
- `:bootJar`: PASS. Backend tests not rerun because this change modifies client presentation only.
- Browser smoke and desktop/mobile screenshots: **SKIPPED**. Browser tooling previously rejected URL authority for the local app. No alternative browser/CDP workaround was used. DOM tests are not visual layout verification; responsive styling remains unverified visually.

Logs are local ignored files: `server/build/local-run/player-validation-client.txt` and `player-validation-build.txt`.
