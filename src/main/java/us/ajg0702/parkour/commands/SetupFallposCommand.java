package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;

public class SetupFallposCommand extends SubCommand {

	public SetupFallposCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("fallpos").executes(ctx -> {
			Player p = requirePlayer(ctx);
			if(p == null || !plugin.setup.requireActive(p, msgs())) return OK;
			plugin.setup.fallpos = p.getLocation();
			p.sendMessage(msgs().get("setup.set.fallpos", p));
			return OK;
		});
	}
}
