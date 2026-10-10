package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;
import us.ajg0702.parkour.game.PkPlayer;

import java.util.ArrayList;
import java.util.List;

public class ListCommand extends SubCommand {

	public ListCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("list").executes(ctx -> {
			List<PkPlayer> plys = plugin.man.getPlayers();
			List<Component> lines = new ArrayList<>();
			lines.add(msgs().get("list.header"));
			if(plys.isEmpty()) {
				lines.add(msgs().get("list.none"));
			}
			for(PkPlayer p : plys) {
				lines.add(msgs().get("list.format", tag("name", p.getPlayer().getName()), tag("score", p.getScore())));
			}
			sender(ctx).sendMessage(Messages.join(lines));
			return OK;
		});
	}
}
