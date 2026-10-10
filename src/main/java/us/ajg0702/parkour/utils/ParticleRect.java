package us.ajg0702.parkour.utils;

import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.entity.Player;

/**
 * Debug outline of a parkour area. Points are computed once and reused.
 * Originally created by Connor on 12/3/2016.
 */
public class ParticleRect {

	private static final double STEP = 1.0;
	private static final double MAX_DIST_SQ = 64 * 64;

	private final double[] points;
	private final double cx, cy, cz;

	public ParticleRect(Location start, double width, double length, double height) {
		double ox = start.getX();
		double oy = start.getY();
		double oz = start.getZ();
		cx = ox + length * 0.5;
		cy = oy + height * 0.5;
		cz = oz + width * 0.5;

		double[][] sides = {
				{0, 0, 0, length, 0, 0},
				{0, 0, 0, 0, 0, width},
				{0, 0, 0, 0, height, 0},
				{0, 0, width, 0, height, 0},
				{length, 0, 0, 0, height, 0},
				{0, 0, width, length, 0, 0},
				{length, 0, 0, 0, 0, width},
				{0, height, 0, 0, 0, width},
				{0, height, 0, length, 0, 0},
				{0, height, width, length, 0, 0},
				{length, height, 0, 0, 0, width},
				{length, 0, width, 0, height, 0}
		};

		int count = 0;
		for(double[] s : sides) {
			double len = Math.hypot(Math.hypot(s[3], s[4]), s[5]);
			if(len > 0) count += (int) (len / STEP) + 1;
		}
		double[] pts = new double[count * 3];
		int i = 0;
		for(double[] s : sides) {
			double len = Math.hypot(Math.hypot(s[3], s[4]), s[5]);
			if(len == 0) continue;
			double dx = s[3] / len * STEP;
			double dy = s[4] / len * STEP;
			double dz = s[5] / len * STEP;
			double x = ox + s[0];
			double y = oy + s[1];
			double z = oz + s[2];
			for(double t = 0; t <= len; t += STEP) {
				pts[i++] = x;
				pts[i++] = y;
				pts[i++] = z;
				x += dx;
				y += dy;
				z += dz;
			}
		}
		if(i == pts.length) {
			points = pts;
		} else {
			points = new double[i];
			System.arraycopy(pts, 0, points, 0, i);
		}
	}

	public void draw(Player player) {
		Location loc = player.getLocation();
		double dx = loc.getX() - cx;
		double dy = loc.getY() - cy;
		double dz = loc.getZ() - cz;
		if(dx * dx + dy * dy + dz * dz > MAX_DIST_SQ) return;
		for(int i = 0; i < points.length; i += 3) {
			player.spawnParticle(Particle.FLAME, points[i], points[i + 1], points[i + 2], 1, 0, 0, 0, 0);
		}
	}
}
