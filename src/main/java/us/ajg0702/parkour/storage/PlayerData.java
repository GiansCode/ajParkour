package us.ajg0702.parkour.storage;

import org.jspecify.annotations.Nullable;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory copy of a player's stored data. Thread-safe; written through to the database asynchronously.
 */
public final class PlayerData {
	public static final String OVERALL = "overall";

	public record Score(int score, int time) {}

	private final UUID uuid;
	private volatile @Nullable String name;
	private volatile @Nullable String material;
	private final AtomicInteger gamesPlayed;
	private final Map<String, Score> scores = new ConcurrentHashMap<>();
	private final long loadedAt = System.currentTimeMillis();

	public PlayerData(UUID uuid, @Nullable String name, @Nullable String material, int gamesPlayed) {
		this.uuid = uuid;
		this.name = name;
		this.material = material;
		this.gamesPlayed = new AtomicInteger(gamesPlayed);
	}

	public long loadedAt() {
		return loadedAt;
	}

	public UUID uuid() {
		return uuid;
	}

	public @Nullable String name() {
		return name;
	}

	public void name(@Nullable String name) {
		this.name = name;
	}

	/** The raw block selection, or null/"random" for random blocks. */
	public @Nullable String material() {
		return material;
	}

	public void material(@Nullable String material) {
		this.material = material;
	}

	public int gamesPlayed() {
		return gamesPlayed.get();
	}

	public void incrementGamesPlayed() {
		gamesPlayed.incrementAndGet();
	}

	public int highScore(@Nullable String area) {
		Score s = scores.get(area == null ? OVERALL : area);
		return s == null ? 0 : s.score();
	}

	public int time(@Nullable String area) {
		Score s = scores.get(area == null ? OVERALL : area);
		return s == null ? 0 : s.time();
	}

	public Map<String, Score> scores() {
		return Map.copyOf(scores);
	}

	public void putScore(String area, int score, int time) {
		scores.put(area, new Score(score, time));
	}

	public void offerScore(String area, int score, int time) {
		scores.merge(area, new Score(score, time), (old, offered) -> offered.score() > old.score() ? offered : old);
	}

	public void removeScore(String area) {
		scores.remove(area);
	}

	public void clearScores() {
		scores.clear();
	}
}
