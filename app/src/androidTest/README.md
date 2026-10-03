# Device acceptance tests

Phase 2 adds `Phase2DeviceAcceptanceTest` for the installed target API, FULL access/insets and the actual DataStore file path with recreation, LIMITED labelling with recreation, and DENIED scanner/count fail-closed behavior. Run its individual methods with grants preset **outside** instrumentation; revoking this package's permission during instrumentation can kill the test process. Record and restore the original grants. `pm grant READ_MEDIA_VISUAL_USER_SELECTED` covers the OS grant branch but does not prove a human picker selection or its exact accessible URI set. The API 26–36/OEM matrix, real picker reselection/revocation and cloud/D2D restore remain explicit acceptance items in `docs/PHASE2_PLATFORM_REPORT.md`. The three basic checks do not read or mutate photo bytes; production Dashboard count and permission ID refresh queries still run.

`Phase2BatchDeviceTest` creates 2,002 app-owned synthetic PNG rows, protects one keeper, approves the first 2,000 candidates via real system consent, and cancels the remaining one via a second real system consent. The actual repository and ResultsViewModel reconcile all URI states and protect stats. Its small Compose host launches ActivityResult consent without composing thousands of current result cards: this is not proof of large-gallery ResultsScreen performance. Cleanup checks each exact URI's unique display-name prefix and app owner before removal; it never cleans gallery-wide. Run only on a dedicated test emulator with FULL read access; the suite must print its synthetic cleanup count. Source generator only; generated media and APKs remain outside Git.

After the batch run, `Phase2DeviceAcceptanceTest#batchFixtureCleanupIncludesTrashedRows` performs a read-only app-owner/test-prefix audit with `QUERY_ARG_MATCH_TRASHED=MATCH_INCLUDE`. A normal empty query excludes trash and does not prove cleanup. This additional check creates/removes no media.

## Phase 1 safety regression suite

`safety/Phase1DeviceAcceptanceTest.kt` runs on API 30+. It uses ActivityScenario, real Compose screens, UiAutomation, ActivityResult, MediaStore, Android codecs and an isolated in-memory Room cache. Existing Espresso 3.6.1 uses a removed hidden input API on API 37, so these tests do not use Espresso or Compose test rules. Runtime dependencies and SDK versions are unchanged.

Use a disposable emulator or a backed-up test device with sufficient RAM. Do not use production photos. The test creates a unique `Pictures/PhotoClarityAI_Phase1_<UUID>/` directory for each case. Cleanup queries the specific inserted URI and validates its unique display-name prefix before deletion. No gallery-wide cleanup occurs. Statistics use an in-memory fake except the actual Hilt/navigation tests, which only trash files and do not add freed bytes. User scan preferences are not edited. ScanResultHolder is restored after each test.

`Phase1SyntheticMedia.kt` generates the CC0 baseline PNG bytes in memory and validates the same size/SHA-256 on every setup. No binary media is required in androidTest assets. The Phase 1 commit contains only the generator/test source and documentation, not media, screenshots or emulator artifacts. A shell-owned external fixture can be copied locally from the existing Phase 0 fixture described below; that copy must remain outside Git.

Build with the project's supported JDK and SDK: `gradlew.bat assembleDebug assembleDebugAndroidTest`. Install both debug APKs using `adb install -r`. Grant the debug app `android.permission.READ_MEDIA_IMAGES` on API 33+ (API 30–32 use READ_EXTERNAL_STORAGE). Record existing permission state and restore it afterward.

For non-owned-media coverage, push only the CC0 PNG to a newly created, uniquely named `Pictures/PhotoClarityAI_Phase1_External_<UUID>/` directory and request a scan of that exact file using ACTION_MEDIA_SCANNER_SCAN_FILE. Query its exact display name to obtain the MediaStore URI; verify owner_package_name differs from the app. Pass `-e externalUri <specific URI> -e externalFolder <unique directory name>` to instrumentation. Without these arguments the non-owned test is explicitly skipped. Restore the external fixture's IS_TRASHED flag as its shell owner before rerunning; afterward delete only that known URI/file/directory. Never substitute an existing user photo.

Run directly, for example (device selector and fixture values must be supplied):

```text
adb -s emulator-5554 shell am instrument -w -r -e class com.photoclarity.ai.safety.Phase1DeviceAcceptanceTest -e externalUri <URI> -e externalFolder <folder> com.photoclarity.ai.debug.test/androidx.test.runner.AndroidJUnitRunner
```

Read the JUnit result, failures and skipped codes; adb's process exit code or INSTRUMENTATION_CODE alone does not prove tests passed. `connectedDebugAndroidTest` uses UTP, which may uninstall target/test APKs at completion and remove private app state. Direct instrumentation was used for the final acceptance run. Do not run UTP against private app data you need to preserve.

The suite can write screenshots of controlled synthetic screens to the debug app's external files `phase1-evidence/`. Keep screenshots, device logs and generated APKs in ignored local evidence directories, not in Git.

Boundaries: seeded result screens are distinguished from actual analyzer/navigation tests. The latter analyze only the test list, then navigate through the production Dashboard/NavHost/Hilt Results flow; they do not start an unrestricted whole-gallery scan. One partial-result delivery test is injected after querying real trashed/unchanged provider rows. Separately, the disappearing-candidate test deletes only its own fixture while real consent is open and verifies the real provider/callback outcome; on API 37 this produced two trashed and one unverifiable candidate. It does not cover all OEM batch failure models. Restoration via the provider proves data exists; it does not test a gallery's restore interface. API 26–29 permanent deletion, API 30 specifically, OEM providers, formats beyond the PNG and process death need separate coverage. See `docs/PHASE1_SAFETY_REPORT.md` for acceptance status and evidence.
