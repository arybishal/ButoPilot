# Project Guidelines & Release Automation Rules

## ⚠️ Mandatory Workflow Rule: Auto-Bump, Push, and Release

Whenever any changes are made to the codebase (features, bug fixes, UI updates, configurations, etc.):

1. **Version Bump**:
   - Increment `versionCode` by 1 in [app/build.gradle](file:///D:/Webapp/BAutoPilot/app/build.gradle).
   - Increment `versionName` semantically (e.g., `1.1.0` -> `1.1.1` for patch/fixes, `1.1.0` -> `1.2.0` for features).
2. **Build Production & Debug Artifacts**:
   - Run `.\gradlew.bat assembleRelease assembleDebug`.
   - Prepare named release binaries:
     - `ButoPilot-vX.Y.Z-Release.apk`
     - `ButoPilot-vX.Y.Z-Debug.apk`
3. **Generate Release Notes**:
   - Maintain `release_notes_vX.Y.Z.md` with features, fixes, and asset tables.
4. **Git Commit & Push**:
   - Commit all changes with a clear release or feat message.
   - Push to GitHub remote: `git push origin master`.
5. **Publish GitHub Release**:
   - Create a GitHub Release using `gh release create vX.Y.Z --title "ButoPilot vX.Y.Z — ..." --notes-file release_notes_vX.Y.Z.md <assets>`.
