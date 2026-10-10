package us.ajg0702.parkour;

import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.game.PkArea;
import us.ajg0702.parkour.storage.PlayerData;
import us.ajg0702.parkour.utils.Tasks;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;

public class BlockSelector implements Listener {

	private static final int PER_PAGE = 45;

	/**
	 * A configured block. {@code raw} is the entry exactly as written in blocks.yml (stored as the player's choice).
	 * @param areas lowercase area names this block is limited to in random mode; empty means every area
	 */
	public record Choice(String raw, Material material, Set<String> areas) {}

	private static final Choice INVALID = new Choice("", Material.AIR, Set.of());

	private static final List<String> DEFAULT_BLOCKS = List.of(
			"BLUE_GLAZED_TERRACOTTA", "ORANGE_GLAZED_TERRACOTTA", "WHITE_GLAZED_TERRACOTTA",
			"LIGHT_BLUE_GLAZED_TERRACOTTA", "YELLOW_GLAZED_TERRACOTTA", "LIME_GLAZED_TERRACOTTA",
			"PINK_GLAZED_TERRACOTTA", "GRAY_GLAZED_TERRACOTTA", "CYAN_GLAZED_TERRACOTTA",
			"PURPLE_GLAZED_TERRACOTTA", "BROWN_GLAZED_TERRACOTTA", "GREEN_GLAZED_TERRACOTTA",
			"RED_GLAZED_TERRACOTTA", "BLACK_GLAZED_TERRACOTTA", "MOSSY_COBBLESTONE", "NETHER_BRICKS",
			"QUARTZ_BLOCK", "QUARTZ_PILLAR", "BLACK_WOOL", "BLUE_WOOL", "BROWN_WOOL", "CYAN_WOOL",
			"GRAY_WOOL", "GREEN_WOOL", "LIGHT_BLUE_WOOL", "LIGHT_GRAY_WOOL", "LIME_WOOL", "MAGENTA_WOOL",
			"ORANGE_WOOL", "PINK_WOOL", "PURPLE_WOOL", "RED_WOOL", "WHITE_WOOL", "YELLOW_WOOL");

	private final Main plugin;
	private final File blocksFile;

	private volatile List<Choice> choices = List.of();
	private volatile List<UUID> presents = List.of();
	private final Map<String, List<Material>> randomPerArea = new ConcurrentHashMap<>();
	private final Map<String, Choice> parsedChoices = new ConcurrentHashMap<>();
	private final Map<String, Boolean> warnedInvalid = new ConcurrentHashMap<>();

	public BlockSelector(Main pl) {
		plugin = pl;
		blocksFile = new File(plugin.getDataFolder(), "blocks.yml");
		reloadTypes();
	}

	public void reloadTypes() {
		YamlConfiguration blocks = YamlConfiguration.loadConfiguration(blocksFile);
		if(!blocks.isSet("blocks")) {
			YamlConfiguration oldconfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
			blocks.set("blocks", oldconfig.isSet("blocks") ? oldconfig.get("blocks") : DEFAULT_BLOCKS);
			blocks.options().setHeader(List.of(
					"This is where you can set what blocks to use.",
					"Here is a guide for the special things you can do in this file: https://wiki.ajg0702.us/ajparkour/configs/Blocks",
					"Make sure to only use blocks (not items) or it will break!"));
			try {
				blocks.save(blocksFile);
			} catch(IOException e) {
				plugin.getLogger().severe("Unable to save blocks file! " + e.getMessage());
			}
		}

		List<Choice> parsed = new ArrayList<>();
		for(String raw : blocks.getStringList("blocks")) {
			Choice c = parse(raw);
			if(c != null) {
				parsed.add(c);
			} else {
				plugin.getLogger().warning("Could not find block '" + raw + "'! Make sure it exists in the server version you are using!");
			}
		}
		if(parsed.isEmpty()) {
			parsed.add(new Choice("STONE", Material.STONE, Set.of()));
			plugin.getLogger().warning("None of the materials in blocks.yml are valid! Falling back to stone.");
		}

		List<UUID> heads = new ArrayList<>();
		for(String u : blocks.getStringList("present_heads")) {
			try {
				heads.add(UUID.fromString(u));
			} catch(IllegalArgumentException ignored) {}
		}

		choices = List.copyOf(parsed);
		presents = List.copyOf(heads);
		randomPerArea.clear();
		parsedChoices.clear();
		warnedInvalid.clear();
		for(Choice c : this.choices) parsedChoices.put(c.raw(), c);
	}

	private static @Nullable Choice parse(@Nullable String raw) {
		if(raw == null || raw.isBlank()) return null;
		String[] parts = raw.split(";", 2);
		Material m = Material.matchMaterial(parts[0].split(":")[0]);
		if(m == null || !m.isBlock()) return null;
		Set<String> areas = parts.length > 1
				? Arrays.stream(parts[1].split(",")).map(s -> s.trim().toLowerCase(Locale.ROOT)).filter(s -> !s.isEmpty()).collect(Collectors.toUnmodifiableSet())
				: Set.of();
		return new Choice(raw, m, areas);
	}

	public List<Choice> getChoices() {
		return choices;
	}

	public List<UUID> getPresents() {
		return presents;
	}

	// ------------------------------------------------------------------ block picking

	/**
	 * Gets the block a player should use. Thread-safe and never touches the database.
	 */
	public Material getBlockMaterial(Player p, PkArea area) {
		PlayerData data = plugin.scores.getCached(p.getUniqueId());
		String raw = data == null ? null : data.material();
		if(raw != null && !isRandom(raw)) {
			Choice c = parsedChoices.computeIfAbsent(raw, k -> {
				Choice parsed = parse(k);
				return parsed == null ? INVALID : parsed;
			});
			if(c != INVALID) return c.material();
			if(warnedInvalid.putIfAbsent(raw, true) == null) {
				plugin.getLogger().warning("Could not find block '" + raw + "'! Using random blocks instead.");
			}
		}
		List<Material> pool = randomPerArea.computeIfAbsent(area.getName().toLowerCase(Locale.ROOT), this::buildPool);
		return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
	}

	/**
	 * @deprecated use {@link #getBlockMaterial(Player, PkArea)}
	 */
	@Deprecated
	public String getBlock(Player p, PkArea area) {
		return getBlockMaterial(p, area).name();
	}

	private List<Material> buildPool(String areaLower) {
		List<Material> pool = new ArrayList<>();
		for(Choice c : choices) {
			if(c.areas().isEmpty() || c.areas().contains(areaLower)) pool.add(c.material());
		}
		if(pool.isEmpty()) pool.add(choices.getFirst().material());
		return List.copyOf(pool);
	}

	private boolean isRandom(String raw) {
		return raw.equalsIgnoreCase("random") || raw.equalsIgnoreCase(plugin.settings().randomItem().name());
	}

	// ------------------------------------------------------------------ GUI

	private static final class Gui implements InventoryHolder {
		private final UUID owner;
		private Inventory inventory;
		private int page = 0;
		private final Choice[] slots = new Choice[54];

		private Gui(UUID owner) {
			this.owner = owner;
		}

		@Override
		public Inventory getInventory() {
			return inventory;
		}
	}

	/** Opens the selector. Safe to call from any thread. */
	public void openSelector(Player ply) {
		Tasks.entity(ply, () -> {
			Gui gui = new Gui(ply.getUniqueId());
			gui.inventory = Bukkit.createInventory(gui, 54, plugin.msgs.get("gui.selector.title", ply));
			render(ply, gui);
			ply.openInventory(gui.inventory);
		});
	}

	private List<Choice> displayed() {
		Material randomMat = plugin.settings().randomItem();
		return choices.stream().filter(c -> c.material() != randomMat && c.material().isItem()).toList();
	}

	private void render(Player player, Gui gui) {
		Messages msgs = plugin.msgs;
		Inventory inv = gui.inventory;
		inv.clear();
		Arrays.fill(gui.slots, null);

		Material randomMat = plugin.settings().randomItem();
		PlayerData data = plugin.scores.getCached(player.getUniqueId());
		String rawSelected = data == null ? null : data.material();
		boolean randomSelected = rawSelected == null || isRandom(rawSelected);
		Choice selectedChoice = randomSelected ? null : parse(rawSelected);
		Material selected = selectedChoice == null ? null : selectedChoice.material();

		inv.setItem(0, named(Material.ARROW, msgs.get("gui.selector.items.prevpage.name", player), null, false));
		inv.setItem(8, named(Material.ARROW, msgs.get("gui.selector.items.nextpage.name", player), null, false));
		inv.setItem(4, named(randomMat, msgs.get("gui.selector.items.random.title", player),
				msgs.get("gui.selector.items.random.lore", player), randomSelected));

		List<Choice> list = displayed();
		int start = gui.page * PER_PAGE;
		Component selectedLore = msgs.get("gui.selector.items.selected.lore", player);
		for(int i = 0; i < PER_PAGE && start + i < list.size(); i++) {
			Choice c = list.get(start + i);
			int slot = 9 + i;
			gui.slots[slot] = c;
			boolean isSelected = c.material() == selected;
			inv.setItem(slot, named(c.material(), null, isSelected ? selectedLore : null, isSelected));
		}
	}

	private static ItemStack named(Material m, @Nullable Component name, @Nullable Component lore, boolean glint) {
		ItemStack it = new ItemStack(m);
		it.editMeta(meta -> {
			if(name != null) meta.displayName(name);
			if(lore != null) meta.lore(List.of(lore));
			if(glint) meta.setEnchantmentGlintOverride(true);
		});
		return it;
	}

	@EventHandler
	public void onInvClick(InventoryClickEvent e) {
		if(!(e.getInventory().getHolder(false) instanceof Gui gui)) return;
		e.setCancelled(true);
		if(!(e.getWhoClicked() instanceof Player p) || !p.getUniqueId().equals(gui.owner)) return;
		if(e.getClickedInventory() != gui.inventory) return;

		int slot = e.getSlot();
		String choice;
		if(slot == 0) {
			if(gui.page <= 0) return;
			gui.page--;
			render(p, gui);
			return;
		} else if(slot == 8) {
			if((gui.page + 1) * PER_PAGE >= displayed().size()) return;
			gui.page++;
			render(p, gui);
			return;
		} else if(slot == 4) {
			choice = "random";
		} else if(slot >= 9 && gui.slots[slot] != null) {
			choice = gui.slots[slot].raw();
		} else {
			return;
		}
		plugin.scores.setMaterial(p.getUniqueId(), choice);
		render(p, gui);
	}
}
