# YB Second Wind — Yellowbrick v3 Android app

Android replacement for the discontinued **YB Messenger** app. Sends and
receives satellite messages through a Yellowbrick v3 / MkII tracker over
Bluetooth. Works on Android 12 and later, including Android 14/15 where the
vendor app no longer installs.

> Not affiliated with, endorsed by or supported by YB Tracking Ltd.
> "Yellowbrick" and "YB" are their marks, used here only to identify the
> device. Use of a third-party app with your airtime account is between you
> and your airtime provider.

## Why

- *"App not installed as app isn't compatible with your phone"* when
  installing YB Messenger 1.3. Android 14+ refuses apps built for
  Android 4.4 (targetSdk 20).
- YB Messenger installs but can't find or connect to the Yellowbrick.
  It never asks for the Bluetooth permissions Android 12 introduced.
- The Yellowbrick itself and the satellite service — those still work; only
  the phone app died.

## What it does

- Conversations per person, with each message's state shown. A person's phone
  number and e-mail can be merged into one conversation.
- Message info on long-press: addresses, channel, timings, part ids, credits.
- Device panel: connection, credits with their age, firmware version, configured
  tracking and inbox-check intervals, storage warning, last connection.
- Favourites and recent recipients, plus pick-from-phone-contacts.
- Device guide: condensed MkII reference for use alongside the app.
- Automatic recovery when another phone has paired with the Yellowbrick
  since you last connected.

## Requirements

- Yellowbrick v3 / MkII with firmware 02.07.xx and an active airtime account.
- Your 4-character keyword and password from YB Tracking.
- Android 12 or later with Bluetooth Classic.

## Install

Download the APK from the Releases page and sideload it. Upgrades must be
signed with the same key; the Releases page is the only source of signed
builds.

## Build

JDK 17, Android SDK 35, Gradle wrapper included.

```sh
./gradlew :app:assembleDebug
./gradlew :app:test
```

## Protocol

`docs/PROTOCOL.md` is the complete interoperability specification and the
only input to the protocol code. Nothing from the vendor's software is
included in this repository; see `NOTICE`.

## Licence

Apache-2.0. See `LICENSE` and `NOTICE`.
