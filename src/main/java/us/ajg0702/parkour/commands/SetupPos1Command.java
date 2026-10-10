package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;

public class SetupPos1Command extends SubCommand {

	public SetupPos1Command(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("pos1").executes(ctx -> {
			Player p = requirePlayer(ctx);
			if(p == null || !plugin.setup.requireActive(p, msgs())) return OK;
			plugin.setup.pos1 = p.getLocation();
			p.sendMessage(msgs().get("setup.set.pos1", p));
			plugin.setup.warnIfSmall(p, msgs());
			return OK;
		});
	}
}
