package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.game.PkArea;

public class SetupSaveCommand extends SubCommand {

	public SetupSaveCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("save").executes(ctx -> {
			Player p = requirePlayer(ctx);
			if(p == null || !plugin.setup.requireActive(p, msgs())) return OK;
			SetupSession s = plugin.setup;
			if(!s.isComplete()) {
				msgs().send(p, "setup.save.not-done", tag("cmd", label(ctx)));
				return OK;
			}
			String name = s.name;
			plugin.areaStorage.save(new PkArea(name, s.pos1, s.pos2, s.fallpos, s.diff, s.max == null ? -1 : s.max));
			s.clear();
			msgs().send(p, "setup.save.success", tag("name", name));
			return OK;
		});
	}
}
