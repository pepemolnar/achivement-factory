# Release Build & Install

Achievement Factory ships as a single signed APK, sideloaded directly onto the owner's own
phone. There is no Play Store step in this project — no `.aab`, no Play App Signing, no
release-track configuration of any kind. See `STACK.md`'s "What NOT to Use" table for why.

## One-time: generate the release keystore

Do this once, on the machine you build releases from. The keystore and its passwords must
never be committed — `.gitignore` already excludes `*.jks`, `*.keystore`, and
`keystore.properties`.

```bash
keytool -genkeypair \
  -v \
  -keystore /path/outside/the/repo/release.jks \
  -alias achievement-factory-release \
  -keyalg RSA \
  -keysize 2048 \
  -validity 10000 \
  -dname "CN=Achievement Factory, OU=Personal, O=Achievement Factory, L=Local, ST=Local, C=US"
```

`keytool`'s default keystore format is PKCS12, which does **not** support a store password and
a key password that differ — `keytool` will warn and silently reuse the store password for the
key. Use the same generated value for both in `keystore.properties` below; do not try to force
them apart.

Store the keystore file itself somewhere outside the repository (or, if it must live inside the
repo, only in a location `.gitignore` already excludes). Generate a long random password with
your OS's own secure random source, not a memorable phrase — you are not meant to type it
yourself, only the `keystore.properties` file below reads it.

## `keystore.properties`

Create this file at the project root (gitignored — confirm with
`git check-ignore keystore.properties`, which must exit 0):

```properties
storeFile=C:/path/to/release.jks
storePassword=<the generated password>
keyAlias=achievement-factory-release
keyPassword=<the same generated password — see the PKCS12 note above>
```

`app/build.gradle.kts` reads this file with a plain `java.util.Properties` load guarded by a
file-existence check. If `keystore.properties` is absent — e.g. a fresh clone on a machine that
hasn't generated a keystore yet — the `release` build type simply configures with no signing
config rather than failing the build at configuration time; `assembleRelease` still succeeds
and produces an **unsigned** APK. Signing only activates once `keystore.properties` exists.

## Build

```bash
./gradlew :app:assembleRelease
```

Produces `app/build/outputs/apk/release/app-release.apk`. `isMinifyEnabled = false` for the
release build type — there is no APK size budget on a single-device install, and disabling R8
removes the whole class of "R8 stripped a Room-generated class" failures for zero cost. Do not
hand-write ProGuard rules that fight Room's own consumer rules; none exist in this project.

## Verify the signature

Run `apksigner verify` from `build-tools;36.0.0` against the built APK:

```bash
"$ANDROID_HOME/build-tools/36.0.0/apksigner" verify --verbose app/build/outputs/apk/release/app-release.apk
```

Expect `Verifies` and at least one signature scheme (`v2` or later) reporting `true`.

## Install on the owner's phone

With the phone connected over USB and Developer Options → USB debugging enabled:

```bash
adb install -r app/build/outputs/apk/release/app-release.apk
```

`-r` reinstalls over an existing debug or prior release build without wiping app data (the
signing key must match for `-r` to succeed against an existing install — see the migration
warning below for what changes once real data exists).

## The migration rule, from the first install onward

**From the moment this APK is installed and used, the phone holds real, unrecoverable streak
history.** `android:allowBackup="false"` is deliberate (the product boundary is "everything
lives on one device"), which means there is no cloud restore path if the local database is
ever wiped or corrupted by a bad schema change.

This makes the following a standing rule, not a Phase 1 detail:

- `fallbackToDestructiveMigration()` (and its downgrade counterpart) must never appear anywhere
  in this codebase. Every schema version bump ships a real `Migration(from, to)`, adds it to
  the database builder, commits the newly exported schema JSON under `app/schemas/`, and
  extends `MigrationTest` (`app/src/androidTest/java/com/achievementfactory/data/`) to migrate
  a *populated* database and assert no row was lost — never a fresh empty one.
- `SchemaExportTest` (a plain JVM test) fails automatically if `APP_DATABASE_VERSION` is bumped
  without a matching exported schema file, so this discipline is enforced by a red test rather
  than remembered by hand.
- Every release that changes the schema must run `./gradlew :app:connectedDebugAndroidTest` on
  a real device before installing, exercising the migration test against real data.

Reinstalling with `adb install -r` after a schema change with no migration path will crash on
open or (worse) silently corrupt existing rows — treat any pending schema change as unsafe to
ship without its migration and test in place first.
