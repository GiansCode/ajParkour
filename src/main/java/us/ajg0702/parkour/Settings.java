package us.ajg0702.parkour;

import net.kyori.adventure.key.InvalidKeyException;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.sound.Sound;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.FileConfiguration;
import org.jspecify.annotations.Nullable;

import java.util.Arrays;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * An immutable, pre-parsed snapshot of config.yml so hot paths never touch YAML.
 */
public record Settings(
		boolean areaSelectionLowest,
		boolean randomBlockEachJump,
		Material randomItem,
		@Nullable Sound jumpSound,
		@Nullable Sound startSound,
		@Nullable Sound endSound,
		int topShown,
		int jumpsAhead,
		@Nullable Particle newBlockParticle,
		int particleCount,
		boolean executeRewardsAfter,
		boolean parkourInventory,
		Set<String> startDisabledWorlds,
		int kickTime,
		boolean notifyUpdate,
		boolean beginScorePerArea,
		boolean enablePortals,
		boolean enableUpdater,
		boolean enableBlockSelectorItem,
		boolean requirePermissionForBlockSelectorItem,
		boolean debug
) {

	public static Settings load(FileConfiguration c, Logger log) {
		String areaSelection = c.getString("area-selection", "lowest");
		boolean lowest = areaSelection.equalsIgnoreCase("lowest");
		if(!lowest) {
			log.warning("area-selection in config was not set correctly! Will only use first parkour area.");
		}

		Material randomItem = Material.matchMaterial(c.getString("random-item", "VINE"));
		if(randomItem == null || !randomItem.isItem()) {
			log.warning("random-item '" + c.getString("random-item") + "' is not a valid item! Using VINE.");
			randomItem = Material.VINE;
		}

		Particle particle = null;
		String particleRaw = c.getString("new-block-particle", "CLOUD");
		if(!particleRaw.equalsIgnoreCase("none")) {
			try {
				particle = Particle.valueOf(particleRaw.toUpperCase(Locale.ROOT));
				if(particle.getDataType() != Void.class) {
					log.warning("Particle '" + particleRaw + "' requires extra data and cannot be used for new-block-particle.");
					particle = null;
				}
			} catch(IllegalArgumentException e) {
				log.warning("Cannot find particle '" + particleRaw + "'!");
			}
		}

		Set<String> disabledWorlds = Arrays.stream(c.getString("start-disabled-worlds", "").split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.collect(Collectors.toUnmodifiableSet());

		return new Settings(
				lowest,
				c.getString("random-block-selection", "each").equalsIgnoreCase("each"),
				randomItem,
				parseSound(c.getString("jump-sound"), log),
				parseSound(c.getString("start-sound"), log),
				parseSound(c.getString("end-sound"), log),
				Math.max(1, c.getInt("top-shown", 10)),
				Math.max(0, c.getInt("jumps-ahead", 1)),
				particle,
				c.getInt("particle-count", 25),
				!c.getString("execute-reward-commands", "earned").equalsIgnoreCase("earned"),
				c.getBoolean("parkour-inventory", false),
				disabledWorlds,
				c.getInt("kick-time", 60),
				c.getBoolean("notify-update", true),
				c.getBoolean("begin-score-per-area", false),
				c.getBoolean("enable-portals", true),
				c.getBoolean("enable-updater", true),
				c.getBoolean("enable-block-selector-item", true),
				c.getBoolean("require-permission-for-block-selector-item", false),
				c.getBoolean("debug", false)
		);
	}

	@SuppressWarnings({"deprecation", "removal"})
	private static @Nullable Sound parseSound(@Nullable String raw, Logger log) {
		if(raw == null || raw.isBlank() || raw.equalsIgnoreCase("none")) return null;
		try {
			Key key;
			if(raw.indexOf(':') >= 0 || raw.indexOf('.') >= 0) {
				key = Key.key(raw.toLowerCase(Locale.ROOT));
			} else {
				key = org.bukkit.Sound.valueOf(raw.toUpperCase(Locale.ROOT)).key();
			}
			return Sound.sound(key, Sound.Source.MASTER, 1f, 1f);
		} catch(IllegalArgumentException | InvalidKeyException e) {
			log.warning("Cannot find sound '" + raw + "'! Make sure it exists on the server version you are running!");
			return null;
		}
	}
}
