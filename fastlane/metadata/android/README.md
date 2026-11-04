# Google Play listing

Store texts and graphics for `fastlane supply`, in `de-DE` and `en-US`:

- `title.txt` (30), `short_description.txt` (80), `full_description.txt` (4000)
- `images/icon.png` (512x512) and `images/featureGraphic.png` (1024x500), drawn by
  `scripts/play-art.py` from the launcher mark
- `images/phoneScreenshots/`: emulator screenshots (1080x2400, demo status bar), padded to
  1200x2400 with the app background because Play allows at most 2:1

Uploading needs a Play Console service account (JSON key) and the app created in the Console
first; see issue #9.
