package us.ajg0702.parkour;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.storage.Database;
import us.ajg0702.parkour.storage.PlayerData;
import us.ajg0702.parkour.top.TopEntry;
import us.ajg0702.parkour.top.TopManager;

import java.io.File;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;

import static us.ajg0702.parkour.storage.PlayerData.OVERALL;

/**
 * Score and player storage.
 * <p>
 * Online players are cached in memory (loaded off-thread during login), so gameplay never waits on the database.
 * The blocking getters remain for API compatibility; they answer from the cache when possible.
 */
public class Scores {

	private final Main plugin;
	private final File storageConfigFile;
	private final YamlConfiguration storageConfig;

	private @Nullable Database db;

	private final Map<UUID, PlayerData> cache = new ConcurrentHashMap<>();

	public Scores(Main pl) {
		plugin = pl;
		storageConfigFile = new File(pl.getDataFolder(), "storage.yml");
		storageConfig = YamlConfiguration.loadConfiguration(storageConfigFile);
		checkStorageConfig();

		String method = storageConfig.getString("method", "sqlite");
		String prefix = storageConfig.getString("mysql.tablePrefix", "ajparkour_");

		if(method.equalsIgnoreCase("mysql")) {
			try {
				db = Database.mysql(plugin.getLogger(),
						storageConfig.getString("mysql.ip"),
						storageConfig.getString("mysql.database"),
						storageConfig.getString("mysql.username"),
						storageConfig.getString("mysql.password"),
						prefix,
						storageConfig.getBoolean("mysql.useSSL"),
						storageConfig.getBoolean("mysql.allowPublicKeyRetrieval"),
						storageConfig.getInt("mysql.minConnections"),
						storageConfig.getInt("mysql.maxConnections"),
						storageConfig.getString("mysql.tx_isolation", ""));
				initDatabase(false);
			} catch(Exception e) {
				plugin.getLogger().log(Level.WARNING, "Could not connect to database! Switching to sqlite storage. Error: ", e);
				if(db != null) db.close();
				db = null;
				method = "sqlite";
			}
		}
		if(method.equalsIgnoreCase("sqlite") || method.equalsIgnoreCase("yaml")) {
			try {
				db = Database.sqlite(plugin.getLogger(), new File(plugin.getDataFolder(), "scores.db"), prefix);
				initDatabase(method.equalsIgnoreCase("yaml"));
			} catch(Exception e) {
				plugin.getLogger().log(Level.SEVERE, "Unable to create sqlite database. High scores will not work!", e);
				if(db != null) db.close();
				db = null;
			}
		}
		if(db == null) {
			plugin.getLogger().severe("Unable to find database method! Check storage.yml as you most likely put in an invalid storage method.");
		}
	}

	private void checkStorageConfig() {
		Map<String, Object> v = new LinkedHashMap<>();
		v.put("method", "sqlite");
		v.put("mysql.ip", "127.0.0.1:3306");
		v.put("mysql.username", "");
		v.put("mysql.password", "");
		v.put("mysql.database", "");
		v.put("mysql.tablePrefix", "ajparkour_");
		v.put("mysql.allowPublicKeyRetrieval", false);
		v.put("mysql.useSSL", false);
		v.put("mysql.minConnections", 2);
		v.put("mysql.maxConnections", 10);
		v.put("mysql.tx_isolation", "");

		boolean save = false;
		for(Map.Entry<String, Object> e : v.entrySet()) {
			if(!storageConfig.isSet(e.getKey())) {
				storageConfig.set(e.getKey(), e.getValue());
				save = true;
			}
		}
		if(save) {
			storageConfig.options().setHeader(List.of(
					"", "",
					"This file tells the plugin where it",
					"should store player high scores.", "",
					"The method option can either be 'sqlite' or 'mysql'.",
					"If it is mysql, you must configure the mysql section below.", ""
			));
			saveStorageConfig();
		}
	}

	private void saveStorageConfig() {
		try {
			storageConfig.save(storageConfigFile);
		} catch(IOException e) {
			plugin.getLogger().log(Level.SEVERE, "Unable to save storage.yml", e);
		}
	}

	private void initDatabase(boolean convertYaml) throws SQLException {
		Database db = this.db;
		db.createTables();
		String oldTableName = storageConfig.getString("mysql.table");
		if(oldTableName != null && db.type() == Database.Type.MYSQL) {
			convertFromOldSQL(oldTableName);
		}
		if(convertYaml) {
			convertFromYaml();
		}
		db.migrate();
	}

	public void disable() {
		if(db != null) {
			db.close();
		}
	}

	public @Nullable Database getDatabase() {
		return db;
	}

	// ------------------------------------------------------------------ player cache

	public @Nullable PlayerData getCached(UUID uuid) {
		return cache.get(uuid);
	}

	public Collection<PlayerData> getCachedPlayers() {
		return List.copyOf(cache.values());
	}

	/**
	 * Loads every player and score into memory. After this, reads never touch the database.
	 */
	public void preloadAll() {
		if(db == null) return;
		try {
			int n = db.call(c -> {
				Map<UUID, PlayerData> loaded = new HashMap<>();
				try(var st = c.createStatement();
				    ResultSet rs = st.executeQuery("SELECT id, material, name, gamesplayed FROM " + db.table("players"))) {
					while(rs.next()) {
						UUID id = UUID.fromString(rs.getString(1));
						loaded.put(id, new PlayerData(id, rs.getString("name"), rs.getString("material"), rs.getInt("gamesplayed")));
					}
				}
				try(var st = c.createStatement();
				    ResultSet rs = st.executeQuery("SELECT player, area, score, time FROM " + db.table("scores"))) {
					while(rs.next()) {
						UUID id = UUID.fromString(rs.getString(1));
						PlayerData data = loaded.computeIfAbsent(id, u -> new PlayerData(u, null, null, 0));
						data.offerScore(rs.getString(2), rs.getInt(3), rs.getInt(4));
					}
				}
				cache.putAll(loaded);
				return loaded.size();
			});
			plugin.getLogger().info("Loaded " + n + " players from storage.");
		} catch(Exception e) {
			plugin.getLogger().log(Level.SEVERE, "Unable to preload scores", Database.unwrap(e));
		}
	}

	/**
	 * Returns cached data, creating an empty record for players we have never seen.
	 * @param name the player's current name, stored if not null
	 */
	public CompletableFuture<PlayerData> load(UUID uuid, @Nullable String name) {
		PlayerData cached = cache.get(uuid);
		if(cached != null) {
			if(name != null && !name.equals(cached.name())) {
				cached.name(name);
				writeName(uuid, name);
			}
			return CompletableFuture.completedFuture(cached);
		}
		PlayerData created = new PlayerData(uuid, name, null, 0);
		PlayerData existing = cache.putIfAbsent(uuid, created);
		if(existing != null) {
			if(name != null && !name.equals(existing.name())) {
				existing.name(name);
				writeName(uuid, name);
			}
			return CompletableFuture.completedFuture(existing);
		}
		if(name != null) writeName(uuid, name);
		return CompletableFuture.completedFuture(created);
	}

	private void writeName(UUID uuid, String name) {
		if(db == null) return;
		db.write("update a player name", c -> {
			try(PreparedStatement ps = c.prepareStatement(db.upsertPlayerNameSql())) {
				ps.setString(1, uuid.toString());
				ps.setString(2, name);
				ps.executeUpdate();
			}
		});
	}

	/** Blocking load, for use on login threads. */
	public PlayerData loadBlocking(UUID uuid, @Nullable String name) {
		return load(uuid, name).join();
	}

	public void unload(UUID uuid) {
		// Data stays in memory so later reads never hit the database.
	}

	// ------------------------------------------------------------------ game-facing writes

	/**
	 * Records a finished run. Only stored where it beats the existing score (for the area and overall).
	 */
	public void submitBest(UUID uuid, String area, int score, int time) {
		PlayerData data = cache.get(uuid);
		if(data != null) {
			if(score <= data.highScore(area) && score <= data.highScore(OVERALL)) return;
			data.offerScore(area, score, time);
			data.offerScore(OVERALL, score, time);
		}
		if(db == null) return;
		db.write("save a score", c -> {
			try(PreparedStatement ps = c.prepareStatement(db.upsertBestScoreSql())) {
				Database.bindScore(ps, uuid, area, score, time);
				ps.addBatch();
				if(!area.equals(OVERALL)) {
					Database.bindScore(ps, uuid, OVERALL, score, time);
					ps.addBatch();
				}
				ps.executeBatch();
			}
		}).thenRun(() -> {
			TopManager.getInstance().invalidate(area);
			TopManager.getInstance().invalidate(OVERALL);
		});
	}

	public void incrementGamesPlayed(UUID uuid) {
		PlayerData data = cache.get(uuid);
		if(data != null) data.incrementGamesPlayed();
		if(db == null) return;
		db.write("update games played", c -> {
			try(PreparedStatement ps = c.prepareStatement(db.incrementGamesPlayedSql())) {
				ps.setString(1, uuid.toString());
				ps.executeUpdate();
			}
		});
	}

	/** Deletes every score (overall and per-area) for a player. */
	public void resetPlayer(UUID uuid) {
		PlayerData data = cache.get(uuid);
		if(data != null) data.clearScores();
		if(db == null) return;
		db.write("reset a player", c -> {
			try(PreparedStatement ps = c.prepareStatement("DELETE FROM " + db.table("scores") + " WHERE player=?")) {
				ps.setString(1, uuid.toString());
				ps.executeUpdate();
			}
		}).thenRun(() -> TopManager.getInstance().invalidateAll());
	}

	// ------------------------------------------------------------------ leaderboards

	public CompletableFuture<List<TopEntry>> fetchTop(String area, int limit) {
		return CompletableFuture.completedFuture(topFromMemory(area, limit));
	}

	public List<TopEntry> topFromMemory(String area, int limit) {
		String a = area == null ? OVERALL : area;
		int n = Math.max(1, limit);
		List<PlayerData> ranked = new ArrayList<>();
		for(PlayerData d : cache.values()) {
			if(d.highScore(a) > 0) ranked.add(d);
		}
		ranked.sort(Comparator.comparingInt((PlayerData d) -> d.highScore(a)).reversed());
		if(ranked.size() > n) ranked = ranked.subList(0, n);
		List<TopEntry> list = new ArrayList<>(ranked.size());
		for(PlayerData d : ranked) {
			String name = d.name();
			list.add(new TopEntry(list.size() + 1, name == null ? "Unknown" : name, d.highScore(a), d.time(a)));
		}
		return List.copyOf(list);
	}

	/**
	 * @deprecated blocking; use {@link TopManager#getTop(int, String)} or {@link TopManager#getTopAsync(String, int)}.
	 */
	@Deprecated
	public TopEntry getTopPosition(int position, String area) {
		try {
			List<TopEntry> list = fetchTop(area == null ? OVERALL : area, position).join();
			return list.size() >= position ? list.get(position - 1) : TopEntry.empty(position);
		} catch(Exception e) {
			plugin.getLogger().log(Level.WARNING, "An error occurred while trying to get a top score:", e);
			return new TopEntry(position, "Error", -1, -1);
		}
	}

	// ------------------------------------------------------------------ API (kept for compatibility)

	public int getHighScore(UUID uuid, String area) {
		PlayerData data = cache.get(uuid);
		return data == null ? 0 : data.highScore(area);
	}

	public int getTime(UUID uuid, String area) {
		PlayerData data = cache.get(uuid);
		return data == null ? 0 : data.time(area);
	}

	public int getTime(UUID uuid) {
		return getTime(uuid, null);
	}

	private int queryScoreColumn(UUID uuid, @Nullable String area, String column) {
		return "time".equals(column) ? getTime(uuid, area) : getHighScore(uuid, area);
	}

	/**
	 * Sets a score exactly. A score and time of 0 deletes the entry.
	 * If the score is for an area and beats the overall score, the overall score is updated too.
	 */
	public void setScore(UUID uuid, int score, int time, final String area) {
		String ar = area == null || area.equals("null") ? OVERALL : area;
		boolean delete = score == 0 && time == 0;
		PlayerData data = cache.get(uuid);
		if(data != null) {
			if(delete) data.removeScore(ar);
			else data.putScore(ar, score, time);
			if(!ar.equals(OVERALL) && !delete) data.offerScore(OVERALL, score, time);
		}
		if(db == null) return;
		db.write("set a score", c -> {
			if(delete) {
				try(PreparedStatement ps = c.prepareStatement("DELETE FROM " + db.table("scores") + " WHERE player=? AND area=?")) {
					ps.setString(1, uuid.toString());
					ps.setString(2, ar);
					ps.executeUpdate();
				}
				return;
			}
			try(PreparedStatement ps = c.prepareStatement(db.upsertScoreSql())) {
				Database.bindScore(ps, uuid, ar, score, time);
				ps.executeUpdate();
			}
			if(!ar.equals(OVERALL)) {
				try(PreparedStatement ps = c.prepareStatement(db.upsertBestScoreSql())) {
					Database.bindScore(ps, uuid, OVERALL, score, time);
					ps.executeUpdate();
				}
			}
		}).thenRun(() -> {
			TopManager.getInstance().invalidate(ar);
			TopManager.getInstance().invalidate(OVERALL);
		});
	}

	public void setMaterial(UUID uuid, String mat) {
		PlayerData data = cache.get(uuid);
		if(data != null) data.material(mat);
		if(db == null) return;
		db.write("set a block material", c -> {
			try(PreparedStatement ps = c.prepareStatement("UPDATE " + db.table("players") + " SET material=? WHERE id=?")) {
				ps.setString(1, mat);
				ps.setString(2, uuid.toString());
				ps.executeUpdate();
			}
		});
	}

	public String getMaterial(UUID uuid) {
		PlayerData data = cache.get(uuid);
		if(data == null) return "RANDOM";
		String m = data.material();
		return m == null ? "RANDOM" : m;
	}

	public String getName(UUID uuid) {
		PlayerData data = cache.get(uuid);
		return data == null ? null : data.name();
	}

	public int getGamesPlayed(UUID uuid) {
		PlayerData data = cache.get(uuid);
		return data == null ? 0 : data.gamesPlayed();
	}

	public void addToGamesPlayed(UUID uuid) {
		incrementGamesPlayed(uuid);
	}

	public void updateName(Player player) {
		load(player.getUniqueId(), player.getName());
	}

	// ------------------------------------------------------------------ legacy conversions

	private void convertFromOldSQL(String oldTable) throws SQLException {
		plugin.getLogger().info("Starting database conversion (getting rid of the json in MySQL :puke:)");
		try(Connection conn = db.connection(); Statement st = conn.createStatement()) {
			try(ResultSet teCheck = st.executeQuery("show tables like '" + oldTable + "_old'")) {
				if(teCheck.next()) {
					plugin.getLogger().info("Conversion seems to already be done. Marking it as done.");
					storageConfig.set("mysql.table", null);
					saveStorageConfig();
					return;
				}
			}
			st.executeUpdate("alter table " + oldTable + " rename " + oldTable + "_old");
			try(ResultSet oldData = st.executeQuery("select * from " + oldTable + "_old")) {
				while(oldData.next()) {
					UUID uuid = UUID.fromString(oldData.getString("id"));
					plugin.getLogger().info("Converting " + uuid);
					insertJsonData(conn, uuid, oldData.getString("name"), oldData.getString("score"),
							oldData.getInt("time"), oldData.getString("material"), oldData.getInt("gamesplayed"));
				}
			}
		}
		storageConfig.set("mysql.table", null);
		saveStorageConfig();
	}

	private void convertFromYaml() throws SQLException {
		File ymlFile = new File(plugin.getDataFolder(), "scores.yml");
		YamlConfiguration yml = YamlConfiguration.loadConfiguration(ymlFile);
		plugin.getLogger().info("Starting yaml conversion (moving to sqlite)");
		try(Connection conn = db.connection()) {
			for(String rawUUID : yml.getKeys(false)) {
				ConfigurationSection oldData = yml.getConfigurationSection(rawUUID);
				if(oldData == null) continue;
				UUID uuid = UUID.fromString(rawUUID);
				plugin.getLogger().info("Converting " + uuid);
				insertJsonData(conn, uuid, null, oldData.getString("score"), oldData.getInt("time", 0),
						oldData.getString("material"), oldData.getInt("gamesplayed", 0));
			}
		}
		storageConfig.set("method", "sqlite");
		saveStorageConfig();
	}

	private void insertJsonData(Connection conn, UUID uuid, @Nullable String name, @Nullable String raw, int time,
	                            @Nullable String mat, int gamesPlayed) throws SQLException {
		if(raw == null) raw = "{}";
		if(raw.matches("-?\\d+")) raw = "{\"null\":" + raw + "}";

		JsonObject scores;
		try {
			scores = JsonParser.parseString(raw).getAsJsonObject();
		} catch(Exception e) {
			plugin.getLogger().log(Level.SEVERE, "An error occurred when attempting convert a player's score:", e);
			scores = new JsonObject();
		}

		int largest = 0;
		int largestTime = 0;
		boolean insertedOverall = false;
		try(PreparedStatement ps = conn.prepareStatement(
				"insert into " + db.table("scores") + " (area, player, score, time) values (?, ?, ?, ?)")) {
			for(Map.Entry<String, JsonElement> e : scores.entrySet()) {
				String area = e.getKey().equals("null") ? OVERALL : e.getKey();
				int score = Math.round(e.getValue().getAsLong());
				int t = 0;
				if(area.equals(OVERALL)) {
					t = time;
					insertedOverall = true;
				} else if(score > largest) {
					largest = score;
					largestTime = time;
				}
				Database.bindScore(ps, uuid, area, score, t);
				ps.executeUpdate();
			}
			if(!insertedOverall) {
				Database.bindScore(ps, uuid, OVERALL, largest, largestTime);
				ps.executeUpdate();
			}
		}

		try(PreparedStatement ps = conn.prepareStatement(
				"insert into " + db.table("players") + " (id, material, name, gamesplayed) values (?, ?, ?, ?)")) {
			ps.setString(1, uuid.toString());
			ps.setString(2, mat);
			ps.setString(3, name);
			ps.setInt(4, gamesPlayed);
			ps.executeUpdate();
		}
	}
}
