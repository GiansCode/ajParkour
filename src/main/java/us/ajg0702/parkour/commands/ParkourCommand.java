package us.ajg0702.parkour.commands;

import io.papermc.paper.command.brigadier.Commands;
import us.ajg0702.parkour.Main;

import java.util.List;

/**
 * Registers {@code /ajparkour} and its aliases with Brigadier.
 */
public final class ParkourCommand {

	private static final List<String> ALIASES = List.of("ap", "ajp", "ajpk", "apk");

	private ParkourCommand() {}

	public static void register(Main plugin, Commands registrar) {
		var root = Commands.literal("ajparkour")
				.executes(new HelpCommand(plugin)::show);
		for(SubCommand cmd : commands(plugin)) {
			root.then(cmd.build());
		}
		registrar.register(root.build(), "ajParkour commands", ALIASES);
	}

	private static List<SubCommand> commands(Main plugin) {
		return List.of(
				new HelpCommand(plugin),
				new StartCommand(plugin),
				new ListCommand(plugin),
				new TopCommand(plugin),
				new AreasCommand(plugin),
				new BlocksCommand(plugin),
				new SetupCommand(plugin),
				new EditCommand(plugin),
				new PortalsCommand(plugin),
				new RemoveAreaCommand(plugin),
				new ReloadCommand(plugin),
				new ResetCommand(plugin),
				new UpdateCommand(plugin),
				new VersionCommand(plugin),
				new DifficultiesCommand(plugin),
				new CacheDebugCommand(plugin)
		);
	}
}
