package us.ajg0702.parkour;

import me.clip.placeholderapi.PlaceholderAPI;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.JoinConfiguration;
import net.kyori.adventure.text.minimessage.tag.resolver.TagResolver;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.utils.Text;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * MiniMessage messages, parsed once on load. Callers get Adventure components — never raw strings with color codes.
 */
public class Messages {

	private static Messages instance;

	private static final Set<String> NO_PREFIX = Set.of(
			"prefix",
			"score",
			"placeholders.stats.no-data",
			"placeholders.current.no-data",
			"placeholders.stats.time-format",
			"fall.force.reasons.teleport",
			"fall.force.reasons.afk",
			"gui.selector.title",
			"gui.selector.items.random.title",
			"gui.selector.items.random.lore",
			"gui.selector.items.selected.lore",
			"gui.selector.items.nextpage.name",
			"gui.selector.items.prevpage.name",
			"items.blockselector.name"
	);

	private final Main plugin;
	private final File file;

	/** MiniMessage templates with the prefix applied. */
	private volatile Map<String, String> templates = Map.of();
	/** Pre-deserialized messages that have no tags and no PlaceholderAPI. */
	private volatile Map<String, Component> statics = Map.of();

	public static Messages getInstance() {
		return instance;
	}

	public Messages(Main pl) {
		this.plugin = pl;
		this.file = new File(pl.getDataFolder(), "messages.yml");
		instance = this;
		reload();
	}

	public static TagResolver tag(String name, String value) {
		return Text.tag(name, value);
	}

	public static TagResolver tag(String name, int value) {
		return Text.tag(name, value);
	}

	public Component get(String key, TagResolver... tags) {
		return get(key, null, tags);
	}

	public Component get(String key, @Nullable OfflinePlayer player, TagResolver... tags) {
		String template = templates.get(key);
		if(template == null) {
			return Text.MINI.deserialize("<dark_red>| <red>Could not find the message '<key>'! <dark_red>| ", tag("key", key));
		}
		boolean papi = plugin.papi && player != null && template.indexOf('%') >= 0;
		if(!papi && tags.length == 0) {
			Component ready = statics.get(key);
			if(ready != null) return ready;
		}
		if(papi) {
			template = PlaceholderAPI.setPlaceholders(player, template);
		}
		return Text.MINI.deserialize(template, Text.withUnknown(tags));
	}

	public void send(CommandSender to, String key, TagResolver... tags) {
		to.sendMessage(get(key, to instanceof OfflinePlayer p ? p : null, tags));
	}

	public void send(CommandSender to, @Nullable OfflinePlayer player, String key, TagResolver... tags) {
		to.sendMessage(get(key, player, tags));
	}

	public String plain(String key, TagResolver... tags) {
		return Text.plain(get(key, tags));
	}

	public String plain(String key, @Nullable OfflinePlayer player, TagResolver... tags) {
		return Text.plain(get(key, player, tags));
	}

	public Component parse(String text, TagResolver... tags) {
		return Text.parse(text, tags);
	}

	public static Component join(Component... lines) {
		return Component.join(JoinConfiguration.separator(Component.newline()), lines);
	}

	public static Component join(List<Component> lines) {
		return Component.join(JoinConfiguration.separator(Component.newline()), lines);
	}

	public void reload() {
		YamlConfiguration msgs = YamlConfiguration.loadConfiguration(file);
		boolean changed = false;

		for(Map.Entry<String, String> e : defaults().entrySet()) {
			if(!msgs.isSet(e.getKey())) {
				msgs.set(e.getKey(), e.getValue());
				changed = true;
			}
		}

		Map<String, String> moved = new HashMap<>();
		moved.put("top-header", "top.header");
		moved.put("list-header", "list.header");
		moved.put("block-potion", "block.potion");
		moved.put("startfirst", "start.first");
		moved.put("fall.force.afk", "fall.force.reasons.afk");
		for(Map.Entry<String, String> e : moved.entrySet()) {
			if(msgs.isString(e.getKey())) {
				msgs.set(e.getValue(), msgs.getString(e.getKey()));
				msgs.set(e.getKey(), null);
				changed = true;
			}
		}

		if(changed) {
			msgs.options().setHeader(List.of(
					"",
					"MiniMessage is used for all messages: https://docs.advntr.dev/minimessage/format.html",
					"Placeholders look like <score>, <player>, <cmd>, <area>.",
					"Legacy & codes in existing files still work.",
					"Delete a line and restart to restore that message's default.",
					""
			));
			try {
				msgs.save(file);
			} catch(IOException e) {
				plugin.getLogger().warning("Could not save messages file!");
			}
		}

		String prefix = Text.modernize(msgs.getString("prefix", ""));
		Map<String, String> newTemplates = new HashMap<>();
		Map<String, Component> newStatics = new HashMap<>();
		for(String key : msgs.getKeys(true)) {
			if(!msgs.isString(key)) continue;
			String value = Text.modernize(msgs.getString(key));
			if(!NO_PREFIX.contains(key) && !prefix.isEmpty()) {
				value = prefix + value;
			}
			newTemplates.put(key, value);
			if(value.indexOf('<') < 0 && value.indexOf('%') < 0) {
				newStatics.put(key, Text.MINI.deserialize(value));
			} else if(value.indexOf('%') < 0 && !value.contains("<score>") && !value.contains("<highscore>")
					&& !value.contains("<cmd>") && !value.contains("<name>") && !value.contains("<player>")
					&& !value.contains("<area>") && !value.contains("<input>") && !value.contains("<options>")
					&& !value.contains("<pos>") && !value.contains("<time>") && !value.contains("<difficulty>")
					&& !value.contains("<coords>") && !value.contains("<count>") && !value.contains("<file>")
					&& !value.contains("<version>") && !value.contains("<w>") && !value.contains("<h>")
					&& !value.contains("<l>") && !value.contains("<m>") && !value.contains("<s>")
					&& !value.contains("<key>")) {
				try {
					newStatics.put(key, Text.MINI.deserialize(value));
				} catch(Exception ignored) {}
			}
		}
		templates = Map.copyOf(newTemplates);
		statics = Map.copyOf(newStatics);
	}

	private static Map<String, String> defaults() {
		Map<String, String> d = new LinkedHashMap<>();
		d.put("prefix", "");
		d.put("alreadyin", "<red>You are already in parkour!");
		d.put("start.score", "<green>Started parkour! <gray>Try to beat your high-score of <reset><score></reset><gray>!");
		d.put("start.first", "<green>Started parkour!");
		d.put("noperm", "<red>You do not have permission to do that!");
		d.put("score", "<gray>Score <reset><b><score>");
		d.put("beatrecord", "<green>Congrats!<gray> You beat your previous score of <white><score><gray>!");
		d.put("beatrecord-ingame", "<green>Congrats! <gray>You just beat your previous highscore! Keep going!");
		d.put("disabledworld", "<red>You cannot start parkour from this world!");

		d.put("block.potion", "<red>Nope!");
		d.put("block.place", "<red>Nope!");
		d.put("block.break", "<red>Nope!");
		d.put("numberformatexception", "<red>That is not a valid number!");
		d.put("areafull", "<red>Could not start parkour because the area is full! <gray>Try again later.");

		d.put("must-be-ingame", "<red>You must be in-game to do that!");
		d.put("not-in-game", "<red>You must do this command from the console!");
		d.put("couldnt-find-player", "<red>Could not find player <player>");
		d.put("list.header", "<blue>Players playing parkour:");
		d.put("list.format", "<gold><name> <gray>- <yellow><score> jumps");
		d.put("list.none", "<gray>None");
		d.put("nobodys-played-yet", "<red>Nobody has played parkour yet!");
		d.put("reload.usage", "<red>Usage: /<cmd> reload <file><gray> Files: <options>");
		d.put("reload.success", "<green>Reloaded <file>!");
		d.put("reload.unknown", "<red>Could not find file for <file>!");

		d.put("top.header", "<blue>Top Scores:");
		d.put("top.header-area", "<blue>Top Scores for <area>:");
		d.put("top.format", "<green><pos>. <gold><name> <yellow><score> jumps");

		d.put("fall.normal", "<red>You fell! <gray>Your score was <reset><score>");
		d.put("fall.force.base", "<red>Your parkour game was ended because ");
		d.put("fall.force.reasons.teleport", "you teleported");
		d.put("fall.force.reasons.afk", "you went AFK.");

		d.put("errors.blocknotair.base", "<red><b>An error occured:</b><red> The block is not air!");
		d.put("errors.blocknotair.player", "<gold>Tell an admin, and try again later.");
		d.put("errors.blocknotair.admin", "<gold>Make sure the parkour area is completly air, and that the plugin has been correctly set up by reading /<cmd> setup.");
		d.put("errors.notsetup.base", "<red>ajParkour has not been set up yet.");
		d.put("errors.notsetup.player", "<gold>Ask an admin to set it up!");
		d.put("errors.notsetup.admin", "<gold>For info on how to set it up, do /<cmd> setup");
		d.put("errors.too-many-players", "<red>There are too many players in parkour right now. Try again later.");
		d.put("errors.inventory-save", "<red>An error occurred while trying to save your inventory!");

		d.put("placeholders.stats.no-data", "---");
		d.put("placeholders.current.no-data", "0");
		d.put("placeholders.stats.time-format", "<m>m <s>s");

		d.put("commands.help.header", "<green>ajParkour made by <dark_green>ajgeiss0702<green>!");
		d.put("commands.help.start", "<gold> /<cmd> start <gray>- <blue>Start parkour!");
		d.put("commands.help.startarea", "<gold> /<cmd> start <area> <gray>- <blue>Start parkour in a certain area.");
		d.put("commands.help.startothers", "<gold> /<cmd> start <player> [area] <gray>- <blue>Start another player's parkour in a certain area.");
		d.put("commands.help.top", "<gold> /<cmd> top <gray>- <blue>See the top 10 players!");
		d.put("commands.help.list", "<gold> /<cmd> list <gray>- <blue>See everyone that is playing parkour, and their current scores.");
		d.put("commands.help.setup", "<gold> /<cmd> setup <gray>- <blue>Commands for setting up the plugin. <gray>(required permssion: ajparkour.setup)");
		d.put("commands.help.portals", "<gold> /<cmd> portals <gray>- <blue>Commands for setting up portals.");
		d.put("commands.help.areas", "<gold> /<cmd> areas <gray>- <blue>List all loaded areas");

		d.put("commands.setup.header", "<green>ajParkour<dark_green> setup commands");
		d.put("commands.setup.create", "<gold> /<cmd> setup create <gray>- <blue>Create an area");
		d.put("commands.setup.pos1", "<gold> /<cmd> setup pos1 <gray>- <blue>Set the first position");
		d.put("commands.setup.pos2", "<gold> /<cmd> setup pos2 <gray>- <blue>Set the second position");
		d.put("commands.setup.we", "<gold> /<cmd> setup we <gray>- <blue>Select first and second position with worldedit");
		d.put("commands.setup.fallpos", "<gold> /<cmd> setup fallpos <gray>- <blue>Set fall position!");
		d.put("commands.setup.edit", "<gold> /<cmd> edit <gray>- <blue>Load an existing area into the editing queue.");
		d.put("commands.setup.info", "<gold> /<cmd> setup info <gray>- <blue>Show which points you have set so far!");
		d.put("commands.setup.diff", "<gold> /<cmd> setup difficulty <gray>- <blue>Set the area's difficulty!");
		d.put("commands.setup.max", "<gold> /<cmd> setup max <gray>- <blue>Set the maximum number of people allowed in the area!");
		d.put("commands.setup.save", "<gold> /<cmd> setup save <gray>- <blue>Saves and enabled the area. (This should be done last)");

		d.put("commands.portals.header", "<green>ajParkour<dark_green> portal commands");
		d.put("commands.portals.list", "<gold> /<cmd> portals list <gray>- <blue>Lists all portals.");
		d.put("commands.portals.create", "<gold> /<cmd> portals create <gray>- <blue>Create a portal.");
		d.put("commands.portals.remove", "<gold> /<cmd> portals remove <gray>- <blue>Remove a portal.");

		d.put("commands.listareas.header", "<blue>Loaded parkour areas:");
		d.put("commands.listareas.format", "<gray> - <green><name> <gray>Difficulty: <blue><difficulty>");
		d.put("commands.listareas.none", "<gray>No areas loaded!");

		d.put("not-from-console", "<red>You cannot run this command from the console!");

		d.put("setup.no-name", "<red>You must name your area!");
		d.put("setup.invalid-name", "<red>You can't name your arena that.");
		d.put("setup.edit.no-name", "<red>Please enter the name of an area to edit!");
		d.put("setup.edit.loaded", "<green>Area loaded for editing! Re-set positions using /<cmd> setup");
		d.put("setup.edit.no-area", "<red>An area with that name does not exist!");
		d.put("setup.need-to-create", "<green>You must create an area before editing it!");
		d.put("setup.we.not-installed", "<red>You need to have WorldEdit installed to use this command!<gray> Try using the built-in pos1 and pos2 setup commands.");
		d.put("setup.we.incomplete-selection", "<red>You must make a complete worldedit selection before doing this!");
		d.put("setup.created", "<green>Area created and added to the editing queue. See <dark_green>/ajParkour setup<green> or view the plugin page for steps to set it up!");
		d.put("setup.already-creating", "<red>An arena is currently being set up. <green>Please either save that arena, or run this command again to override. (<red><b>NOTE:</b><red> Overriding will erase all of the data of the arena being set up before!)");
		d.put("setup.info.header", "<gold>Colors: <green>Set <gray>Not set (optional) <red>Not set (required)");
		d.put("setup.info.set", "<green><name>");
		d.put("setup.info.missing-required", "<red><name>");
		d.put("setup.info.missing-optional", "<gray><name>");
		d.put("setup.set.pos1", "<green>First position set!");
		d.put("setup.set.pos2", "<green>Second position set!");
		d.put("setup.set.fallpos", "<green>Fall position set!");
		d.put("setup.set.we", "<green>Positions set!");
		d.put("setup.set.diff", "<green>Difficulty set!");
		d.put("setup.set.max", "<green>Max players set!");
		d.put("setup.max.need-args", "<red>Please enter a number!");
		d.put("setup.diff.not-valid", "<red>Invalid difficulty! <gray>Valid difficulties are <white>easy<gray>, <white>medium<gray>, <white>hard<gray>, <white>expert<gray>, and <white>balanced");
		d.put("setup.diff.need-args", "<red>Please enter a difficulty! <gray>Valid difficulties are <white>easy<gray>, <white>medium<gray>, <white>hard<gray>, <white>expert<gray>, and <white>balanced<gray>.\n<green>Balanced difficulty will make the parkour get harder as the player gets higher scores.");
		d.put("setup.save.not-done", "<red>You have not set all of the required settings! <gray>Check <white>/<cmd> setup info<gray> to see what you have left.");
		d.put("setup.save.success", "<green>Arena <name> saved and loaded!");
		d.put("setup.area.too-small", "<red>The area you just created does not meet the minimum reccomended size of 20x20x20!\n<gray>You can continue creating the area, but know you may run into issues with players running into each other, and possibly parkour going outside the area!\n<gray>Your area: <l>x<w>x<h> (XxZxY)");

		d.put("portals.list.header", "<dark_green>Parkour Portals");
		d.put("portals.list.format.area", "<dark_green><name> <gray>- <green><coords> <gray>- <green><area>");
		d.put("portals.list.format.no-area", "<dark_green><name> <gray>- <green><coords>");
		d.put("portals.create.help", "<red>Usage: /<cmd> portals create <name> [area]");
		d.put("portals.create.success", "<green>Created a portal named '<name>' at your current location! <gray>You might have to restart the server for the portal to start working.");
		d.put("portals.remove.help", "<red>Please enter the name of the portal to be removed!");
		d.put("portals.remove.cannot-find", "<red>Cannot find a portal with that name! Make sure you spelled it correctly, and that you put the same caps too.");
		d.put("portals.remove.success", "<green>Removed portal '<name>'!");
		d.put("portals.remove.no-portals", "<red>No portals have been set up yet! Please create portals before removing them.");

		d.put("gui.selector.title", "Block Selector");
		d.put("gui.selector.items.random.title", "<reset>Random Blocks");
		d.put("gui.selector.items.random.lore", "<gray>Will pick a random block.");
		d.put("gui.selector.items.selected.lore", "<green>Currently Selected");
		d.put("gui.selector.items.nextpage.name", "<green><b>Next Page");
		d.put("gui.selector.items.prevpage.name", "<green><b>Previous Page");

		d.put("items.blockselector.name", "<green>Block Selector");

		d.put("start.no-area-player", "<red>No arena or player could be found called '<input>'!");
		d.put("start.unknown-player", "<red>Cannot find that player!");
		d.put("start.unknown-area", "<red>Cannot find that area!");
		d.put("start.started-other", "<green>Started parkour for <player>!");
		d.put("start.started-other-area", "<green>Started parkour for <b><white><player></b><green> on area <b><white><area></b><green>!");

		d.put("commands.not-on-whitelist", "<red>You cannot use this command while on parkour!");

		d.put("reset.usage", "<red>Usage: /<cmd> reset <player>");
		d.put("reset.success", "<green>Reset score of <player> to 0");

		d.put("blockselector.openedfor", "<green>Opened block selector for <player>");

		d.put("areas.remove.help", "<red>Please enter the name of the area to be removed!");
		d.put("areas.remove.cannot-find", "<red>Cannot find an area with that name! Make sure you spelled it correctly, and that you put the same caps too.");
		d.put("areas.remove.success", "<green>Removed area '<name>'!");

		d.put("version", "<green>ajParkour <dark_green>v<green><version> <dark_green>by <gold>ajgeiss0702 <gray>(https://www.spigotmc.org/members/ajgeiss0702.49935?)");

		d.put("updater.bar", "<gray><st>                                                         </st>");
		d.put("updater.available", "<newline><newline>  <green>An update is available for ajParkour!<newline>  <dark_green>You can download it using /ajParkour update<newline><newline>");
		d.put("updater.disabled", "<red>The updater is disabled.");
		d.put("updater.none", "<red>There is no update to download!");
		d.put("updater.downloading", "<gray>Downloading ajParkour <version>...");
		d.put("updater.folder-fail", "<red>Unable to create the update folder. Check console for more info");
		d.put("updater.success", "<green>Success! <gray>Restart the server and the new version will be ready!");
		d.put("updater.fail", "<red>An error occurred while trying to download the newest version. Check console for more info");
		return d;
	}
}
