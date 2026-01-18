[![Public Compute Services Banner](https://i.imgur.com/drmF4WCl.png)](https://i.imgur.com/drmF4WC.png)

Public Compute Services is an Xposed module which allows the use of alternative repositories to 
download AICore models. This allows local AI features to work on rooted devices.

## Download & Installation

Installing Public Compute Services is simple:

- Download and install the [latest APK](https://github.com/KieronQuinn/PublicComputeServices/releases)
- Enable the Xposed module, and enable the required scopes
- Reboot
- After rebooting, open Public Compute Services and configure the options as required

## Manifest Repositories

A Manifest Repository is an alternative location for downloading manifests from. No default 
repository is included in Public Compute Services. You can find a list of currently available 
repositories on the [XDA thread](https://kieronquinn.co.uk/redirect/pcs/xda).

## Features

- Supports enabling almost all AI features with a correctly set up repository, for a list of 
features, please see the [FAQs](https://github.com/KieronQuinn/PublicComputeServices/blob/main/app/src/main/res/raw/faq.md)
- Set or change the Device Configuration, selecting which AI models to download for your device
- Automatically keep manifests in sync with your selected repository by checking for updates once a 
day and applying them.
- Enable AI features in Google Phone that are normally not available in your region
- Enable debug logging for hooked Google apps, useful for observing features such as Magic Cue

## Frequently Asked Questions
FAQs can be found [here](https://github.com/KieronQuinn/PublicComputeServices/blob/main/app/src/main/res/raw/faq.md). 
They are also available in the app, from the uTag settings.

## Screenshots

[![Main Settings](https://i.imgur.com/VyLnHY1l.png)](https://i.imgur.com/VyLnHY1.png)
[![Device Configuration](https://i.imgur.com/at3kKIOl.png)](https://i.imgur.com/at3kKIO.png)

## Building

> **Note**: Building Public Compute Services requires extracting the decryption key for the 
> manifests. **No help will be provided for doing this**. If you're happy using the already existing 
> native libraries for Sekret containing the key, you can remove the Gradle dependency on the Sekret
> local library and copy in the `.so` files yourself.

1. Clone the repository as normal
2. Run the gradle task `generateSekret`
3. Open app/sekret/build.gradle and add the following block to the bottom:
```kotlin
android {
  namespace = "com.kieronquinn.app.pcs.sekret"
    compileSdk {
        version = release(36)
    }
}
```
4. Open the auto-generated `local.properties` file and set it up as follows:
```properties
storeFile=<path to keystore>
storePassword=<keystore password>
keyAlias=<key alias>
keyPassword=<key password>
keyHash=<hash of your keystore>
```
5. Open app/sekret.properties and set it up as follows:
```properties
MANIFEST_KEY=<extracted encryption key>
```
6. Run the Gradle task `createAndCopySekretNativeBinary`
7. Compile the app as normal