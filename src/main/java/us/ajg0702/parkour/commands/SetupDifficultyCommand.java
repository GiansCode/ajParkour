package us.ajg0702.parkour.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.entity.Player;
import us.ajg0702.parkour.Main;
import us.ajg0702.parkour.game.Difficulty;

import java.util.Arrays;
import java.util.Locale;

public class SetupDifficultyCommand extends SubCommand {

	public SetupDifficultyCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("difficulty")
				.executes(ctx -> {
					sender(ctx).sendMessage(msgs().get("setup.diff.need-args", player(ctx)));
					return OK;
				})
				.then(Commands.argument("difficulty", StringArgumentType.word())
						.suggests((_, builder) -> suggest(builder,
								Arrays.stream(Difficulty.values()).map(d -> d.name().toLowerCase(Locale.ROOT)).toList()))
						.executes(ctx -> {
							Player p = requirePlayer(ctx);
							if(p == null || !plugin.setup.requireActive(p, msgs())) return OK;
							String raw = StringArgumentType.getString(ctx, "difficulty").toUpperCase(Locale.ROOT);
							try {
								plugin.setup.diff = Difficulty.valueOf(raw);
							} catch(IllegalArgumentException e) {
								p.sendMessage(msgs().get("setup.diff.not-valid", p));
								return OK;
							}
							p.sendMessage(msgs().get("setup.set.diff", p));
							return OK;
						}));
	}
}
