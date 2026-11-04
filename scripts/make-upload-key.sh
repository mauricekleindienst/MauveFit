#!/usr/bin/env bash
# Creates the upload key for Google Play, once. Run it yourself: it asks for the passwords, and
# neither the key nor the passwords go into the repository.
#
#   scripts/make-upload-key.sh [path]   default: ~/.mauvefit/upload.jks
#
# Afterwards, in GitHub → Settings → Secrets and variables → Actions, add:
#   MAUVE_UPLOAD_KEYSTORE_B64     the base64 the script prints at the end
#   MAUVE_UPLOAD_STORE_PASSWORD   the keystore password
#   MAUVE_UPLOAD_KEY_PASSWORD     the key password (the same, if you pressed Enter)
# Keep a copy of the .jks and the passwords somewhere safe: with Play App Signing, a lost upload
# key can be reset through Play support, but that takes days.
set -euo pipefail
out=${1:-"$HOME/.mauvefit/upload.jks"}
[ -e "$out" ] && { echo "$out exists already" >&2; exit 1; }
mkdir -p "$(dirname "$out")"
keytool -genkeypair -v -keystore "$out" -alias upload -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=Maurice Kleindienst, O=Mauve Fit"
echo
echo "Created $out. Value for MAUVE_UPLOAD_KEYSTORE_B64:"
base64 -w0 "$out" 2>/dev/null || base64 "$out" | tr -d '\n'
echo
