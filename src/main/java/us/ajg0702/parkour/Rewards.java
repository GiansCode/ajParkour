package us.ajg0702.parkour;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.api.events.PlayerEndParkourEvent;
import us.ajg0702.parkour.api.events.PlayerStartParkourEvent;
import us.ajg0702.parkour.game.PkArea;
import us.ajg0702.parkour.game.PkPlayer;
import us.ajg0702.parkour.storage.PlayerData;
import us.ajg0702.parkour.top.TopManager;
import us.ajg0702.parkour.utils.Tasks;
import us.ajg0702.parkour.utils.Text;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class Rewards implements Listener {

	/**
	 * A reward from rewards.yml, parsed once on load.
	 * @param areas raw area rules; a leading '!' excludes the area
	 * @param permission required permission; a leading '!' requires the player to NOT have it
	 */
	private record Reward(String key, int number, String message, List<String> commands, boolean firstTimeOnly,
	                      List<String> areas, String permission, int cooldown) {}

	private record Special(String message, List<String> commands) {}

	private final Main plugin;
	private final File rwFile;

	private volatile List<Reward> exceptions = List.of();
	private volatile Map<Integer, List<Reward>> exceptionsByScore = Map.of();
	private volatile List<Reward> intervals = List.of();
	private volatile Special beatServerRecord = new Special("", List.of());
	private volatile Special start = new Special("", List.of());
	private volatile Special end = new Special("", List.of());

	private final Map<UUID, Map<String, Long>> cooldowns = new ConcurrentHashMap<>();

	public Rewards(Main plugin) {
		this.plugin = plugin;
		rwFile = new File(plugin.getDataFolder(), "rewards.yml");
		reload();
	}

	public void reload() {
		YamlConfiguration rw = YamlConfiguration.loadConfiguration(rwFile);
		YamlConfiguration oldconfig = YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "config.yml"));
		boolean changed = false;
		if(!rw.isSet("intervals")) {
			changed = true;
			if(oldconfig.isSet("rewards.interval")) {
				ConfigurationSection r = oldconfig.getConfigurationSection("rewards");
				int interval = r.getInt("interval");
				rw.set("intervals." + interval + ".message", r.getString("message"));
				rw.set("intervals." + interval + ".commands", r.isString("command")
						? Collections.singletonList(r.getString("command"))
						: r.getStringList("command"));
			} else {
				rw.set("intervals.10.message", "<green>Congrats! <gray>You got to <score> jumps! <aqua>Have a diamond!");
				rw.set("intervals.10.commands", Collections.singletonList("give {PLAYER} diamond 1"));
			}
		}
		if(!rw.isSet("exceptions")) {
			changed = true;
			if(oldconfig.isSet("rewards.exceptions")) {
				ConfigurationSection s = oldconfig.getConfigurationSection("rewards.exceptions");
				for(String k : s.getKeys(false)) {
					ConfigurationSection ss = s.getConfigurationSection(k);
					s.set(k + ".commands", ss.isString("command")
							? Collections.singletonList(ss.getString("command"))
							: ss.getStringList("command"));
					s.set(k + ".command", null);
				}
				rw.set("exceptions", s);
			} else {
				rw.set("exceptions.1.message", "<green><b>Congrats, you made a jump!");
				rw.set("exceptions.1.first-time-only", true);
			}
		}
		if(!rw.isSet("specials")) {
			changed = true;
			rw.set("specials.beat-server-record.message", "<green><b>Congrats!</b><gray> You reached the server high score!");
			rw.set("specials.beat-server-record.commands", Collections.singletonList("give {PLAYER} emerald 1"));
		}
		if(!rw.isSet("specials.start")) {
			changed = true;
			rw.set("specials.start.message", "");
			rw.set("specials.start.commands", Collections.emptyList());
		}
		if(!rw.isSet("specials.end")) {
			changed = true;
			rw.set("specials.end.message", "");
			rw.set("specials.end.commands", Collections.emptyList());
		}
		if(changed) {
			rw.options().setHeader(List.of("This is the rewards file.", "Here is a guide for this: https://wiki.ajg0702.us/ajparkour/configs/Rewards"));
			try {
				rw.save(rwFile);
			} catch(IOException e) {
				plugin.getLogger().severe("Unable to save rewards file: " + e.getMessage());
			}
		}

		exceptions = parseRewards(rw.getConfigurationSection("exceptions"));
		Map<Integer, List<Reward>> byScore = new java.util.HashMap<>();
		for(Reward r : exceptions) {
			byScore.computeIfAbsent(r.number(), _ -> new ArrayList<>()).add(r);
		}
		Map<Integer, List<Reward>> frozen = new java.util.HashMap<>();
		byScore.forEach((k, v) -> frozen.put(k, List.copyOf(v)));
		exceptionsByScore = Map.copyOf(frozen);
		intervals = parseRewards(rw.getConfigurationSection("intervals"));
		beatServerRecord = parseSpecial(rw, "beat-server-record");
		start = parseSpecial(rw, "start");
		end = parseSpecial(rw, "end");
	}

	private List<Reward> parseRewards(@Nullable ConfigurationSection section) {
		if(section == null) return List.of();
		List<Reward> out = new ArrayList<>();
		for(String key : section.getKeys(false)) {
			int n;
			try {
				n = Integer.decode(key.split("-")[0]);
			} catch(NumberFormatException e) {
				plugin.getLogger().warning("Invalid reward number '" + key + "' in rewards.yml");
				continue;
			}
			String areas = section.getString(key + ".areas", "");
			out.add(new Reward(key, n,
					Text.modernize(section.getString(key + ".message", "")),
					section.getStringList(key + ".commands"),
					section.getBoolean(key + ".first-time-only", false),
					areas.isEmpty() ? List.of() : List.of(areas.split(",")),
					section.getString(key + ".permission", ""),
					section.getInt(key + ".cooldown", 0)));
		}
		return List.copyOf(out);
	}

	private static Special parseSpecial(YamlConfiguration rw, String key) {
		return new Special(Text.modernize(rw.getString("specials." + key + ".message", "")), rw.getStringList("specials." + key + ".commands"));
	}

	public void checkRewards(PkPlayer p, final int score, PkArea area) {
		Player player = p.getPlayer();
		String scoreStr = Integer.toString(score);

		if(!p.beatServerHighscore) {
			TopManager top = TopManager.getInstance();
			if(top.isLoaded(null) && score > top.getCachedTopScore(null)) {
				p.beatServerHighscore = true;
				Special s = beatServerRecord;
				if(!s.message().isEmpty()) {
					sendReward(player, s.message(), scoreStr);
				}
				if(!s.commands().isEmpty()) {
					executeCommands(replaceScore(s.commands(), scoreStr), p);
				}
			} else if(!top.isLoaded(null)) {
				top.getTop(1, null);
			}
		}

		List<Reward> matched = exceptionsByScore.get(score);
		if(matched != null) for(Reward r : matched) {
			if(r.firstTimeOnly()) {
				PlayerData data = plugin.scores.getCached(player.getUniqueId());
				if(data != null && r.number() <= data.highScore(null)) continue;
			}
			if(!passes(r, player, area)) continue;
			if(onCooldown(r, player)) continue;
			give(r, p, scoreStr);
			break;
		}

		for(Reward r : intervals) {
			if(r.number() <= 0 || score % r.number() != 0) continue;
			if(!passes(r, player, area)) continue;
			give(r, p, scoreStr);
		}
	}

	private boolean passes(Reward r, Player player, PkArea area) {
		for(String a : r.areas()) {
			boolean not = a.startsWith("!");
			PkArea ar = plugin.man.getArea(not ? a.substring(1) : a);
			if(ar == null) continue;
			if(not ? ar.getName().equals(area.getName()) : !ar.getName().equals(area.getName())) return false;
		}
		String permission = r.permission();
		if(!permission.isEmpty()) {
			if(permission.startsWith("!")) {
				if(player.hasPermission(permission.substring(1))) return false;
			} else if(!player.hasPermission(permission)) {
				return false;
			}
		}
		return true;
	}

	private boolean onCooldown(Reward r, Player player) {
		if(r.cooldown() <= 0) return false;
		Map<String, Long> cds = cooldowns.computeIfAbsent(player.getUniqueId(), _ -> new ConcurrentHashMap<>());
		long now = System.currentTimeMillis();
		Long last = cds.get(r.key());
		if(last != null && (now - last) / 1000 < r.cooldown()) return true;
		cds.put(r.key(), now);
		return false;
	}

	private void give(Reward r, PkPlayer p, String scoreStr) {
		if(!r.message().isEmpty()) {
			sendReward(p.getPlayer(), r.message(), scoreStr);
		}
		executeCommands(replaceScore(r.commands(), scoreStr), p);
	}

	private static List<String> replaceScore(List<String> cmds, String score) {
		return cmds.stream().map(s -> s.replace("{SCORE}", score)).toList();
	}

	public void executeCommands(List<String> cmds, PkPlayer p) {
		executeCommands(cmds, p, false);
	}

	public void executeCommands(List<String> cmds, PkPlayer p, boolean dontSkip) {
		if(!dontSkip && plugin.settings().executeRewardsAfter()) {
			p.addCommands(cmds);
			return;
		}
		staticExecuteCommands(cmds, p.getPlayer());
	}

	@EventHandler
	public void onStart(PlayerStartParkourEvent e) {
		Player p = e.getPlayer();
		Special s = start;
		sendReward(p, s.message(), "0");
		staticExecuteCommands(s.commands(), p);
	}

	@EventHandler
	public void onFall(PlayerEndParkourEvent e) {
		Player p = e.getPlayer();
		String score = Integer.toString(e.getFallScore());
		Special s = end;
		sendReward(p, s.message(), score);
		staticExecuteCommands(replaceScore(s.commands(), score), p);
	}

	private void sendReward(Player p, String template, String score) {
		if(template.isEmpty()) return;
		if(plugin.papi) {
			template = PlaceholderAPI.setPlaceholders(p, template);
		}
		p.sendMessage(Text.parse(template, Text.tag("score", score), Text.tag("player", p.getName())));
	}

	/**
	 * Runs reward commands. Console commands run on the global thread, [p] commands on the player's thread.
	 */
	public static void staticExecuteCommands(List<String> cmds, Player p) {
		for(String cmdr : cmds) {
			if(cmdr == null) continue;
			boolean execAsPlayer = cmdr.startsWith("[p]");
			String cmd = execAsPlayer ? cmdr.substring(3) : cmdr;
			cmd = cmd.stripLeading();
			if(cmd.startsWith("/")) cmd = cmd.substring(1);
			String finalCmd = cmd.replace("{PLAYER}", p.getName());
			if(execAsPlayer) {
				Tasks.entity(p, () -> Bukkit.dispatchCommand(p, finalCmd));
			} else {
				Tasks.global(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCmd));
			}
		}
	}
}
