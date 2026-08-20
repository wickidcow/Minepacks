<!-- Variables (this block will not be visible in the readme -->
[banner]: https://pcgamingfreaks.at/images/minepacks.png
[spigot]: https://www.spigotmc.org/resources/minepacks.19286/
[license]: https://github.com/wickidcow/Minepacks/blob/master/LICENSE
[licenseImg]: https://img.shields.io/github/license/wickidcow/Minepacks.svg
[ci]: https://github.com/wickidcow/Minepacks/actions/workflows/maven.yml
[ciImg]: https://github.com/wickidcow/Minepacks/actions/workflows/maven.yml/badge.svg
[apiVersionImg]: https://img.shields.io/badge/dynamic/xml.svg?label=api-version&query=%2F%2Frelease[1]&url=https%3A%2F%2Frepo.pcgamingfreaks.at%2Frepository%2Fmaven-releases%2Fat%2Fpcgamingfreaks%2FMinepacks-API%2Fmaven-metadata.xml
[apiJavaDoc]: https://ci.pcgamingfreaks.at/job/Minepacks%20API/javadoc/
[apiBuilds]: https://github.com/wickidcow/Minepacks/actions/workflows/maven.yml
<!-- End of variables block -->

[![Logo][banner]][spigot]

This branch holds the API for the Minepacks plugin.

[![ciImg]][ci] [![apiVersionImg]][apiJavaDoc] [![licenseImg]][license]

## Adding it to your plugin:
### Maven:
The API is available through maven.
#### Repository:
```
<repository>
	<id>pcgf-repo</id>
	<url>https://repo.pcgamingfreaks.at/repository/maven-everything</url>
</repository>
```
#### Dependency:
```
<!-- Minepacks API -->
<dependency>
    <groupId>at.pcgamingfreaks</groupId>
    <artifactId>Minepacks-API</artifactId>
    <version>2.2</version><!-- Check api-version shield for newest version -->
</dependency>
```

### Build from source:
```
git clone https://github.com/wickidcow/Minepacks.git
cd Minepacks
mvn -pl Minepacks-API
```

## Usage:
### Get access to the API:
```java
public static MinepacksPlugin getMinepacks() {
    Plugin bukkitPlugin = Bukkit.getPluginManager().getPlugin("Minepacks");
    if(!(bukkitPlugin instanceof MinepacksPlugin)) {
    	// Do something if Minepacks is not available
        return null;
    }
    return (MinepacksPlugin) bukkitPlugin;
}
```
You can now use the returned `MinepacksPlugin` object to interact with the Minepacks plugin.

### Access a players backpack inventory:
```java
public static Inventory getPlayerBackpackInventory(Player player) {
    Backpack bp = getMinepacks().getBackpackCachedOnly(player);
    if(bp == null) return null; //Backpack not loaded (retry later)
    return bp.getInventory();
}
```
This will return null if the backpack is not loaded or the inventory of the backpack if the backpack is already loaded.

### Folia thread ownership

Minepacks schedules its own player operations through Folia entity schedulers, but the API intentionally returns Bukkit objects directly rather than hiding them behind blocking cross-region calls.

When running on Folia:

* Access or mutate a live backpack `Inventory` only from the backpack owner's entity thread.
* Do not pass one live backpack inventory between players in different regions.
* Prefer Minepacks API methods such as `openBackpack(...)` for GUI opening so Minepacks can perform the appropriate entity-scheduler handoff.
* Treat callbacks involving an online backpack owner as owner-context work. If your plugin needs to continue on another player's region, explicitly hand off to that player's entity scheduler.
* Minepacks inventory-clear events run in the target player's owning region. If `getSender()` is a different online `Player`, do not dereference or mutate that sender from the event callback; schedule any sender work on that sender's entity scheduler first.
* Treat any Minepacks event exposing two different live players the same way: the callback's current region does not grant ownership of the other player's Bukkit state.

Paper, Purpur, Bukkit, and Spigot keep their normal API behavior; these restrictions are specifically about Folia's region ownership model.

## Links:
* [JavaDoc][apiJavaDoc]
* [API Build Workflow][apiBuilds]
