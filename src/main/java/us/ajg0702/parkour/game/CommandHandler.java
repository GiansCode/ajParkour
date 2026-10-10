package us.ajg0702.parkour.game;

import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import us.ajg0702.parkour.Main;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Blocks commands that are not on the whitelist while a player is in parkour.
 */
public class CommandHandler implements Listener {

	private final Main plugin;
	private final File file;
	private volatile Set<String> allowed = Set.of();

	public CommandHandler(Main plugin) {
		this.plugin = plugin;
		this.file = new File(plugin.getDataFolder(), "commands.yml");
		YamlConfiguration cfg = YamlConfiguration.loadConfiguration(file);
		if(!cfg.isSet("whitelist")) {
			cfg.set("whitelist", List.of());
			cfg.options().setHeader(List.of(
					"Commands allowed while a player is in parkour.",
					"Any other command is blocked. An empty list disables this."
			));
			try {
				cfg.save(file);
			} catch(IOException e) {
				plugin.getLogger().log(Level.SEVERE, "Unable to save commands.yml", e);
			}
		}
		reload(cfg);
	}

	public void reload() {
		reload(YamlConfiguration.loadConfiguration(file));
	}

	private void reload(YamlConfiguration cfg) {
		List<String> raw = cfg.getStringList("whitelist");
		allowed = raw.stream()
				.map(s -> s.startsWith("/") ? s.substring(1) : s)
				.map(s -> s.toLowerCase(Locale.ROOT))
				.collect(Collectors.toUnmodifiableSet());
	}

	@EventHandler(ignoreCancelled = true)
	public void onCommand(PlayerCommandPreprocessEvent e) {
		Set<String> allow = allowed;
		if(allow.isEmpty()) return;
		Player p = e.getPlayer();
		if(!plugin.man.inParkour(p)) return;
		String name = e.getMessage();
		int space = name.indexOf(' ');
		if(space >= 0) name = name.substring(0, space);
		if(name.startsWith("/")) name = name.substring(1);
		if(allow.contains(name.toLowerCase(Locale.ROOT))) return;
		e.setCancelled(true);
		p.sendMessage(plugin.msgs.get("commands.not-on-whitelist", p));
	}
}
