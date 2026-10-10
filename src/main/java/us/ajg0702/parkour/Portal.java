package us.ajg0702.parkour;

import org.bukkit.Location;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.game.PkArea;

public record Portal(String name, Location loc, @Nullable PkArea area) {

	public Location getLoc() {
		return loc;
	}

	public @Nullable PkArea getArea() {
		return area;
	}

	public String getName() {
		return name;
	}
}
