package us.ajg0702.parkour.utils;

import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.Main;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.stream.Stream;

/**
 * Keeps players' inventories while they are on the parkour.
 * Backups are loaded into memory on enable; gameplay never touches the disk.
 */
public final class InventoryStore {

	private final Main plugin;
	private final Path dir;
	private final Map<UUID, byte[]> backups = new ConcurrentHashMap<>();

	public InventoryStore(Main plugin) {
		this.plugin = plugin;
		this.dir = plugin.getDataFolder().toPath().resolve("inventories");
	}

	public void preload() {
		if(!Files.isDirectory(dir)) return;
		try(Stream<Path> files = Files.list(dir)) {
			files.filter(p -> p.getFileName().toString().endsWith(".bin")).forEach(p -> {
				String raw = p.getFileName().toString();
				try {
					UUID id = UUID.fromString(raw.substring(0, raw.length() - 4));
					backups.put(id, Files.readAllBytes(p));
				} catch(Exception e) {
					plugin.getLogger().log(Level.WARNING, "Skipping inventory backup " + raw, e);
				}
			});
		} catch(IOException e) {
			plugin.getLogger().log(Level.WARNING, "Unable to preload inventory backups", e);
		}
		if(!backups.isEmpty()) {
			plugin.getLogger().info("Loaded " + backups.size() + " inventory backup(s) from disk.");
		}
	}

	private Path file(UUID id) {
		return dir.resolve(id + ".bin");
	}

	/**
	 * Snapshots the player's inventory. Does not clear it.
	 * @return the saved contents, or null if the inventory could not be saved
	 */
	public ItemStack @Nullable [] save(Player p) {
		ItemStack[] contents = p.getInventory().getContents();
		for(int i = 0; i < contents.length; i++) {
			if(contents[i] != null) contents[i] = contents[i].clone();
		}
		byte[] data;
		try {
			data = ItemStack.serializeItemsAsBytes(contents);
		} catch(Exception e) {
			plugin.getLogger().log(Level.SEVERE, "An error occurred while trying to save " + p.getName() + "'s inventory:", e);
			return null;
		}
		backups.put(p.getUniqueId(), data);
		Path target = file(p.getUniqueId());
		plugin.io().execute(() -> {
			try {
				Files.createDirectories(dir);
				Path tmp = target.resolveSibling(target.getFileName() + ".tmp");
				Files.write(tmp, data);
				Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch(IOException e) {
				plugin.getLogger().log(Level.SEVERE, "Unable to write inventory backup for " + p.getName(), e);
			}
		});
		return contents;
	}

	/** Restores a snapshot and removes the backup. Must be called on the player's thread. */
	public void restore(Player p, ItemStack[] contents) {
		p.getInventory().setContents(contents);
		drop(p.getUniqueId());
	}

	private void drop(UUID id) {
		backups.remove(id);
		Path target = file(id);
		plugin.io().execute(() -> {
			try {
				Files.deleteIfExists(target);
			} catch(IOException e) {
				plugin.getLogger().log(Level.WARNING, "Unable to delete inventory backup for " + id, e);
			}
		});
	}

	/**
	 * If the server stopped while this player was on the parkour, gives them their inventory back.
	 */
	public void recover(Player p) {
		byte[] data = backups.get(p.getUniqueId());
		if(data == null) return;
		Tasks.entity(p, () -> {
			if(plugin.man.inParkour(p)) return;
			try {
				restore(p, ItemStack.deserializeItemsFromBytes(data));
				plugin.getLogger().info("Restored " + p.getName() + "'s inventory from an unfinished parkour game.");
			} catch(Exception e) {
				plugin.getLogger().log(Level.SEVERE, "Unable to restore inventory backup for " + p.getName(), e);
			}
		});
	}
}
