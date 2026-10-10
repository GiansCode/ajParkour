package us.ajg0702.parkour.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import net.kyori.adventure.text.Component;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.Messages;
import us.ajg0702.parkour.game.PkArea;
import us.ajg0702.parkour.top.TopEntry;
import us.ajg0702.parkour.top.TopManager;
import us.ajg0702.parkour.utils.Tasks;

import java.util.ArrayList;
import java.util.List;

public class TopCommand extends SubCommand {

	public TopCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("top")
				.executes(ctx -> show(ctx, null))
				.then(Commands.argument("area", StringArgumentType.string())
						.suggests(areas())
						.executes(ctx -> show(ctx, StringArgumentType.getString(ctx, "area"))));
	}

	private int show(CommandContext<CommandSourceStack> ctx, @Nullable String areaInput) {
		CommandSender sender = sender(ctx);
		Player sply = player(ctx);
		String area = areaInput;
		if(areaInput != null) {
			PkArea a = plugin.man.getArea(areaInput);
			if(a != null) area = a.getName();
		}
		String areaName = area;
		TopManager.getInstance().getTopAsync(area, plugin.settings().topShown()).thenAccept(entries -> {
			List<TopEntry> shown = entries.stream().filter(e -> !e.isEmpty()).toList();
			Component message;
			if(shown.isEmpty()) {
				message = msgs().get("nobodys-played-yet", sply);
			} else {
				List<Component> lines = new ArrayList<>();
				lines.add(areaName == null
						? msgs().get("top.header", sply)
						: msgs().get("top.header-area", sply, tag("area", areaName)));
				for(TopEntry entry : shown) {
					String time = entry.time() < 0
							? msgs().plain("placeholders.stats.no-data", sply)
							: msgs().plain("placeholders.stats.time-format", sply, tag("m", entry.time() / 60), tag("s", entry.time() % 60));
					lines.add(msgs().get("top.format", sply,
							tag("pos", entry.position()),
							tag("name", entry.name()),
							tag("score", entry.score()),
							tag("time", time)));
				}
				message = Messages.join(lines);
			}
			Component send = message;
			if(sply != null) Tasks.entity(sply, () -> sender.sendMessage(send));
			else Tasks.global(() -> sender.sendMessage(send));
		});
		return OK;
	}
}
