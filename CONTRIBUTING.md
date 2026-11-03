# Working on Mauve Fit

## One app, two platforms

The Android app (`app/`) and the iOS app (`ios/`) always have the **same features**. A feature is
done when it is in both. Only the platform services differ:

| | Android | iOS |
|---|---|---|
| Health data | Health Connect | Apple Health (HealthKit) |
| Watch | Wear OS app (`wear/`) | Apple Watch app (watchOS) |
| Widgets | Glance widget | WidgetKit |
| Backup | Google Drive | iCloud |

Shared data lives once in the repo and is read by both apps: `app/src/main/assets/exercise*.json`
and `ios/MauveFit/Resources/` (muscle map, Everkinetic drawings).

## Issues

Every piece of work has an issue. Labels: `android`, `ios`, `watch` for the platform, `parity` for
changes both apps need, `store` for releases and store listings, `design`, and `needs-user` when
Maurice has to decide or do something.

## Pull requests

Work happens on a branch and lands in `main` through a pull request that references its issue.
CI must be green: `Android` (unit tests, phone and watch build) and `iOS` (unit tests, UI
screenshots). A change on one platform names the issue for the other one if it is not in the same
PR. Merging to `main` builds a TestFlight upload; Android releases come from `scripts/release.sh`.

No emojis in the UI. Commits under Maurice's name only.
