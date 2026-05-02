# Pingra
brand new open source Android text messaging app

Pingra is a very small Kotlin + Jetpack Compose SMS app. The first version is
intentionally basic: it can request the Android default SMS role, request SMS
permissions, list SMS conversations, open a thread, and send plain text SMS.

## Build

This project expects JDK 17 and an Android SDK with API 36 installed:

```sh
./gradlew assembleDebug
```

SMS features must be tested on a physical Android phone or an emulator image
with telephony/SMS support. Android requires Pingra to be selected as the
default SMS app before it can manage SMS provider data.
