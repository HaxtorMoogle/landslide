package me.desht.landslide.commands;

import me.desht.dhutils.DHValidate;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.landslide.SlideOTron;

import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
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
			String playerName = args[0];
			player = Bukkit.getPlayer(playerName);
			DHValidate.notNull(player, "Player " + playerName + " is not online.");
		} else {
			notFromConsole(sender);
			player = (Player) sender;
		}

		player.getInventory().addItem(SlideOTron.makeWand());
		player.updateInventory();

		if (!sender.getName().equals(player.getName())) {
			MiscUtil.statusMessage(sender, "Gave a Slide-O-Tron\u2122 to " + player.getName());
			String from = sender instanceof ConsoleCommandSender ? "the Powers-That-Be" : sender.getName();
			MiscUtil.alertMessage(player, "You received a Slide-O-Tron\u2122 from " + from + ".  Use with care.");
		} else {
			MiscUtil.statusMessage(sender, "Gave yourself a Slide-O-Tron\u2122");
		}
		return true;
	}

}
