package us.ajg0702.parkour.game;

import org.bukkit.Location;
import org.bukkit.World;

public record BlockPos(int x, int y, int z) {

	public static BlockPos of(Location l) {
		return new BlockPos(l.getBlockX(), l.getBlockY(), l.getBlockZ());
	}

	public BlockPos offset(int dx, int dy, int dz) {
		return new BlockPos(x + dx, y + dy, z + dz);
	}

	public Location toLocation(World world) {
		return new Location(world, x, y, z);
	}

	public double distance(BlockPos o) {
		return Math.sqrt(distanceSquared(o));
	}

	public long distanceSquared(BlockPos o) {
		long dx = x - o.x;
		long dy = y - o.y;
		long dz = z - o.z;
		return dx * dx + dy * dy + dz * dz;
	}

	/** Packs the position into a long (same layout as Paper's block keys). */
	public long key() {
		return key(x, y, z);
	}

	public static long key(int x, int y, int z) {
		return ((long) x & 0x7FFFFFF) | (((long) z & 0x7FFFFFF) << 27) | ((long) y << 54);
	}
}
