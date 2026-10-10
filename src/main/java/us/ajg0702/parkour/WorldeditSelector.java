package us.ajg0702.parkour;

import com.sk89q.worldedit.IncompleteRegionException;
import com.sk89q.worldedit.LocalSession;
import com.sk89q.worldedit.bukkit.WorldEditPlugin;
import com.sk89q.worldedit.math.BlockVector3;
import com.sk89q.worldedit.regions.Region;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.commands.SetupSession;

/**
 * Only loaded when WorldEdit is installed.
 */
public final class WorldeditSelector {
	private WorldeditSelector() {}

	public static void select(Player player, Messages msgs, SetupSession session) {
		WorldEditPlugin wep = (WorldEditPlugin) Bukkit.getPluginManager().getPlugin("WorldEdit");
		if(wep == null) {
			player.sendMessage(msgs.get("setup.we.not-installed", player));
			return;
		}
		LocalSession s = wep.getSession(player);
		Region region;
		try {
			region = s.getSelection(s.getSelectionWorld());
		} catch(IncompleteRegionException e) {
			player.sendMessage(msgs.get("setup.we.incomplete-selection", player));
			return;
		}
		World world = Bukkit.getWorld(region.getWorld().getName());
		BlockVector3 max = region.getMaximumPoint();
		BlockVector3 min = region.getMinimumPoint();
		session.pos1 = new Location(world, max.x(), max.y(), max.z());
		session.pos2 = new Location(world, min.x(), min.y(), min.z());
		player.sendMessage(msgs.get("setup.set.we", player));
	}
}
