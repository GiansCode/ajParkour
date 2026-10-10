package us.ajg0702.parkour.utils;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;

import java.io.File;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

public class Updater implements Listener {

	private static final String VERSION_URL = "https://ajg0702.us/pl/ap/updates/getversion.php";
	private static final String DOWNLOAD_URL = "https://ajg0702.us/pl/ap/updates/downloads/ajParkour-%s.jar";

	private static Updater instance = null;

	private final Main pl;
	private final boolean enabled;
	private final String currentVersion;
	private final HttpClient http;

	private volatile boolean ready = false;
	private volatile boolean updateAvailable = false;
	private volatile boolean alreadyDownloaded = false;
	private volatile String latestVersion = "";

	private Updater(Main pl) {
		instance = this;
		this.pl = pl;
		this.enabled = pl.settings().enableUpdater();
		this.currentVersion = pl.getPluginMeta().getVersion().split("-")[0];
		this.http = HttpClient.newBuilder()
				.followRedirects(HttpClient.Redirect.NORMAL)
				.connectTimeout(Duration.ofSeconds(10))
				.build();
		if(!enabled) return;
		pl.getServer().getPluginManager().registerEvents(this, pl);
		Tasks.asyncTimer(this::check, 0, 1, TimeUnit.HOURS);
	}

	public static Updater getInstance() {
		return instance;
	}

	public static Updater getInstance(Main plugin) {
		if(instance == null) {
			new Updater(plugin);
		}
		return instance;
	}

	private HttpRequest.Builder request(String url) {
		return HttpRequest.newBuilder(URI.create(url))
				.timeout(Duration.ofSeconds(30))
				.header("User-Agent", "ajParkour/" + currentVersion);
	}

	public void check() {
		if(!enabled || alreadyDownloaded) return;
		http.sendAsync(request(VERSION_URL).GET().build(), HttpResponse.BodyHandlers.ofString())
				.thenAccept(res -> {
					String latest = res.body().strip();
					if(res.statusCode() != 200 || latest.isEmpty()) return;
					latestVersion = latest;
					updateAvailable = isNewer(latest, currentVersion);
					if(!ready) {
						if(updateAvailable) {
							pl.getLogger().info("An update is available! (" + latest + ") Do /ajParkour update to download it!");
						} else {
							pl.getLogger().info("You are up to date! (" + latest + ")");
						}
					}
					ready = true;
				})
				.exceptionally(e -> {
					pl.getLogger().warning("Unable to check for an update! " + e.getMessage());
					return null;
				});
	}

	static boolean isNewer(String latest, String current) {
		String[] a = latest.split("\\.");
		String[] b = current.split("\\.");
		for(int i = 0; i < Math.max(a.length, b.length); i++) {
			int x = i < a.length ? parse(a[i]) : 0;
			int y = i < b.length ? parse(b[i]) : 0;
			if(x != y) return x > y;
		}
		return false;
	}

	private static int parse(String s) {
		try {
			return Integer.parseInt(s.replaceAll("\\D.*", ""));
		} catch(NumberFormatException e) {
			return 0;
		}
	}

	@EventHandler
	public void onJoin(PlayerJoinEvent e) {
		if(!enabled || alreadyDownloaded || !ready || !updateAvailable || !pl.settings().notifyUpdate()) return;
		if(!e.getPlayer().hasPermission("ajparkour.update")) return;
		Messages msgs = Messages.getInstance();
		Tasks.entityLater(e.getPlayer(), () -> e.getPlayer().sendMessage(Messages.join(
				msgs.get("updater.bar"),
				msgs.get("updater.available"),
				msgs.get("updater.bar")
		)), 20);
	}

	/**
	 * Downloads the latest version into the server's update folder. It replaces the current jar on the next restart.
	 */
	public void downloadUpdate(CommandSender p) {
		Messages msgs = Messages.getInstance();
		if(!enabled) {
			msgs.send(p, "updater.disabled");
			return;
		}
		if(!updateAvailable || alreadyDownloaded) {
			msgs.send(p, "updater.none");
			return;
		}
		File updateFolder = Bukkit.getUpdateFolderFile();
		Path target = updateFolder.toPath().resolve(pl.getJarFile().getName());
		Path tmp = updateFolder.toPath().resolve(pl.getJarFile().getName() + ".part");
		msgs.send(p, "updater.downloading", Messages.tag("version", latestVersion));
		try {
			Files.createDirectories(updateFolder.toPath());
		} catch(Exception e) {
			msgs.send(p, "updater.folder-fail");
			pl.getLogger().warning("Unable to create update folder: " + e.getMessage());
			return;
		}
		http.sendAsync(request(DOWNLOAD_URL.formatted(latestVersion)).GET().build(), HttpResponse.BodyHandlers.ofFile(tmp))
				.thenAccept(res -> {
					try {
						if(res.statusCode() != 200) {
							Files.deleteIfExists(tmp);
							throw new IllegalStateException("HTTP " + res.statusCode());
						}
						Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
					} catch(Exception e) {
						throw new RuntimeException(e);
					}
					updateAvailable = false;
					alreadyDownloaded = true;
					msgs.send(p, "updater.success");
				})
				.exceptionally(e -> {
					msgs.send(p, "updater.fail");
					pl.getLogger().warning("Unable to download update: " + e.getMessage());
					return null;
				});
	}
}
