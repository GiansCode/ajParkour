package us.ajg0702.parkour.game;

import io.papermc.paper.datacomponent.item.ResolvableProfile;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Skull;
import org.bukkit.block.data.BlockData;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.utils.Tasks;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public class PkJump {

	private static final float[] DIRS = {0f, 90f, 180f, -180f, -90f};
	private static final int[] AIR_DY = {0, 1, -1, 2, -2, 3};
	private static final BlockData AIR = Material.AIR.createBlockData();
	private static final Collection<List<BlockPos>> NO_OTHERS = List.of();

	private final PkPlayer ply;
	private final World world;
	private final BlockPos pos;
	private volatile boolean placed = false;

	PkJump(PkPlayer ply, World world, BlockPos pos) {
		this.ply = ply;
		this.world = world;
		this.pos = pos;
	}

	/**
	 * Picks the best position for the next jump.
	 * @param from the 'from' position of the previous jump
	 * @param existing the jumps already generated for this player (oldest first)
	 * @param others snapshots of the jump positions of other players in the same area
	 */
	static PkJump generate(PkPlayer ply, World world, BlockPos from, float yaw, List<PkJump> existing, Collection<List<BlockPos>> others) {
		Difficulty d = JumpManager.getInstance().resolve(ply.getArea().getDifficulty(), ply.getScore());

		int r = Main.random(d.getMin(), d.getMax());
		int maxy = 1;
		if(r > 4) maxy = 0;
		if(r >= 5) r = 5;

		if(existing.size() >= 2) {
			int prevy = existing.getLast().pos.y();
			int prev2y = existing.get(existing.size() - 2).pos.y();
			if(prevy - prev2y > 0) maxy = 0;
		}

		BlockPos[] candidates = new BlockPos[12];
		int n = 0;
		candidates[n++] = from.offset(r, 0, 0);
		candidates[n++] = from.offset(-r, 0, 0);
		candidates[n++] = from.offset(0, 0, r);
		candidates[n++] = from.offset(0, 0, -r);
		if(maxy != 0) {
			candidates[n++] = from.offset(r, maxy, 0);
			candidates[n++] = from.offset(-r, maxy, 0);
			candidates[n++] = from.offset(r, -maxy, 0);
			candidates[n++] = from.offset(-r, -maxy, 0);
			candidates[n++] = from.offset(0, maxy, r);
			candidates[n++] = from.offset(0, -maxy, r);
			candidates[n++] = from.offset(0, maxy, -r);
			candidates[n++] = from.offset(0, -maxy, -r);
		}

		PkArea area = ply.getArea();
		float closest = closestDir(yaw);
		int goodr = Main.random(-10, 1);
		ChunkRead chunks = new ChunkRead();
		if(others == null) others = NO_OTHERS;

		int best = Integer.MIN_VALUE;
		int bestN = 0;
		BlockPos[] bestList = new BlockPos[n];
		for(int i = 0; i < n; i++) {
			int s = score(world, candidates[i], from, area, others, closest, goodr, chunks);
			if(s > best) {
				best = s;
				bestList[0] = candidates[i];
				bestN = 1;
			} else if(s == best) {
				bestList[bestN++] = candidates[i];
			}
		}
		return new PkJump(ply, world, bestList[ThreadLocalRandom.current().nextInt(bestN)]);
	}

	/**
	 * Calculates the score for a particular block location
	 * @param block a {@link org.bukkit.Location Location} of the block to get the score of
	 * @param from a {@link org.bukkit.Location Location} of the 'from' position of the previous jump
	 * @param area The PkArea the block would be in
	 * @param ply The player the block would belong to.
	 * @param yaw a float with the player's yaw (left-right looking)
	 * @return the score of a block. (usually 0-10, but can be higher or lower)
	 */
	public static int getBlockScore(Location block, Location from, PkArea area, @Nullable PkPlayer ply, float yaw) {
		List<List<BlockPos>> others = new ArrayList<>();
		for(PkPlayer p : Manager.getInstance().getPlayersInArea(area)) {
			if(p != ply) others.add(p.jumpSnapshot());
		}
		return score(block.getWorld(), BlockPos.of(block), BlockPos.of(from), area, others, closestDir(yaw), Main.random(-10, 1), new ChunkRead());
	}

	static int getBlockScore(World world, BlockPos block, BlockPos from, PkArea area, Collection<List<BlockPos>> others, float yaw) {
		return score(world, block, from, area, others, closestDir(yaw), Main.random(-10, 1), new ChunkRead());
	}

	private static int score(World world, BlockPos block, BlockPos from, PkArea area, Collection<List<BlockPos>> others,
	                         float closest, int goodr, ChunkRead chunks) {
		int x = block.x();
		int y = block.y();
		int z = block.z();

		if(!chunks.readable(world, x, z)) return -340;
		for(int dy : AIR_DY) {
			if(!world.getType(x, y + dy, z).isAir()) return -40;
		}

		int score = 10;
		int xc = from.x() - x;
		int zc = from.z() - z;

		if(xc == 0 && zc > 0) {
			score += pick(closest, goodr, 180f, -90f, 90f);
		} else if(xc < 0 && zc > 0) {
			score += pick(closest, goodr, Float.NaN, 90f, 0f);
		} else if(xc < 0 && zc == 0) {
			score += pick(closest, goodr, 90f, 0f, 180f);
		} else if(xc < 0) {
			score += pick(closest, goodr, Float.NaN, 90f, 180f);
		} else if(xc == 0 && zc < 0) {
			score += pick(closest, goodr, 0f, 90f, -90f);
		} else if(xc > 0 && zc < 0) {
			score += pick(closest, goodr, Float.NaN, 180f, -90f);
		} else if(xc > 0 && zc == 0) {
			score += pick(closest, goodr, -90f, 0f, 180f);
		} else if(xc > 0) {
			score += pick(closest, goodr, Float.NaN, 0f, -90f);
		}

		if(!others.isEmpty()) {
			long closestOther = Long.MAX_VALUE;
			for(List<BlockPos> jumps : others) {
				for(BlockPos j : jumps) {
					closestOther = Math.min(closestOther, block.distanceSquared(j));
				}
			}
			if(closestOther != Long.MAX_VALUE) {
				long ch = 7 - Math.round(Math.sqrt(closestOther));
				if(ch > 0) score -= (int) ch;
			}
		}

		int d = area.distanceFromWall(x, y, z);
		if(!area.contains(x, y, z)) {
			d = -Math.abs(d);
			score = -10;
		}
		if(d <= 0) d -= 10;
		if(d < 7 && d >= 0) {
			score -= 7 - d;
		} else if(d < 0) {
			score += d;
		}
		return score;
	}

	private static float closestDir(float yaw) {
		if(yaw < 0) yaw += 360;
		if(yaw > 180) {
			yaw -= 360;
			yaw = Math.abs(yaw) * -1;
		}
		yaw *= -1;
		float distance = Math.abs(DIRS[0] - yaw);
		int idx = 0;
		for(int c = 1; c < DIRS.length; c++) {
			float cdistance = Math.abs(DIRS[c] - yaw);
			if(cdistance < distance) {
				idx = c;
				distance = cdistance;
			}
		}
		return DIRS[idx];
	}

	/**
	 * Direction scoring. For straight jumps, {@code best} gets the random bonus and {@code a}/{@code b} get -3.
	 * For diagonal jumps ({@code best} is NaN), {@code a}/{@code b} get +1. Everything else gets -7.
	 */
	private static int pick(float closest, int goodr, float best, float a, float b) {
		if(!Float.isNaN(best)) {
			if(same(closest, best)) return goodr;
			if(same(closest, a) || same(closest, b)) return -3;
			return -7;
		}
		if(same(closest, a) || same(closest, b)) return 1;
		return -7;
	}

	private static boolean same(float o, float t) {
		if(Math.abs(t - 180f) < 0.001 && Math.abs(o + 180f) < 0.001) return true;
		return Math.abs(o - t) < 0.001;
	}

	private static final class ChunkRead {
		private int cx = Integer.MIN_VALUE;
		private int cz;
		private boolean ok;

		boolean readable(World world, int x, int z) {
			int nx = x >> 4;
			int nz = z >> 4;
			if(nx == cx && nz == cz) return ok;
			cx = nx;
			cz = nz;
			return ok = world.isChunkLoaded(nx, nz) && Bukkit.isOwnedByCurrentRegion(world, nx, nz);
		}
	}

	public BlockPos pos() {
		return pos;
	}

	/**
	 * Get 'from' location
	 * @return a {@link org.bukkit.Location Location} that the player is supposed to jump from this jump to the next one
	 */
	public Location getFrom() {
		return pos.toLocation(world);
	}

	/**
	 * Get 'to' location
	 * @return a {@link org.bukkit.Location Location} that the player is supposed to jump to from the previous jump
	 */
	public Location getTo() {
		return pos.toLocation(world);
	}

	/**
	 * Places all blocks for this jump
	 */
	public void place() {
		Main main = ply.plugin;
		Material type = main.settings().randomBlockEachJump()
				? main.selector.getBlockMaterial(ply.getPlayer(), ply.getArea())
				: ply.getBlockMaterial();
		List<UUID> presents = type == Material.PLAYER_HEAD ? main.selector.getPresents() : List.of();
		placed = true;
		int x = pos.x();
		int y = pos.y();
		int z = pos.z();
		Tasks.region(world, x, z, () -> {
			Block b = world.getBlockAt(x, y, z);
			b.setType(type, false);
			if(presents.isEmpty()) return;
			if(b.getState(false) instanceof Skull skull) {
				skull.setProfile(ResolvableProfile.resolvableProfile()
						.uuid(presents.get(ThreadLocalRandom.current().nextInt(presents.size())))
						.build());
				skull.update(true, false);
			}
		});
	}

	public boolean isPlaced() {
		return placed;
	}

	/**
	 * Breaks all blocks from this jump
	 */
	public void remove() {
		placed = false;
		int x = pos.x();
		int y = pos.y();
		int z = pos.z();
		Tasks.region(world, x, z, () -> world.getBlockAt(x, y, z).setBlockData(AIR, false));
	}
}
