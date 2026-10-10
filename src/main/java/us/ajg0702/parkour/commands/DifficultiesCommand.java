package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.game.Difficulty;

public class DifficultiesCommand extends SubCommand {

	public DifficultiesCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("difficulties").executes(ctx -> {
			for(Difficulty d : Difficulty.values()) {
				sender(ctx).sendMessage(d + " (" + d.getMin() + "/" + d.getMax() + ")");
			}
			return OK;
		});
	}
}
