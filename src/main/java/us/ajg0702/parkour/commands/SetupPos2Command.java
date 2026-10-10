package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;

public class SetupPos2Command extends SubCommand {

	public SetupPos2Command(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("pos2").executes(ctx -> {
			Player p = requirePlayer(ctx);
			if(p == null || !plugin.setup.requireActive(p, msgs())) return OK;
			plugin.setup.pos2 = p.getLocation();
			p.sendMessage(msgs().get("setup.set.pos2", p));
			plugin.setup.warnIfSmall(p, msgs());
			return OK;
		});
	}
}
