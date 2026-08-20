/*
 *   Copyright (C) 2024 GeorgH93
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

package at.pcgamingfreaks.Minepacks.Bukkit;

import at.pcgamingfreaks.Bukkit.MCVersion;
import at.pcgamingfreaks.Bukkit.Message.Message;
import at.pcgamingfreaks.Bukkit.Util.InventoryUtils;
import at.pcgamingfreaks.Minepacks.Bukkit.Database.Helper.InventoryCompressor;
import at.pcgamingfreaks.Util.StringUtils;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;

public class Backpack implements at.pcgamingfreaks.Minepacks.Bukkit.API.Backpack
{
	@Setter(AccessLevel.PACKAGE) private static ShrinkApproach shrinkApproach = ShrinkApproach.COMPRESS;
	@Setter(AccessLevel.PACKAGE) private static Message messageBackpackShrunk = new Message("Backpack shrunk!");
	private static Object titleOwnGlobal;
	private static String titleFormat, titleOtherFormat;
	private static boolean useDynTitle;
	private final Object titleOwn;
	private final String titleOther;
	@Getter private final UUID ownerId;
	private final Map<Player, Boolean> opened = new ConcurrentHashMap<>(); //Thanks Minecraft 1.14
	private Inventory bp;
	@Getter private int size;
	@Getter @Setter private volatile int ownerDatabaseId;
	private final AtomicBoolean hasChanged = new AtomicBoolean(false);

	public static void setTitle(final @NotNull String title, final @NotNull String titleOther)
	{
		titleFormat = title;
		titleOtherFormat = titleOther;
		useDynTitle = !title.equals(titleOther);
		// Paper-family servers use the stable public inventory API instead of the NMS title path.
		titleOwnGlobal = Minepacks.isPaperFamily() || title.contains("%s") ? null : InventoryUtils.prepareTitleForOpenInventoryWithCustomTitle(title);
	}

	public Backpack(OfflinePlayer owner)
	{
		this(owner, 9);
	}
	
	public Backpack(OfflinePlayer owner, int size)
	{
		this(owner, size, -1);
	}

	public Backpack(OfflinePlayer owner, int size, int ID)
	{
		if(MCVersion.isNewerOrEqualThan(MCVersion.MC_1_14) && size > 54)
		{
			size = 54;
			Minepacks.getInstance().getLogger().warning("Backpacks with more than 6 rows are no longer supported on Minecraft 1.14 and up!");
		}
		this.ownerId = owner.getUniqueId();
		titleOther = StringUtils.limitLength(String.format(titleOtherFormat, owner.getName()), 32);
		bp = Bukkit.createInventory(this, size, titleOther);
		this.size = size;
		ownerDatabaseId = ID;

		if(Minepacks.isPaperFamily()) titleOwn = null;
		else if (titleOwnGlobal != null) titleOwn = titleOwnGlobal;
		else titleOwn = InventoryUtils.prepareTitleForOpenInventoryWithCustomTitle(String.format(titleFormat, owner.getName()));
	}
	
	public Backpack(final OfflinePlayer owner, ItemStack[] backpack, final int ID)
	{
		this(owner, backpack.length, ID);
		if(MCVersion.isNewerOrEqualThan(MCVersion.MC_1_14) && backpack.length > 54)
		{ // Try to optimize space usage to compress items into only 6 rows
			InventoryCompressor compressor = new InventoryCompressor(backpack, 54);
			final List<ItemStack> toMuch = compressor.compress();
			backpack = compressor.getTargetStacks();
			if(!toMuch.isEmpty())
			{
				Minepacks.getInstance().getLogger().warning(owner.getName() + "'s backpack has too many items.");
				Player player = owner.getPlayer();
				if(player != null)
				{
					Minepacks.getScheduler().runAtEntity(player, task -> {
						if(!player.isOnline()) return;
						Map<Integer, ItemStack> left = player.getInventory().addItem(toMuch.toArray(new ItemStack[0]));
						left.forEach((id, stack) -> player.getWorld().dropItemNaturally(player.getLocation(), stack));
						this.setChanged();
					});
				}
				else throw new RuntimeException("Backpack too big for MC 1.14 and up!");
			}
		}
		bp.setContents(backpack);
	}

	@Override
	@Deprecated
	public @NotNull OfflinePlayer getOwner()
	{
		return Bukkit.getServer().getOfflinePlayer(ownerId);
	}

	@Override
	public @Nullable Player getOwnerPlayer()
	{
		return Bukkit.getServer().getPlayer(ownerId);
	}

	public void checkResize()
	{
		Player owner = Bukkit.getServer().getPlayer(this.ownerId);
		if(owner == null) return;
		if(Minepacks.isFoliaServer())
		{
			Minepacks.getScheduler().runAtEntity(owner, task -> checkResizeForOwner(owner));
		}
		else
		{
			checkResizeForOwner(owner);
		}
	}

	private void checkResizeForOwner(@NotNull Player owner)
	{
		if(!ownerId.equals(owner.getUniqueId()) || !owner.hasPermission(Permissions.USE)) return;
		// Never resize a live Folia inventory. It may be owned by a different region thread.
		if(Minepacks.isFoliaServer() && !opened.isEmpty()) return;
		int newSize = Minepacks.getInstance().getBackpackPermSize(owner);
		if(newSize != bp.getSize())
		{
			boolean dropped = false;
			List<ItemStack> items = setSize(newSize);
			for(ItemStack item : items)
			{
				if(item != null)
				{
					owner.getWorld().dropItemNaturally(owner.getLocation(), item);
					dropped = true;
				}
			}
			if(dropped) messageBackpackShrunk.send(owner);
		}
	}

	@Override
	public void open(final @NotNull Player player, final boolean editable)
	{
		open(player, editable, null);
	}

	@Override
	public void open(final @NotNull Player player, final boolean editable, final @Nullable String title)
	{
		if(Minepacks.isFoliaServer())
		{
			// A Bukkit Inventory must not be shared live across independent Folia regions. Keep
			// the GUI owner-only; administrative data operations are handled without a shared view.
			if(!ownerId.equals(player.getUniqueId()))
			{
				Minepacks.getScheduler().runAtEntity(player, task -> player.sendMessage("Opening another player's backpack is disabled on Folia for data safety."));
				return;
			}
			Minepacks.getScheduler().runAtEntity(player, task -> {
				if(!player.isOnline()) return;
				checkResizeForOwner(player);
				openPrepared(player, editable, title);
			});
			return;
		}

		checkResize();
		openPrepared(player, editable, title);
	}

	private void openPrepared(@NotNull Player player, boolean editable, @Nullable String title)
	{
		if(Minepacks.isFoliaServer() && !opened.isEmpty() && !opened.containsKey(player))
		{
			player.sendMessage("This backpack is already open.");
			return;
		}

		if(Minepacks.isPaperFamily())
		{
			// Paper/Purpur/Folia use the public API; the previous custom-title path depended on NMS.
			player.openInventory(bp);
		}
		else if(title != null)
		{
			InventoryUtils.openInventoryWithCustomTitle(player, bp, title);
		}
		else if(useDynTitle && ownerId.equals(player.getUniqueId()))
		{
			InventoryUtils.openInventoryWithCustomTitlePrepared(player, bp, titleOwn);
		}
		else
		{
			player.openInventory(bp);
		}
		// Only track a viewer after the inventory open completed without throwing.
		opened.put(player, editable);
	}

	public void close(Player p)
	{
		opened.remove(p);
	}

	public void closeAll()
	{
		if(!Minepacks.isFoliaServer())
		{
			opened.forEach((key, value) -> key.closeInventory());
		}
		// During Folia shutdown the entity schedulers may already be unavailable. The server owns
		// GUI teardown; Minepacks only needs to drop viewer references and persist the snapshot.
		opened.clear();
		save();
	}

	@Override
	public boolean isOpen()
	{
		return !opened.isEmpty();
	}

	@Override
	public boolean canEdit(@NotNull Player player)
	{
		return opened.containsKey(player) && opened.get(player);
	}

	public @NotNull List<ItemStack> setSize(int newSize)
	{
		if(Minepacks.isFoliaServer() && !opened.isEmpty())
		{
			throw new IllegalStateException("Cannot resize an open backpack on Folia.");
		}
		if(!Minepacks.isFoliaServer()) opened.forEach((key, value) -> key.closeInventory()); // Close all open views of the inventory
		List<ItemStack> removedItems;
		ItemStack[] itemStackArray;
		if(bp.getSize() > newSize)
		{
			InventoryCompressor compressor = new InventoryCompressor(bp.getContents(), newSize);
			switch(shrinkApproach)
			{
				case FAST: compressor.fast(); break;
				case COMPRESS: compressor.compress(); break;
				case SORT: compressor.sort(); break;
			}
			itemStackArray = compressor.getTargetStacks();
			removedItems = compressor.getToMuch();
		}
		else
		{
			itemStackArray = bp.getContents();
			removedItems = new ArrayList<>(0);
		}
		bp = Bukkit.createInventory(this, newSize, titleOther);
		for(int i = 0; i < itemStackArray.length; i++)
		{
			bp.setItem(i, itemStackArray[i]);
		}
		setChanged();
		save(); // Make sure the new inventory is saved
		size = newSize;
		if(!Minepacks.isFoliaServer()) opened.forEach((key, value) -> key.openInventory(bp));
		return removedItems;
	}

	@Override
	public @NotNull Inventory getInventory()
	{
		return bp;
	}

	@Override
	public boolean hasChanged()
	{
		return hasChanged.get();
	}

	@Override
	public void setChanged()
	{
		hasChanged.set(true);
	}

	@Override
	public void save()
	{
		// Atomically claim the current dirty state. If another region marks the backpack dirty
		// after this CAS, that newer change remains true and will be persisted by the next save.
		if(hasChanged.compareAndSet(true, false))
		{
			Minepacks.getInstance().getDatabase().saveBackpack(this);
		}
	}

	public void forceSave()
	{
		hasChanged.set(true);
		save();
	}

	public void backup()
	{
		Minepacks.getInstance().getDatabase().backup(this);
	}

	@Override
	public void clear()
	{
		bp.clear();
		setChanged();
		save();
	}

	@Override
	public void drop(final @NotNull Location location)
	{
		InventoryUtils.dropInventory(bp, location);
		setChanged();
		save();
	}
}
