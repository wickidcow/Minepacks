<!-- Variables (this block will not be visible in the readme -->
[banner]: https://pcgamingfreaks.at/images/minepacks.png
[spigot]: https://www.spigotmc.org/resources/19286/
[spigotRatingImg]: https://img.shields.io/badge/dynamic/json.svg?color=brightgreen&label=rating&query=%24.rating.average&suffix=%20%2F%205&url=https%3A%2F%2Fapi.spiget.org%2Fv2%2Fresources%2F19286
[spigotDownloadsImg]: https://img.shields.io/badge/dynamic/json.svg?color=brightgreen&label=downloads%20%28spigotmc.org%29&query=%24.downloads&url=https%3A%2F%2Fapi.spiget.org%2Fv2%2Fresources%2F19286
[bukkit]: https://dev.bukkit.org/projects/minepacks
[bukkitDownloadsImg]: https://cf.way2muchnoise.eu/full_minepacks_downloads.svg
[versionsImg]: https://cf.way2muchnoise.eu/versions/minepacks.svg
[issues]: https://github.com/GeorgH93/Minepacks/issues
[wiki]: https://github.com/GeorgH93/Minepacks/wiki
[wikiFAQ]: https://github.com/GeorgH93/Minepacks/wiki/FAQ
[wikiPermissions]: https://github.com/GeorgH93/Minepacks/wiki/Permissions
[release]: https://github.com/GeorgH93/Minepacks/releases/latest
[releaseImg]: https://img.shields.io/github/release/GeorgH93/Minepacks.svg?label=github%20release
[license]: https://github.com/GeorgH93/Minepacks/blob/master/LICENSE
[licenseImg]: https://img.shields.io/github/license/GeorgH93/Minepacks.svg
[ci]: https://ci.pcgamingfreaks.at/job/Minepacks/
[ciImg]: https://ci.pcgamingfreaks.at/job/Minepacks/badge/icon
[ciDev]: https://ci.pcgamingfreaks.at/job/Minepacks%20Dev/
[ciDevImg]: https://ci.pcgamingfreaks.at/job/Minepacks%20Dev/badge/icon
[apiVersionImg]: https://img.shields.io/badge/dynamic/xml.svg?label=api-version&query=%2F%2Frelease[1]&url=https%3A%2F%2Frepo.pcgamingfreaks.at%2Frepository%2Fmaven-releases%2Fat%2Fpcgamingfreaks%2FMinepacks-API%2Fmaven-metadata.xml
[api]: https://github.com/GeorgH93/Minepacks/tree/master/Minepacks-API
[apiJavaDoc]: https://ci.pcgamingfreaks.at/job/Minepacks%20API/javadoc/
[apiBuilds]: https://ci.pcgamingfreaks.at/job/Minepacks%20API/
[bugReports]: https://github.com/GeorgH93/Minepacks/issues?q=is%3Aissue+is%3Aopen+label%3Abug
[bugReportsImg]: https://img.shields.io/github/issues/GeorgH93/Minepacks/bug.svg?label=bug%20reports
[reportBug]: https://github.com/GeorgH93/Minepacks/issues/new?labels=bug&template=bug.md
[featureRequests]: https://github.com/GeorgH93/Minepacks/issues?q=is%3Aissue+is%3Aopen+label%3Aenhancement
[featureRequestsImg]: https://img.shields.io/github/issues/GeorgH93/Minepacks/enhancement.svg?label=feature%20requests&color=informational
[requestFeature]: https://github.com/GeorgH93/Minepacks/issues/new?labels=enhancement&template=enhancement.md
[config]: https://github.com/GeorgH93/Minepacks/blob/master/Minepacks/resources/config.yml
[pcgfPluginLib]: https://github.com/GeorgH93/PCGF_PluginLib
[pcgfPluginLibAdvantages]: https://github.com/GeorgH93/Minepacks/wiki/Build-and-Mode-comparison#Advantages-of-using-the-PCGF-PluginLib
[languages]: https://github.com/GeorgH93/Minepacks/tree/master/Minepacks/resources/lang
<!-- End of variables block -->

[![Logo][banner]][spigot]

Minepacks is a free and reliable backpack plugin for Minecraft servers running Bukkit, Spigot, Paper, Purpur, or Folia.

[![ciImg]][ci] [![releaseImg]][release]
[![apiVersionImg]][api] [![licenseImg]][license] [![spigotRatingImg]][spigot]

[![featureRequestsImg]][featureRequests] [![bugReportsImg]][bugReports]
[![spigotDownloadsImg]][spigot] [![bukkitDownloadsImg]][bukkit]

## Paper, Purpur, and Folia compatibility in this fork

This fork preserves Minepacks' Bukkit/Spigot compatibility while using stable public APIs and scheduler ownership rules for modern Paper-family servers.

* Paper and Purpur use the standard `plugin.yml` loader; the experimental `paper-plugin.yml` / `PluginBootstrap` path has been removed.
* Paper, Purpur, and Folia use Bukkit's public inventory-opening API instead of version-specific menu/NMS title rewriting.
* The Minecraft upper-version safety gate remains in place so stored backpack data is not rewritten on an unvalidated future server version.
* Folia player work is routed through entity schedulers and location-owned world work through region schedulers.
* Folia intentionally disables live viewing/editing of another player's backpack because one live Bukkit `Inventory` must not be shared across independently ticking player regions.
* Folia intentionally disables Minepacks live reload and live database migration. Restart the server to reload Minepacks, and perform storage migration from a maintenance/non-Folia instance or offline workflow.
* Full-inventory auto-pickup pauses while the owner's backpack GUI is open on Folia to avoid concurrent mutation of the live inventory.
* Per-viewer custom backpack-title rewriting is not used on Paper-family servers; the normal backpack inventory title is used instead.
* Plugins using the Minepacks API on Folia must access a live backpack `Inventory` from the backpack owner's entity thread. The API does not hide unsafe cross-region access by blocking between regions.

## Production JAR in this fork

The published GitHub release asset is one self-contained `Minepacks-<version>.jar`. It includes the required PCGF PluginLib runtime pieces internally, so server owners do not need to install PCGF PluginLib separately.

The older BadRabbit runtime selector is not used by the production JAR. Direct self-contained startup is used instead because it is simpler, avoids constructing a second `JavaPlugin` implementation at runtime, and is the path validated by the Paper/Purpur/Folia server smoke workflow.

## Features:
* [Configuration][config]
* Backpack size controlled by [permissions][wikiPermissions]
* Auto item-collect on full inventory (can be enabled in the config)
* Multiple storage back-ends (Files, SQLite, MySQL)
* Multi language support ([multiple language file included][languages])
* Item filter (block items from being stored in the backpack)
* Preserves the NBT data of items (everything that can be stored in a chest can be stored in the backpack)
* Support for name changing / UUIDs
* Auto-updater
* [API][api] for developers

## Requirements:
### Runtime requirements:
* Use the Java version required by your Minecraft server. The project keeps Java 8 source compatibility for legacy Bukkit/Spigot while modern 26.2 production packaging is validated on Java 25.
* Bukkit or Spigot for legacy supported Minecraft versions, or Paper/Purpur/Folia through the explicitly validated modern version range ![versionsImg]
* The production JAR is self-contained. A separately installed PCGF PluginLib is not required.

### Build requirements:

* JDK for the target build/runtime
* Maven 3
* git

## Build from source:

### Normal/development version:
This build expects PCGF PluginLib to be installed on the server.
```
git clone https://github.com/GeorgH93/Minepacks.git
cd Minepacks
mvn package
```
The final file will be in the `Minepacks/target` folder, named `Minepacks-<CurrentVersion>.jar`.

### Self-contained production version:
This is the recommended build for Paper, Purpur, Folia, and normal standalone server use. It does not require a separate PCGF PluginLib installation.
```
git clone https://github.com/GeorgH93/Minepacks.git
cd Minepacks
mvn clean package -P Standalone
```
The self-contained file will be in the `Minepacks/target` folder, named `Minepacks-<CurrentVersion>-Standalone.jar`. GitHub releases from this fork publish that same validated build as `Minepacks-<CurrentVersion>.jar`.

### Legacy Release profile:
The upstream `Release` Maven profile is retained for source compatibility with the original project, but it uses the legacy BadRabbit runtime selector and is not the production artifact published by this fork.

## API:
Minepacks V2 comes with an API that allows you to interact with this plugin.
If you think there is something missing in the API feel free to open a [feature request][requestFeature].
Please do not access data of the plugin in any other way than through the provided API, the inner workings will change and I won't keep track of what you are using in your plugin.
For more details about the API please check the following links:

[Source Code & Details][api] ⚫ [JavaDoc][apiJavaDoc] ⚫ [Build Server][apiBuilds]

## Support:
* [Wiki][wiki]
* [Issue tracker][issues]
  * [new feature request][requestFeature]
  * [new bug report][reportBug]
* [Faq][wikiFAQ]

## Links:
* [Spigot][spigot] - [![spigotDownloadsImg]][spigot]
* [CurseForge][bukkit] - [![bukkitDownloadsImg]][bukkit]
* [Build Server - Release Builds ![ciImg]][ci]
* [Build Server - Dev Builds ![ciDevImg]][ciDev]
