package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.utils.Updater;

public class UpdateCommand extends SubCommand {

	public UpdateCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("update")
				.requires(perm("ajparkour.update"))
				.executes(ctx -> {
					Updater.getInstance().downloadUpdate(sender(ctx));
					return OK;
				});
	}
}
