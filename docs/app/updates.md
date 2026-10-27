---
title: Updates
summary: How Numa updates itself, how to install an update, switch to canary builds, roll back, and repair an install.
group: Help
icon: refresh-cw
keywords:
  - update
  - updates
  - new version
  - restart to update
  - canary
  - release notes
  - what's new
  - changelog
  - beta
  - rollback
  - repair
  - reinstall
  - uninstall
platforms:
  - desktop
order: 145
---

# Updates

Numa downloads updates in the background and installs them the next time it starts. You never wait for an update when you open Numa.

After an update, Numa opens the **Release notes** window once to show what changed. Releases you have not opened yet are marked **New**.

To read the notes again, open **Settings** > **About** > **Release Notes** and select **View release notes**. You can also press Ctrl+K (or /) and choose **Release notes**. The window lists every release on the left, newest first. The one you run is marked **Installed**. Select a release to read its changes, or use **Newer** and **Older** to move between releases. Type in the search box to find releases by version, title, or wording, for example "lyrics". Select **Copy notes** to copy a release as Markdown.

The notes are included with Numa, so you can read them offline. Settings > About still shows the version you are running in the Release Notes row.

## Install a downloaded update

When an update is ready, **Settings** > **General** > **Updates** says "Update ready. Restart Numa to install." Select **Restart**. Numa closes, switches to the new version, and opens again.

If you do nothing, the update installs the next time you start Numa.

## Check for updates now

Open **Settings** > **General** > **Updates** and select **Check for updates**. The row shows the download size while it downloads. Select **Cancel** to stop; the next check resumes where it stopped.

The **Updates** section only appears when Numa was installed with the Numa installer. Builds you compiled yourself do not update.

## How big updates are

Numa downloads only the files that changed. A typical update is 5 to 40 MB. Shared parts such as the Qt runtime, FFmpeg and the AI models download again only when they change.

## Turn off automatic downloads

Turn off **Download updates automatically** in **Settings** > **General** > **Updates**. Numa then downloads only when you select **Check for updates**.

## Get canary builds

Turn on **Canary builds** in **Settings** > **General** > **Updates**. Numa downloads the latest canary build right away. Turn it off to go back to stable builds; Numa installs the current stable version even if it is older.

## Numa went back to an older version

If a new version crashes twice right after it starts, Numa returns to the previous version and skips the broken one. A later update replaces it.

To go back yourself, run the Numa launcher with `--rollback`, for example `Numa.exe --rollback` on Windows.

## Repair a broken install

If Numa does not start or reports missing files, run the Numa launcher with `--repair`. It checks every installed file and downloads only the damaged or missing ones. Numa also repairs itself automatically when the current version is incomplete.

## Uninstall Numa

On Windows, open **Settings** > **Apps** and uninstall **Numa**. On Linux, run `~/.local/share/numa/numa --uninstall`. Your library, settings and downloads are kept.
