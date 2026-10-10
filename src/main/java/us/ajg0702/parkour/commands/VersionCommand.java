package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import us.ajg0702.parkour.Main;

public class VersionCommand extends SubCommand {

	public VersionCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("version").executes(ctx -> {
			msgs().send(sender(ctx), player(ctx), "version", tag("version", plugin.getPluginMeta().getVersion()));
			return OK;
		});
	}
}
