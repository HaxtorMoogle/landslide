package me.desht.landslide.commands;

import me.desht.dhutils.DHValidate;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.landslide.SlideOTron;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class WandCommand extends AbstractCommand {

	public WandCommand() {
		super("landslide wand", 0, 1);
		setPermissionNode("landslide.commands.wand");
		setUsage("/<command> wand [<player-name>]");
	}

	@SuppressWarnings("deprecation")
	@Override
	public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
		Player player;
		if (args.length > 0) {
			String playerName = args[1];
			player = Bukkit.getPlayer(playerName);
			DHValidate.notNull(player, "Player " + playerName + " is not online.");
		} else {
			notFromConsole(sender);
			player = (Player) sender;
		}

		player.getInventory().addItem(SlideOTron.makeWand());
		player.updateInventory();

		return true;
	}

}
