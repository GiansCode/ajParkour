package us.ajg0702.parkour;

import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.plugin.java.JavaPlugin;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.Messages;
import us.ajg0702.parkour.commands.ParkourCommand;
import us.ajg0702.parkour.commands.SetupSession;
import us.ajg0702.parkour.game.CommandHandler;
import us.ajg0702.parkour.game.JumpManager;
import us.ajg0702.parkour.game.Manager;
import us.ajg0702.parkour.top.TopManager;
import us.ajg0702.parkour.utils.InventoryStore;
import us.ajg0702.parkour.utils.Tasks;
import us.ajg0702.parkour.utils.Updater;

import java.io.File;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadLocalRandom;
import java.util.concurrent.TimeUnit;

public class Main extends JavaPlugin {

	public boolean papi;
	public Messages msgs;
	public Scores scores;
	public Manager man;
	public AreaStorage areaStorage;
	public BlockSelector selector;
	public Rewards rewards;
	public Placeholders placeholders;
	public InventoryStore inventories;
	public final SetupSession setup = new SetupSession();

	private volatile Settings settings;
	private boolean infiniteJump;
	private final ExecutorService io = Executors.newThreadPerTaskExecutor(
			Thread.ofVirtual().name("ajParkour-io-", 0).factory());

	@Override
	public void onEnable() {
		Tasks.init(this);
		saveDefaultConfig();
		reloadConfig();
		settings = Settings.load(getConfig(), getLogger());

		msgs = new Messages(this);
		scores = new Scores(this);
		scores.preloadAll();
		inventories = new InventoryStore(this);
		inventories.preload();
		man = new Manager(this);
		selector = new BlockSelector(this);
		areaStorage = new AreaStorage(this);
		areaStorage.reload();
		rewards = new Rewards(this);
		CommandHandler commandHandler = new CommandHandler(this);
		man.start();

		TopManager.getInstance(this);
		JumpManager.getInstance(this);
		TopManager.getInstance().warm();

		Tasks.globalLater(areaStorage::reload, 20);

		infiniteJump = Bukkit.getPluginManager().getPlugin("InfiniteJump") != null;
		if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
			placeholders = new Placeholders(this);
			placeholders.register();
			papi = true;
		}

		getServer().getPluginManager().registerEvents(man, this);
		getServer().getPluginManager().registerEvents(selector, this);
		getServer().getPluginManager().registerEvents(rewards, this);
		getServer().getPluginManager().registerEvents(commandHandler, this);

		getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS,
				event -> ParkourCommand.register(this, event.registrar()));

		new Metrics(this, 4788);
		Updater.getInstance(this);

		getLogger().info("ajParkour v" + getPluginMeta().getVersion() + " by " + String.join(", ", getPluginMeta().getAuthors()) + " has been enabled!");
	}

	public Settings settings() {
		return settings;
	}

	public ExecutorService io() {
		return io;
	}

	public boolean hasInfiniteJump() {
		return infiniteJump;
	}

	public File getJarFile() {
		return getFile();
	}

	private static final List<String> RELOADABLE = List.of("config", "areas", "messages", "blocks", "rewards", "jumps");

	public List<String> getReloadable() {
		return RELOADABLE;
	}

	public void reload(String key, @Nullable CommandSender sender) {
		if(sender == null) sender = Bukkit.getConsoleSender();
		switch(key) {
			case "config" -> {
				reloadConfig();
				settings = Settings.load(getConfig(), getLogger());
			}
			case "areas" -> areaStorage.reload();
			case "messages" -> msgs.reload();
			case "blocks" -> selector.reloadTypes();
			case "rewards" -> rewards.reload();
			case "jumps" -> JumpManager.getInstance().reload();
			default -> {
				msgs.send(sender, "reload.unknown", Messages.tag("file", key));
				return;
			}
		}
		if("areas".equals(key)) TopManager.getInstance().warm();
		msgs.send(sender, "reload.success", Messages.tag("file", key));
	}

	@Override
	public void onDisable() {
		if(man != null) man.disable();
		if(scores != null) scores.disable();
		io.shutdown();
		try {
			if(!io.awaitTermination(10, TimeUnit.SECONDS)) {
				io.shutdownNow();
			}
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
			io.shutdownNow();
		}
		getLogger().info("ajParkour v" + getPluginMeta().getVersion() + " by " + String.join(", ", getPluginMeta().getAuthors()) + " has been disabled!");
	}

	public static int random(int min, int max) {
		if(min > max) {
			throw new IllegalArgumentException("max must be greater than min: " + min + "-" + max);
		}
		if(min == max) return min;
		return ThreadLocalRandom.current().nextInt(min, max + 1);
	}
}
