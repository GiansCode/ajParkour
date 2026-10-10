package us.ajg0702.parkour.game;

import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.utils.ParticleRect;

import java.util.concurrent.ThreadLocalRandom;

public class PkArea {

	private final Location pos1;
	private final Location pos2;
	private final @Nullable Location fallpos;
	private final String name;
	private final int maxPlayers;
	private final Difficulty difficulty;

	private final int highestX, lowestX, highestY, lowestY, highestZ, lowestZ;

	private volatile @Nullable ParticleRect outline;

	/**
	 * Initializes an area.
	 * @param name A string with the name of the area
	 * @param p1 The {@link org.bukkit.Location Location} of pos1
	 * @param p2 The {@link org.bukkit.Location Location} of pos2
	 * @param fp The {@link org.bukkit.Location Location} of the fall position (can be null)
	 * @param diff The {@link us.ajg0702.parkour.game.Difficulty Difficulty} of the area
	 * @param maxPlayers The max number of players allowed on the area
	 */
	public PkArea(String name, Location p1, Location p2, @Nullable Location fp, Difficulty diff, int maxPlayers) {
		pos1 = p1;
		pos2 = p2;
		fallpos = fp;
		this.name = name;
		this.maxPlayers = maxPlayers;
		this.difficulty = diff;

		highestX = Math.max(p1.getBlockX(), p2.getBlockX());
		lowestX = Math.min(p1.getBlockX(), p2.getBlockX());
		highestY = Math.max(p1.getBlockY(), p2.getBlockY());
		lowestY = Math.min(p1.getBlockY(), p2.getBlockY());
		highestZ = Math.max(p1.getBlockZ(), p2.getBlockZ());
		lowestZ = Math.min(p1.getBlockZ(), p2.getBlockZ());
	}

	/**
	 * Gets the name of the PkArea
	 * @return A string with the name of the area
	 */
	public String getName() {
		return name;
	}

	public World getWorld() {
		return pos1.getWorld();
	}

	/**
	 * Gets the position that the plugin will teleport the player to when they fall.
	 * @return The location of the fallpos. Null if there is not one.
	 */
	public @Nullable Location getFallPos() {
		Location orig = fallpos;
		if(orig == null) return null;
		return new Location(orig.getWorld(), orig.getBlockX() + 0.5, orig.getBlockY() + 0.5, orig.getBlockZ() + 0.5, orig.getYaw(), orig.getPitch());
	}

	/**
	 * Gets pos1 of the area (opposite corner of pos2)
	 * @return the {@link org.bukkit.Location Location} of pos1
	 */
	public Location getPos1() {
		return pos1.clone();
	}

	/**
	 * Gets pos2 of the area (opposite corner of pos1)
	 * @return the {@link org.bukkit.Location Location} of pos2
	 */
	public Location getPos2() {
		return pos2.clone();
	}

	/**
	 * Gets the difficulty of the area.
	 * @return The {@link us.ajg0702.parkour.game.Difficulty Difficulty} of the area.
	 */
	public Difficulty getDifficulty() {
		return difficulty;
	}

	/**
	 * Gets the max number of players allowed in this area
	 * @return The max number of players allowed in the area (as an int)
	 */
	public int getMax() {
		return maxPlayers < 0 ? Integer.MAX_VALUE : maxPlayers;
	}

	public int getRawMax() {
		return maxPlayers;
	}

	public BlockPos randomPosition() {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		return new BlockPos(r.nextInt(lowestX, highestX + 1), r.nextInt(lowestY, highestY + 1), r.nextInt(lowestZ, highestZ + 1));
	}

	/**
	 * Returns a random position inside the area
	 * @return A random {@link org.bukkit.Location Location} inside the area
	 */
	public Location getRandomPosition() {
		BlockPos p = randomPosition();
		return new Location(getWorld(), p.x(), p.y(), p.z(), (float) ThreadLocalRandom.current().nextDouble(-180, 180), 0f);
	}

	/**
	 * Distance (in blocks) from the given block position to the closest wall of the area.
	 */
	public int distanceFromWall(int x, int y, int z) {
		int d = Math.min(Math.abs(x - highestX), Math.abs(x - lowestX));
		d = Math.min(d, Math.min(Math.abs(y - highestY), Math.abs(y - lowestY)));
		return Math.min(d, Math.min(Math.abs(z - highestZ), Math.abs(z - lowestZ)));
	}

	/**
	 * Returns the distance from the given location to the closest wall
	 * @param l The {@link org.bukkit.Location Location} to check.
	 * @return A Double with the distance from the given location to the closest wall.
	 */
	public Double distanceFromWall(Location l) {
		return (double) distanceFromWall(l.getBlockX(), l.getBlockY(), l.getBlockZ());
	}

	public boolean contains(int x, int y, int z) {
		return x >= lowestX && x <= highestX
				&& y >= lowestY && y <= highestY
				&& z >= lowestZ && z <= highestZ;
	}

	/**
	 * Check if the area contains a location
	 * @param l The {@link org.bukkit.Location Location} to check
	 * @return A boolean indicating if the {@link org.bukkit.Location Location} is in the area.
	 */
	public boolean contains(Location l) {
		return l.getWorld() == getWorld() && contains(l.getBlockX(), l.getBlockY(), l.getBlockZ());
	}

	/**
	 * Shows the outline of the area to a player. Must be called on the player's thread.
	 */
	public void draw(Player player) {
		ParticleRect o = outline;
		if(o == null) {
			o = new ParticleRect(new Location(getWorld(), lowestX, lowestY, lowestZ), highestZ - lowestZ, highestX - lowestX, highestY - lowestY);
			outline = o;
		}
		o.draw(player);
	}
}
