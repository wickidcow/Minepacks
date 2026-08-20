/*
 *   Copyright (C) 2023 GeorgH93
 *
 *   This program is free software: you can redistribute it and/or modify
 *   it under the terms of the GNU General Public License as published by
 *   the Free Software Foundation, either version 3 of the License, or
 *   (at your option) any later version.
 *
 *   This program is distributed in the hope that it will be useful,
 *   but WITHOUT ANY WARRANTY; without even the implied warranty of
 *   MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 *   GNU General Public License for more details.
 *
 *   You should have received a copy of the GNU General Public License
 *   along with this program. If not, see <http://www.gnu.org/licenses/>.
 */

package at.pcgamingfreaks.Minepacks.Bukkit.Database;

import at.pcgamingfreaks.ConsoleColor;
import at.pcgamingfreaks.Database.ConnectionProvider.ConnectionProvider;
import at.pcgamingfreaks.Minepacks.Bukkit.API.Callback;
import at.pcgamingfreaks.Minepacks.Bukkit.Backpack;
import at.pcgamingfreaks.Minepacks.Bukkit.Database.UnCacheStrategies.OnDisconnect;
import at.pcgamingfreaks.Minepacks.Bukkit.Database.UnCacheStrategies.UnCacheStrategy;
import at.pcgamingfreaks.Minepacks.Bukkit.Minepacks;

import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.HandlerList;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

public abstract class Database implements Listener
{
	public static final String MESSAGE_UNKNOWN_DB_TYPE = ConsoleColor.RED + "Unknown database type \"%s\"!" + ConsoleColor.RESET;

	protected final Minepacks plugin;
	protected final InventorySerializer itsSerializer;
	protected final boolean onlineUUIDs, bungeeCordMode, forceSaveOnUnload;
	protected boolean useUUIDSeparators, asyncSave = true;
	protected long maxAge;
	private final Map<UUID, Backpack> backpacks = new ConcurrentHashMap<>();
	private final ConcurrentHashMap<UUID, CompletableFuture<Backpack>> loadingBackpacks = new ConcurrentHashMap<>();
	private final UnCacheStrategy unCacheStrategy;
	private final File backupFolder;

	public Database(Minepacks mp)
	{
		plugin = mp;
		itsSerializer = new InventorySerializer(plugin.getLogger());
		useUUIDSeparators = plugin.getConfiguration().getUseUUIDSeparators();
		onlineUUIDs = plugin.getConfiguration().useOnlineUUIDs();
		bungeeCordMode = plugin.getConfiguration().isBungeeCordModeEnabled();
		forceSaveOnUnload = plugin.getConfiguration().isForceSaveOnUnloadEnabled();
		maxAge = plugin.getConfiguration().getAutoCleanupMaxInactiveDays();
		unCacheStrategy = bungeeCordMode ? new OnDisconnect(this) : UnCacheStrategy.getUnCacheStrategy(this);
		backupFolder = new File(this.plugin.getDataFolder(), "backups");
		if(!backupFolder.exists() && !backupFolder.mkdirs()) mp.getLogger().info("Failed to create backups folder.");
	}

	public void init()
	{
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}

	public void close()
	{
		HandlerList.unregisterAll(this);
		asyncSave = false;
		// Prevent an in-flight load from repopulating this database instance after shutdown/reload.
		loadingBackpacks.values().forEach(future -> future.cancel(false));
		loadingBackpacks.clear();
		// A normal save marks the backpack clean as soon as an async write is queued. During
		// shutdown/reload that queued write may not have reached storage yet, so always force one
		// final synchronous snapshot of every cached backpack before clearing the cache.
		backpacks.forEach((key, value) -> {
			value.setChanged();
			value.closeAll();
		});
		backpacks.clear();
		unCacheStrategy.close();
	}

	public static @Nullable Database getDatabase(Minepacks plugin)
	{
		try
		{
			String dbType = plugin.getConfiguration().getDatabaseType();
			ConnectionProvider connectionProvider = null;
			if(dbType.equals("shared") || dbType.equals("external") || dbType.equals("global"))
			{
				/*if[STANDALONE]
				plugin.getLogger().warning(ConsoleColor.RED + "The shared database connection option is not available in standalone mode!" + ConsoleColor.RESET);
				return null;
				else[STANDALONE]*/
				at.pcgamingfreaks.PluginLib.Database.DatabaseConnectionPool pool = at.pcgamingfreaks.PluginLib.Bukkit.PluginLib.getInstance().getDatabaseConnectionPool();
				if(pool == null)
				{
					plugin.getLogger().warning(ConsoleColor.RED + "The shared connection pool is not initialized correctly!" + ConsoleColor.RESET);
					return null;
				}
				dbType = pool.getDatabaseType().toLowerCase(Locale.ROOT);
				connectionProvider = pool.getConnectionProvider();
				/*end[STANDALONE]*/
			}
			Database database;
			switch(dbType)
			{
				case "mysql": database = new MySQL(plugin, connectionProvider); break;
				case "sqlite": database = new SQLite(plugin, connectionProvider); break;
				case "flat":
				case "file":
				case "files":
					database = new Files(plugin); break;
				default: plugin.getLogger().warning(String.format(MESSAGE_UNKNOWN_DB_TYPE,  plugin.getConfiguration().getDatabaseType())); return null;
			}
			database.init();
			return database;
		}
		catch(IllegalStateException e)
		{
			plugin.getLogger().log(Level.SEVERE, "Minepacks refused to initialize backpack storage because a safe serializer or database state is unavailable.", e);
		}
		catch(Exception e)
		{
			plugin.getLogger().log(Level.SEVERE, "Failed to initialize database.", e);
		}
		return null;
	}

	public void backup(@NotNull Backpack backpack)
	{
		writeBackup(backpack.getOwner().getName(), getPlayerFormattedUUID(backpack.getOwnerId()), itsSerializer.getUsedSerializer(), itsSerializer.serialize(backpack.getInventory()));
	}

	protected void writeBackup(@Nullable String userName, @NotNull String userIdentifier, final int usedSerializer, final byte[] data)
	{
		if(userIdentifier.equalsIgnoreCase(userName)) userName = null;
		if(userName != null) userIdentifier = userName + "_" + userIdentifier;
		final File save = new File(backupFolder, userIdentifier + "_" + System.currentTimeMillis() + Files.EXT);
		try(FileOutputStream fos = new FileOutputStream(save))
		{
			fos.write(usedSerializer);
			fos.write(data);
			plugin.getLogger().info("Backup of the backpack has been created: " + save.getAbsolutePath());
		}
		catch(Exception e)
		{
			plugin.getLogger().warning(ConsoleColor.RED + "Failed to write backup! Error: " + e.getMessage() + ConsoleColor.RESET);
		}
	}

	public @Nullable ItemStack[] loadBackup(final String backupName)
	{
		File backup = new File(backupFolder, backupName + Files.EXT);
		return Files.readFile(itsSerializer, backup, plugin.getLogger());
	}

	public ArrayList<String> getBackups()
	{
		File[] files = backupFolder.listFiles((dir, name) -> name.endsWith(Files.EXT));
		if(files != null)
		{
			ArrayList<String> backups = new ArrayList<>(files.length);
			for(File file : files)
			{
				if(!file.isFile()) continue;
				backups.add(file.getName().replaceAll(Files.EXT_REGEX, ""));
			}
			return backups;
		}
		return new ArrayList<>();
	}

	protected String getPlayerFormattedUUID(OfflinePlayer player)
	{
		return getPlayerFormattedUUID(player.getUniqueId());
	}

	protected String getPlayerFormattedUUID(UUID uuid)
	{
		return (useUUIDSeparators) ? uuid.toString() : uuid.toString().replace("-", "");
	}

	public @NotNull Collection<Backpack> getLoadedBackpacks()
	{
		return backpacks.values();
	}

	/**
	 * Gets a backpack for a player. This only includes backpacks that are cached! Do not use it unless you are sure that you only want to use cached data!
	 *
	 * @param player The player whose backpack should be retrieved.
	 * @return The backpack for the player. null if the backpack is not in the cache.
	 */
	public @Nullable Backpack getBackpack(@Nullable OfflinePlayer player)
	{
		return (player == null) ? null : backpacks.get(player.getUniqueId());
	}

	private @NotNull CompletableFuture<Backpack> getOrStartBackpackLoad(final @NotNull OfflinePlayer player)
	{
		final UUID playerId = player.getUniqueId();
		final Backpack cached = backpacks.get(playerId);
		if(cached != null) return CompletableFuture.completedFuture(cached);

		final CompletableFuture<Backpack> newLoad = new CompletableFuture<>();
		final CompletableFuture<Backpack> existingLoad = loadingBackpacks.putIfAbsent(playerId, newLoad);
		if(existingLoad != null) return existingLoad;

		try
		{
			loadBackpack(player, new Callback<Backpack>()
			{
				@Override
				public void onResult(Backpack backpack)
				{
					if(loadingBackpacks.remove(playerId, newLoad))
					{
						Backpack alreadyCached = backpacks.putIfAbsent(playerId, backpack);
						newLoad.complete((alreadyCached == null) ? backpack : alreadyCached);
					}
				}

				@Override
				public void onFail()
				{
					if(loadingBackpacks.remove(playerId, newLoad)) newLoad.complete(null);
				}
			});
		}
		catch(Exception e)
		{
			loadingBackpacks.remove(playerId, newLoad);
			newLoad.completeExceptionally(e);
		}
		return newLoad;
	}

	private void dispatchBackpackCallback(@NotNull OfflinePlayer owner, @NotNull Runnable callback)
	{
		if(Minepacks.isFoliaServer())
		{
			Player onlineOwner = owner.getPlayer();
			if(onlineOwner != null)
			{
				Minepacks.getScheduler().runAtEntity(onlineOwner, task -> callback.run());
			}
			else
			{
				// Offline backpacks have no entity scheduler. Use Folia's global scheduler for
				// plugin/global work rather than inheriting an arbitrary async completion thread.
				Minepacks.getScheduler().runNextTick(task -> callback.run());
			}
			return;
		}
		callback.run();
	}

	public void getBackpack(final OfflinePlayer player, final Callback<at.pcgamingfreaks.Minepacks.Bukkit.API.Backpack> callback, final boolean createNewOnFail)
	{
		if(player == null || player.getClass().getName().contains("NPC"))
		{
			callback.onFail();
			return;
		}

		final UUID playerId = player.getUniqueId();
		getOrStartBackpackLoad(player).whenComplete((loadedBackpack, error) -> dispatchBackpackCallback(player, () -> {
			if(error != null)
			{
				if(!(error instanceof java.util.concurrent.CancellationException))
				{
					plugin.getLogger().log(Level.SEVERE, "Failed to load backpack for " + playerId + ".", error);
				}
				callback.onFail();
				return;
			}
			if(loadedBackpack != null)
			{
				callback.onResult(loadedBackpack);
				return;
			}
			if(!createNewOnFail)
			{
				callback.onFail();
				return;
			}

			try
			{
				// The callback is on the owner's entity scheduler on Folia when the owner is online.
				// computeIfAbsent guarantees simultaneous first-time requests still share one object.
				Backpack created = backpacks.computeIfAbsent(playerId, ignored -> new Backpack(player));
				callback.onResult(created);
			}
			catch(Exception e)
			{
				plugin.getLogger().log(Level.SEVERE, "Failed to create backpack for " + playerId + ".", e);
				callback.onFail();
			}
		}));
	}

	public void getBackpack(final OfflinePlayer player, final Callback<at.pcgamingfreaks.Minepacks.Bukkit.API.Backpack> callback)
	{
		getBackpack(player, callback, true);
	}

	public void unloadBackpack(Backpack backpack)
	{
		if (forceSaveOnUnload)
		{
			backpack.forceSave();
		}
		else
		{
			backpack.save();
		}
		backpacks.remove(backpack.getOwnerId());
	}

	public void asyncLoadBackpack(final OfflinePlayer player)
	{
		if(player == null) return;
		getBackpack(player, new Callback<at.pcgamingfreaks.Minepacks.Bukkit.API.Backpack>()
		{
			@Override
			public void onResult(at.pcgamingfreaks.Minepacks.Bukkit.API.Backpack backpack) { /* preload only */ }

			@Override
			public void onFail() { /* preload only */ }
		}, true);
	}

	@EventHandler
	public void onPlayerLoginEvent(PlayerJoinEvent event)
	{
		updatePlayerAndLoadBackpack(event.getPlayer());
	}

	// DB Functions
	public void updatePlayerAndLoadBackpack(Player player)
	{
		updatePlayer(player);
		if(!bungeeCordMode) asyncLoadBackpack(player);
	}

	public abstract void updatePlayer(Player player);

	public abstract void saveBackpack(Backpack backpack);

	public void syncCooldown(Player player, long time) {}

	public void getCooldown(final Player player, final Callback<Long> callback) {}

	protected abstract void loadBackpack(final OfflinePlayer player, final Callback<Backpack> callback);
}
