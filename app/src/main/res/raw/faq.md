## How does Public Compute Services work?

Public Compute Services runs a small gRPC server on device, and redirects calls from Private Compute 
Services to download "manifests" (lists of available AI models) to this server. When requested, the 
server looks up the mirrored manifests in the provided repository, and returns them to the app which
requested them. From there, the models download as they would do on a non-rooted device.

This replaces the earlier method being used on rooted devices, which was to take full backups of 
app data with the models downloaded. The major advantage of this new method is direct downloading 
from the same source as an unrooted device, including incremental changes, without the need to
download and restore full backups for every update.

## What is a Manifest Repository?

Please check the GitHub readme for more information on this.

## Why is root required?

Root is required in addition to Xposed as the version of the manifests is normally delivered to the
device via Device Config (phenotypes / flags). In order to keep the right versions in sync, Public
Compute Services applies overrides to these configuration values, which requires root.

## Which AI features work with Public Compute Services?

With a correctly configured repository, almost all features are functional, except Voice Translate. 
The state of features and the devices they have been tested on are listed below:

| Feature              | Device          | Working |
|----------------------|-----------------|---------|
| Gemini Nano          | Pixel 10 Pro XL | ✅       |
| Magic Cue            | Pixel 10 Pro XL | ✅       |
| Pixel Screenshots    | Pixel 10 Pro XL | ✅       |
| Pixel Journal        | Pixel 10 Pro XL | ✅       |
| Pixel Weather        | Pixel 8 Pro     | ✅       |
| Take a Message       | Pixel 10 Pro XL | ✅       |
| Hold for Me          | Pixel 10 Pro XL | ✅       |
| Direct My Call       | Pixel 10 Pro XL | ✅       |
| Call Notes           | Pixel 10 Pro XL | ✅       |
| Call Screen          | Pixel 10 Pro XL | ✅       |
| Scam Detection       | Pixel 10 Pro XL | ✅       |
| Gboard Writing Tools | Pixel 10 Pro XL | ✅       |
| Voice Translate      | Pixel 10 Pro XL | ❌       |

> Voice Translate is unsupported due to a different method of fetching its AI models. It may be
> supported in the future. Please note that the devices listed above are just those which the 
> features were tested on, they will also work on other supported devices.

## Which devices are supported by Public Compute Services?

In theory, all devices with AIcore support should work. It's difficult to get a full and up-to-date
list of these, but the currently known supported devices and their device configuration are listed 
below:

| Device            | Variant      | Device Tier |
|-------------------|--------------|-------------|
| Galaxy S24 Ultra  | Samsung QC   | Mid         |
| Galaxy Z Fold 6   | Samsung QC   | Mid         |
| Galaxy S24 FE     | Samsung SLSI | Mid         |
| Galaxy Z Fold 7   | Variant 7    | High        |
| Pixel 8, Pixel 8a | Variant 8    | Mid         |
| Pixel 9, Pixel 9a | Variant 9    | Mid         |
| Galaxy S25 Ultra  | Variant 12   | Mid         |
| Pixel 10          | Variant 14   | Mid         |
| Galaxy Z Flip 7   | Variant 15   | High        |

> If you have a different device and it's working, please reply to the XDA thread with the 
> model and the **default** device configuration for it, so it can be documented. 

## Can I use Public Compute Services to get an AI feature from another device?

Probably not. Most of the AI models are created for specific devices, and do not work when installed
on another. For example, installing Pixel Screenshots models on a Pixel 8 Pro does not work. You are
welcome to try different device configurations to see if any work for you, but it's unlikely to
enable features successfully.

## How can I see a model download's progress?

Google made the progress of model downloads deliberately opaque, but if you want to see the exact 
progress of a download you may be able to enable the "Debug Logging" option (make sure the app 
you're using to download models is also hooked), and monitor the logs - the log tag is the last 
part of the app's package name, in all caps. For example, to follow the progress of Magic Cue's 
models, enable debug logging, make sure *Device Intelligence* is hooked, and monitor the log with 
the tag "PSI".

## Models aren't downloading!

There's a few possible causes for this:
- Check you are on unmetered Wi-Fi - models won't download over mobile data.
- Make sure there are no manifest updates available in Public Compute Services, and that it's able
to fetch the manifests from your specified repository (ie. no error is shown).
- Make sure Public Compute Services, Private Compute Services, AIcore and the app you're downloading 
models for are all up to date.
- Make sure all the required hooks are enabled for Public Compute Services in LSPosed.
- Try rebooting your device.

## Can I host my own repository?

You can clone an existing repository and host it yourself, but it will be hosting the same mirrored 
manifests. If you're concerned about calls from your device to an existing repository, or your 
device is unable to access a repository (eg. due to a firewall), you may wish to consider this.
