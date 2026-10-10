package us.ajg0702.parkour;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.game.BlockPos;
import us.ajg0702.parkour.game.Difficulty;
import us.ajg0702.parkour.game.PkArea;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import java.util.UUID;
import java.util.logging.Level;

/**
 * Stores parkour areas and portals (positions.yml). Everything is parsed once and kept in immutable snapshots,
 * so lookups (including the per-move portal check) never touch YAML.
 */
public class AreaStorage {

	private final Main plugin;
	private final File configfile;
	private YamlConfiguration config;

	private volatile List<PkArea> areas = List.of();
	private volatile Map<String, PkArea> areasByName = Map.of();
	private volatile List<Portal> portals = List.of();
	private volatile Map<UUID, Map<Long, Portal>> portalIndex = Map.of();
	private volatile Set<String> areaNames = Set.of();
	private volatile Set<String> portalNames = Set.of();

	public AreaStorage(Main plugin) {
		this.plugin = plugin;
		this.configfile = new File(plugin.getDataFolder(), "positions.yml");
		this.config = YamlConfiguration.loadConfiguration(configfile);
	}

	// ------------------------------------------------------------------ queries

	public List<PkArea> getAreas() {
		return areas;
	}

	public @Nullable PkArea getArea(@Nullable String name) {
		return name == null ? null : areasByName.get(name.toLowerCase(Locale.ROOT));
	}

	public List<Portal> getPortals() {
		return portals;
	}

	public boolean hasPortals() {
		return !portalIndex.isEmpty();
	}

	public @Nullable Portal portalAt(Location l) {
		Map<UUID, Map<Long, Portal>> index = portalIndex;
		if(index.isEmpty()) return null;
		Map<Long, Portal> world = index.get(l.getWorld().getUID());
		return world == null ? null : world.get(BlockPos.key(l.getBlockX(), l.getBlockY(), l.getBlockZ()));
	}

	/** Names of every configured area, including ones that failed to load. */
	public Set<String> getAreaNames() {
		return areaNames;
	}

	public Set<String> getPortalNames() {
		return portalNames;
	}

	public boolean hasArea(String name) {
		return areaNames.contains(name) || areasByName.containsKey(name.toLowerCase(Locale.ROOT));
	}

	// ------------------------------------------------------------------ loading

	public synchronized void reload() {
		config = YamlConfiguration.loadConfiguration(configfile);
		migrateOldPortals();
		rebuild();
	}

	private void rebuild() {
		List<PkArea> newAreas = new ArrayList<>();
		ConfigurationSection s = config.getConfigurationSection("areas");
		if(s != null) {
			for(String name : s.getKeys(false)) {
				try {
					PkArea area = parseArea(name, s);
					if(area != null) newAreas.add(area);
				} catch(IllegalArgumentException e) {
					plugin.getLogger().warning("Could not load area '" + name + "': " + e.getMessage());
				}
			}
		}
		Map<String, PkArea> byName = new HashMap<>();
		for(PkArea a : newAreas) byName.put(a.getName().toLowerCase(Locale.ROOT), a);
		areas = List.copyOf(newAreas);
		areasByName = Map.copyOf(byName);
		areaNames = s == null ? Set.of() : Set.copyOf(new TreeSet<>(s.getKeys(false)));

		List<Portal> newPortals = new ArrayList<>();
		Map<UUID, Map<Long, Portal>> index = new HashMap<>();
		ConfigurationSection ps = config.getConfigurationSection("portals");
		if(ps != null) {
			for(String name : ps.getKeys(false)) {
				Location loc;
				try {
					loc = parseLoc(ps.getString(name + ".loc"));
				} catch(IllegalArgumentException e) {
					plugin.getLogger().warning("Could not load portal '" + name + "': " + e.getMessage());
					continue;
				}
				if(loc == null || loc.getWorld() == null) {
					plugin.getLogger().warning("Could not load portal '" + name + "': its world is not loaded.");
					continue;
				}
				String areaRaw = ps.getString(name + ".area");
				PkArea area = null;
				if(areaRaw != null) {
					area = getArea(areaRaw);
					if(area == null) {
						plugin.getLogger().warning("Unable to load area for portal '" + name + "'! Did the area get deleted?");
					}
				}
				Portal portal = new Portal(name, loc, area);
				newPortals.add(portal);
				index.computeIfAbsent(loc.getWorld().getUID(), _ -> new HashMap<>())
						.put(BlockPos.key(loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()), portal);
			}
		}
		Map<UUID, Map<Long, Portal>> frozen = new HashMap<>();
		index.forEach((k, v) -> frozen.put(k, Map.copyOf(v)));
		portals = List.copyOf(newPortals);
		portalIndex = Map.copyOf(frozen);
		portalNames = ps == null ? Set.of() : Set.copyOf(new TreeSet<>(ps.getKeys(false)));
	}

	private @Nullable PkArea parseArea(String name, ConfigurationSection s) {
		Location p1 = parseLoc(s.getString(name + ".pos1"));
		Location p2 = parseLoc(s.getString(name + ".pos2"));
		Location fp = parseLoc(s.getString(name + ".fallpos"));
		if(p1 == null || p2 == null) {
			plugin.getLogger().warning("Area '" + name + "' is missing a position. Please edit the area and set both positions!");
			return null;
		}
		if(p1.getWorld() == null) {
			plugin.getLogger().warning("Area '" + name + "' is in a world that isn't loaded. Skipping it.");
			return null;
		}
		String diffraw = s.getString(name + ".difficulty");
		Difficulty diff;
		if(diffraw == null) {
			diff = Difficulty.BALANCED;
			plugin.getLogger().warning("Could not load difficulty for area '" + name + "'! Defaulting to BALANCED. Please edit the area and set the difficulty!");
		} else {
			try {
				diff = Difficulty.valueOf(diffraw.toUpperCase(Locale.ROOT));
			} catch(IllegalArgumentException e) {
				diff = Difficulty.BALANCED;
				plugin.getLogger().warning("Could not load difficulty for area '" + name + "'! Defaulting to BALANCED. Please edit the area and fix the difficulty!");
			}
		}
		return new PkArea(name, p1, p2, fp, diff, s.getInt(name + ".max", -1));
	}

	private void migrateOldPortals() {
		if(config.isSet("portals")) return;
		YamlConfiguration oldconfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
		if(!oldconfig.isSet("area.portals")) return;
		int i = 0;
		for(Map<?, ?> p : oldconfig.getMapList("area.portals")) {
			i++;
			World world = Bukkit.getWorld(String.valueOf(p.get("world")));
			if(world == null) {
				plugin.getLogger().warning("Could not convert portal: Unknown world '" + p.get("world") + "'!");
				continue;
			}
			Location ploc = new Location(world,
					Integer.parseInt(String.valueOf(p.get("x"))),
					Integer.parseInt(String.valueOf(p.get("y"))),
					Integer.parseInt(String.valueOf(p.get("z"))));
			config.set("portals." + i, serialize(new Portal(Integer.toString(i), ploc, null)));
		}
		saveFile();
	}

	// ------------------------------------------------------------------ editing

	public synchronized void save(PkArea area) {
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("pos1", locString(area.getPos1()));
		d.put("pos2", locString(area.getPos2()));
		if(area.getFallPos() != null) d.put("fallpos", locString(area.getFallPos()));
		d.put("difficulty", area.getDifficulty().toString());
		d.put("max", area.getRawMax());
		config.set("areas." + area.getName(), d);
		saveFile();
		rebuild();
	}

	public synchronized void save(Portal portal) {
		config.set("portals." + portal.getName(), serialize(portal));
		saveFile();
		rebuild();
	}

	private Map<String, Object> serialize(Portal portal) {
		Map<String, Object> d = new LinkedHashMap<>();
		d.put("loc", locString(portal.getLoc()));
		if(portal.getArea() != null) d.put("area", portal.getArea().getName());
		return d;
	}

	public synchronized void removeArea(String area) {
		if(!config.isSet("areas." + area)) return;
		config.set("areas." + area, null);
		saveFile();
		rebuild();
	}

	public synchronized boolean removePortal(String name) {
		if(!config.isSet("portals." + name)) return false;
		config.set("portals." + name, null);
		saveFile();
		rebuild();
		return true;
	}

	/** Serializes on the calling thread, writes the file on the IO thread. */
	private void saveFile() {
		config.options().setHeader(List.of(
				" This is where the plugin stores all the areas and portals.",
				" Be careful when editing this file, it could cause some areas or portals to fail to load!"));
		String data = config.saveToString();
		plugin.io().execute(() -> {
			try {
				File tmp = new File(configfile.getParentFile(), configfile.getName() + ".tmp");
				Files.writeString(tmp.toPath(), data, StandardCharsets.UTF_8);
				Files.move(tmp.toPath(), configfile.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
			} catch(IOException e) {
				plugin.getLogger().log(Level.SEVERE, "Failed to save areas config:", e);
			}
		});
	}

	// ------------------------------------------------------------------ helpers

	public static @Nullable Location parseLoc(@Nullable String raw) {
		if(raw == null) return null;
		String[] a = raw.split(",");
		if(a.length == 6) {
			return new Location(Bukkit.getWorld(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]), Float.parseFloat(a[4]), Float.parseFloat(a[5]));
		} else if(a.length == 4) {
			return new Location(Bukkit.getWorld(a[0]), Double.parseDouble(a[1]), Double.parseDouble(a[2]), Double.parseDouble(a[3]));
		}
		throw new IllegalArgumentException("Expected 4 or 6 numbers but got " + a.length);
	}

	private static String locString(Location l) {
		return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ() + "," + l.getYaw() + "," + l.getPitch();
	}

	public static String coordsString(@Nullable Location loc) {
		return coordsString(loc, false);
	}

	public static String coordsString(@Nullable Location loc, boolean world) {
		if(loc == null) return "null";
		String coords = loc.getBlockX() + ", " + loc.getBlockY() + ", " + loc.getBlockZ();
		return world ? loc.getWorld().getName() + ": " + coords : coords;
	}
}
