# ButoPilot v1.6.1 Release Notes

## Highlights
- **120Hz Refresh Rate Support**: Added automatic 120Hz display mode selection in `MainActivity` (`preferredDisplayModeId` and `preferredRefreshRate`) for supported high-refresh-rate screens at native resolution.
- **Immersive Display Cutout & Dock Clearance**: Added top `displayCutout` safe-area padding across all screens so headers and `TopAppBar` titles sit cleanly below the camera cutout while keeping system bars hidden, and increased bottom clearance above `FloatingNavigationDock` from `74.dp` to `86.dp` to prevent bottom card clipping.
- **Home & Monitor UI Polish**:
  - Removed unfinished greeting suffix and emoji from `HomeScreen`.
  - Fixed multi-line title wrapping in `AutomationMonitorScreen` `TopAppBar` and aligned both live metric rows (`Elapsed | Actions | Memory Free` and `Scrolled | Clicked | Tasks Done`) into even 3-column grids.
  - Added bottom list clearance in `ProxyListScreen` so the floating action button no longer overlaps the bottom proxy card.

## Release Assets
| Artifact | Description |
| --- | --- |
| `ButoPilot-v1.6.1-Release.apk` | Production signed APK (`versionCode 27`) |
| `ButoPilot-v1.6.1-Debug.apk` | Debug APK (`versionCode 27`) |
