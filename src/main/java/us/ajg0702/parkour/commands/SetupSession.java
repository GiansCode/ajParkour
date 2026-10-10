package us.ajg0702.parkour.commands;

import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.Messages;
import us.ajg0702.parkour.game.Difficulty;
import us.ajg0702.parkour.game.PkArea;

import java.util.UUID;

/**
 * The area currently being created or edited. Only one area can be set up at a time;
 * a second admin has to run the command twice to take over.
 */
public final class SetupSession {

	public volatile @Nullable String name;
	public volatile @Nullable Location pos1;
	public volatile @Nullable Location pos2;
	public volatile @Nullable Location fallpos;
	public volatile @Nullable Difficulty diff;
	public volatile @Nullable Integer max;

	private @Nullable UUID owner;
	private @Nullable UUID overridePlayer;

	public boolean isActive() {
		return name != null;
	}

	/**
	 * @return false if someone else is setting up an area (the player can run the command again to take over)
	 */
	public synchronized boolean begin(Player p, String name) {
		UUID id = p.getUniqueId();
		if(isActive() && !id.equals(owner) && !id.equals(overridePlayer)) {
			overridePlayer = id;
			return false;
		}
		clear();
		this.owner = id;
		this.name = name;
		return true;
	}

	public synchronized boolean edit(Player p, PkArea area) {
		if(!begin(p, area.getName())) return false;
		pos1 = area.getPos1();
		pos2 = area.getPos2();
		fallpos = area.getFallPos();
		diff = area.getDifficulty();
		max = area.getRawMax();
		return true;
	}

	public synchronized void clear() {
		name = null;
		pos1 = null;
		pos2 = null;
		fallpos = null;
		diff = null;
		max = null;
		owner = null;
		overridePlayer = null;
	}

	public boolean requireActive(Player p, Messages msgs) {
		if(isActive()) return true;
		p.sendMessage(msgs.get("setup.need-to-create", p));
		return false;
	}

	public boolean isComplete() {
		return name != null && pos1 != null && pos2 != null && diff != null;
	}

	public void warnIfSmall(Player p, Messages msgs) {
		if(pos1 == null || pos2 == null) return;
		int l = Math.abs(pos1.getBlockX() - pos2.getBlockX());
		int h = Math.abs(pos1.getBlockY() - pos2.getBlockY());
		int w = Math.abs(pos1.getBlockZ() - pos2.getBlockZ());
		if(l < 20 || h < 20 || w < 20) {
			p.sendMessage(msgs.get("setup.area.too-small", p,
					Messages.tag("w", w), Messages.tag("h", h), Messages.tag("l", l)));
		}
	}
}
