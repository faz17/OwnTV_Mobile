# faz17 OwnTV Mobile — releases and upstream integration

The customized Mobile client is maintained on
`feature/dispatcharr-source-switch`, separately from `ahXN00/OwnTV_Mobile`.
The fork's default `main` contains a daily upstream integration workflow.

## First signed Mobile release

`.github/workflows/dispatcharr-mobile-apk.yml` builds a signed debug APK
for QA and a separate, signed release APK for the first numbered version.

- Release: `v2.0.0`, asset `OwnTV-Mobile-v2.0.0.apk`.
- Application ID: `tv.own.owntv.mobile` (unchanged).
- Persistent signing uses the existing repository Actions secrets:
  `KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
- Release versionCode uses `300000 + GITHUB_RUN_NUMBER` for the first
  release so that it can install above the prior preview's default 99999.
- A release tag is not replaced by later preview builds.

**Never rotate the signing certificate** for the same application ID if
in-place updates must preserve application data. Do not put the keystore
in version control.

## Future Mobile updates

The app's in-app update checker targets **`faz17/OwnTV_Mobile`**.
Subsequent numbered releases, e.g. `v2.0.1`, must increment both
`VERSION_NAME` and `VERSION_CODE` and must use the same signing key.

The signed debug previews used `VERSION_NAME=99.99.99` by default.
They therefore will not detect `v2.0.0` as newer through a
string-only version comparison; users of those previews must install
this first stable release manually. Android installation can still
succeed because the signed release has a higher numerical versionCode.

The upstream sync workflow checks `ahXN00/OwnTV_Mobile` for changes,
attempts a non-forced merge, and runs tests before publishing. Conflicts
or failing builds leave the personalized branch unchanged.
