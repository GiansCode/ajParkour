package us.ajg0702.parkour.commands;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;
import us.ajg0702.parkour.game.PkArea;

import java.util.ArrayList;
import java.util.List;

public class AreasCommand extends SubCommand {

	public AreasCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("areas").executes(ctx -> {
			Player sply = player(ctx);
			List<PkArea> areas = plugin.man.getAreas();
			if(areas.isEmpty()) {
				msgs().send(sender(ctx), sply, "commands.listareas.none");
				return OK;
			}
			List<Component> lines = new ArrayList<>();
			lines.add(msgs().get("commands.listareas.header", sply));
			for(PkArea a : areas) {
				lines.add(msgs().get("commands.listareas.format", sply,
						tag("name", a.getName()), tag("difficulty", a.getDifficulty().toString())));
			}
			sender(ctx).sendMessage(Messages.join(lines));
			return OK;
		});
	}
}
