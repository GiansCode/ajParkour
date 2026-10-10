package us.ajg0702.parkour.commands;

import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.command.brigadier.Commands;
import org.bukkit.command.CommandSender;
import us.ajg0702.parkour.Main;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class ReloadCommand extends SubCommand {

	public ReloadCommand(Main plugin) {
		super(plugin);
	}

	@Override
	public LiteralArgumentBuilder<CommandSourceStack> build() {
		return Commands.literal("reload")
				.requires(perm("ajparkour.reload"))
				.executes(ctx -> {
					List<String> options = new ArrayList<>(plugin.getReloadable());
					options.add("all");
					msgs().send(sender(ctx), player(ctx), "reload.usage",
							tag("cmd", label(ctx)),
							tag("options", String.join(", ", options)));
					return OK;
				})
				.then(Commands.argument("what", StringArgumentType.word())
						.suggests((_, builder) -> {
							List<String> options = new ArrayList<>(plugin.getReloadable());
							options.add("all");
							return suggest(builder, options);
						})
						.executes(ctx -> {
							CommandSender sender = sender(ctx);
							String what = StringArgumentType.getString(ctx, "what").toLowerCase(Locale.ROOT);
							if(what.equals("all")) {
								for(String rl : plugin.getReloadable()) {
									plugin.reload(rl, sender);
								}
							} else {
								plugin.reload(what, sender);
							}
							return OK;
						}));
	}
}
