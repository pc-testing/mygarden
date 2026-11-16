# My Garden

Android app that identifies a plant from a photo and keeps a record of your plants and how to care for them.

- Take or pick a photo: the app names the plant and shows sunlight and watering needs.
- "More info" shows fertiliser, blooming season and propagation.
- The app asks "Add this plant to your garden?" before saving anything.
- If the plant can't be identified, you can fill in the details by hand.

Identification uses the Anthropic API (`claude-haiku-5-5` at low effort). Enter your API key under Settings in the app; it is stored encrypted on the phone and is not part of this repo or the APK.

## Getting the APK

Every push to `main` builds the app with GitHub Actions and publishes it under Releases as **Latest build** (`mygarden.apk`).

`debug.keystore` is a throwaway debug signing key, kept in the repo so each build installs over the previous one.
