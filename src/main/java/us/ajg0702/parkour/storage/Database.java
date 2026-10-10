package us.ajg0702.parkour.storage;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.io.File;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Connection pool plus two executors:
 * an ordered single-thread queue for writes (and per-player loads, so they observe earlier writes),
 * and a read executor for leaderboard queries.
 */
public final class Database {

	public enum Type { SQLITE, MYSQL }

	@FunctionalInterface
	public interface SqlFunction<T> {
		T apply(Connection c) throws SQLException;
	}

	@FunctionalInterface
	public interface SqlConsumer {
		void accept(Connection c) throws SQLException;
	}

	private static final int SCHEMA_VERSION = 2;

	private final Logger logger;
	private final Type type;
	private final String prefix;
	private final HikariDataSource ds;
	private final ExecutorService writes;
	private final ExecutorService reads;

	private Database(Logger logger, Type type, String prefix, HikariDataSource ds) {
		this.logger = logger;
		this.type = type;
		this.prefix = prefix;
		this.ds = ds;
		this.writes = Executors.newSingleThreadExecutor(Thread.ofPlatform().name("ajParkour-db-write").daemon().factory());
		this.reads = type == Type.SQLITE
				? writes
				: Executors.newThreadPerTaskExecutor(Thread.ofVirtual().name("ajParkour-db-read-", 0).factory());
	}

	public static Database sqlite(Logger logger, File file, String prefix) {
		HikariConfig hc = new HikariConfig();
		hc.setPoolName("ajParkour-SQLite");
		hc.setDriverClassName("org.sqlite.JDBC");
		hc.setJdbcUrl("jdbc:sqlite:" + file.getAbsolutePath()
				+ "?journal_mode=WAL&synchronous=NORMAL&busy_timeout=5000&temp_store=MEMORY&cache_size=-8000&foreign_keys=ON");
		hc.setMaximumPoolSize(1);
		hc.setMinimumIdle(1);
		return new Database(logger, Type.SQLITE, prefix, new HikariDataSource(hc));
	}

	public static Database mysql(Logger logger, String host, String database, String username, String password,
	                             String prefix, boolean useSSL, boolean allowPublicKeyRetrieval,
	                             int minConnections, int maxConnections, String txIsolation) {
		HikariConfig hc = new HikariConfig();
		hc.setPoolName("ajParkour-MySQL");
		for(String driver : new String[]{"com.mysql.cj.jdbc.Driver", "com.mysql.jdbc.Driver"}) {
			try {
				Class.forName(driver);
				hc.setDriverClassName(driver);
				break;
			} catch(ClassNotFoundException ignored) {}
		}
		hc.setJdbcUrl("jdbc:mysql://" + host + "/" + database
				+ "?useSSL=" + useSSL
				+ "&allowPublicKeyRetrieval=" + allowPublicKeyRetrieval
				+ "&characterEncoding=utf8");
		hc.setUsername(username);
		hc.setPassword(password);
		hc.setMaximumPoolSize(Math.max(1, maxConnections));
		hc.setMinimumIdle(Math.max(0, Math.min(minConnections, maxConnections)));
		hc.setConnectionTimeout(10_000);
		hc.setLeakDetectionThreshold(60_000);
		hc.addDataSourceProperty("cachePrepStmts", "true");
		hc.addDataSourceProperty("prepStmtCacheSize", "250");
		hc.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");
		hc.addDataSourceProperty("useServerPrepStmts", "true");
		hc.addDataSourceProperty("rewriteBatchedStatements", "true");
		hc.addDataSourceProperty("useLocalSessionState", "true");
		hc.addDataSourceProperty("elideSetAutoCommits", "true");
		hc.addDataSourceProperty("maintainTimeStats", "false");
		if(txIsolation != null && !txIsolation.isEmpty()) {
			hc.setTransactionIsolation(txIsolation);
		}
		return new Database(logger, Type.MYSQL, prefix, new HikariDataSource(hc));
	}

	public Type type() {
		return type;
	}

	public String table(String name) {
		return prefix + name;
	}

	public Connection connection() throws SQLException {
		return ds.getConnection();
	}

	/** Runs synchronously on the calling thread. Never call this from a tick thread. */
	public <T> T call(SqlFunction<T> fn) {
		try(Connection c = ds.getConnection()) {
			return fn.apply(c);
		} catch(SQLException e) {
			throw new CompletionException(e);
		}
	}

	public <T> CompletableFuture<T> read(SqlFunction<T> fn) {
		return CompletableFuture.supplyAsync(() -> call(fn), reads);
	}

	/** Queued on the ordered write thread. */
	public <T> CompletableFuture<T> ordered(SqlFunction<T> fn) {
		return CompletableFuture.supplyAsync(() -> call(fn), writes);
	}

	public CompletableFuture<Void> write(String what, SqlConsumer fn) {
		return CompletableFuture.runAsync(() -> call(c -> {
			fn.accept(c);
			return null;
		}), writes).exceptionally(t -> {
			logger.log(Level.SEVERE, "Database error while trying to " + what + ":", unwrap(t));
			return null;
		});
	}

	public static Throwable unwrap(Throwable t) {
		return t instanceof CompletionException && t.getCause() != null ? t.getCause() : t;
	}

	public void close() {
		writes.shutdown();
		reads.shutdown();
		try {
			if(!writes.awaitTermination(15, TimeUnit.SECONDS)) {
				logger.warning("Timed out waiting for pending database writes to finish.");
			}
			reads.awaitTermination(5, TimeUnit.SECONDS);
		} catch(InterruptedException e) {
			Thread.currentThread().interrupt();
		}
		ds.close();
	}

	// ---------------------------------------------------------------- schema

	public void createTables() throws SQLException {
		String id = type == Type.SQLITE ? "INTEGER PRIMARY KEY AUTOINCREMENT" : "INT PRIMARY KEY AUTO_INCREMENT";
		try(Connection c = connection(); Statement st = c.createStatement()) {
			st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("players")
					+ " (id VARCHAR(36) PRIMARY KEY, material VARCHAR(255), name VARCHAR(17), gamesplayed INT DEFAULT 0)");
			st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("scores")
					+ " (id " + id + ", area VARCHAR(100), player VARCHAR(36), score INT, time INT)");
			st.executeUpdate("CREATE TABLE IF NOT EXISTS " + table("meta")
					+ " (k VARCHAR(64) PRIMARY KEY, v VARCHAR(255))");
		}
	}

	public void migrate() throws SQLException {
		try(Connection c = connection()) {
			int version = schemaVersion(c);
			if(version >= SCHEMA_VERSION) return;

			logger.info("Upgrading database schema (adding indexes). This only happens once.");
			String scores = table("scores");
			try(Statement st = c.createStatement()) {
				if(type == Type.MYSQL) {
					st.executeUpdate("ALTER TABLE " + scores + " MODIFY area VARCHAR(100)");
					st.executeUpdate("ALTER TABLE " + table("players") + " MODIFY material VARCHAR(255)");
					st.executeUpdate("DELETE s1 FROM " + scores + " s1 JOIN " + scores + " s2"
							+ " ON s1.player = s2.player AND s1.area = s2.area"
							+ " AND (s2.score > s1.score OR (s2.score = s1.score AND s2.id < s1.id))");
				} else {
					st.executeUpdate("DELETE FROM " + scores + " WHERE EXISTS (SELECT 1 FROM " + scores + " s2"
							+ " WHERE s2.player = " + scores + ".player AND s2.area = " + scores + ".area"
							+ " AND (s2.score > " + scores + ".score OR (s2.score = " + scores + ".score AND s2.id < " + scores + ".id)))");
				}
				createIndex(st, "CREATE UNIQUE INDEX " + table("scores_player_area") + " ON " + scores + " (player, area)");
				createIndex(st, "CREATE INDEX " + table("scores_area_score") + " ON " + scores + " (area, score)");
			}
			setSchemaVersion(c, SCHEMA_VERSION);
		}
	}

	private void createIndex(Statement st, String sql) throws SQLException {
		try {
			st.executeUpdate(type == Type.SQLITE ? sql.replaceFirst("INDEX ", "INDEX IF NOT EXISTS ") : sql);
		} catch(SQLException e) {
			// MySQL 1061: duplicate key name (index already exists from a partial upgrade)
			if(e.getErrorCode() != 1061) throw e;
		}
	}

	private int schemaVersion(Connection c) throws SQLException {
		try(PreparedStatement ps = c.prepareStatement("SELECT v FROM " + table("meta") + " WHERE k='schema'");
		    ResultSet rs = ps.executeQuery()) {
			return rs.next() ? Integer.parseInt(rs.getString(1)) : 1;
		}
	}

	private void setSchemaVersion(Connection c, int version) throws SQLException {
		String sql = type == Type.SQLITE
				? "INSERT INTO " + table("meta") + " (k, v) VALUES ('schema', ?) ON CONFLICT(k) DO UPDATE SET v = excluded.v"
				: "INSERT INTO " + table("meta") + " (k, v) VALUES ('schema', ?) ON DUPLICATE KEY UPDATE v = VALUES(v)";
		try(PreparedStatement ps = c.prepareStatement(sql)) {
			ps.setString(1, Integer.toString(version));
			ps.executeUpdate();
		}
	}

	// ---------------------------------------------------------------- statements

	/** Stores the score only if it beats the existing one. */
	public String upsertBestScoreSql() {
		String t = table("scores");
		return type == Type.SQLITE
				? "INSERT INTO " + t + " (area, player, score, time) VALUES (?, ?, ?, ?)"
				+ " ON CONFLICT(player, area) DO UPDATE SET score = excluded.score, time = excluded.time"
				+ " WHERE excluded.score > " + t + ".score"
				: "INSERT INTO " + t + " (area, player, score, time) VALUES (?, ?, ?, ?)"
				+ " ON DUPLICATE KEY UPDATE time = IF(VALUES(score) > score, VALUES(time), time),"
				+ " score = GREATEST(score, VALUES(score))";
	}

	/** Stores the score unconditionally. */
	public String upsertScoreSql() {
		String t = table("scores");
		return type == Type.SQLITE
				? "INSERT INTO " + t + " (area, player, score, time) VALUES (?, ?, ?, ?)"
				+ " ON CONFLICT(player, area) DO UPDATE SET score = excluded.score, time = excluded.time"
				: "INSERT INTO " + t + " (area, player, score, time) VALUES (?, ?, ?, ?)"
				+ " ON DUPLICATE KEY UPDATE score = VALUES(score), time = VALUES(time)";
	}

	public String incrementGamesPlayedSql() {
		String t = table("players");
		return type == Type.SQLITE
				? "INSERT INTO " + t + " (id, material, name, gamesplayed) VALUES (?, NULL, NULL, 1)"
				+ " ON CONFLICT(id) DO UPDATE SET gamesplayed = COALESCE(" + t + ".gamesplayed, 0) + 1"
				: "INSERT INTO " + t + " (id, material, name, gamesplayed) VALUES (?, NULL, NULL, 1)"
				+ " ON DUPLICATE KEY UPDATE gamesplayed = COALESCE(gamesplayed, 0) + 1";
	}

	public String upsertPlayerNameSql() {
		String t = table("players");
		return type == Type.SQLITE
				? "INSERT INTO " + t + " (id, material, name, gamesplayed) VALUES (?, NULL, ?, 0)"
				+ " ON CONFLICT(id) DO UPDATE SET name = excluded.name"
				: "INSERT INTO " + t + " (id, material, name, gamesplayed) VALUES (?, NULL, ?, 0)"
				+ " ON DUPLICATE KEY UPDATE name = VALUES(name)";
	}

	public static void bindScore(PreparedStatement ps, UUID player, String area, int score, int time) throws SQLException {
		ps.setString(1, area);
		ps.setString(2, player.toString());
		ps.setInt(3, score);
		ps.setInt(4, time);
	}
}
