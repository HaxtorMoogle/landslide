package me.desht.landslide.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.desht.dhutils.DHUtilsException;
import me.desht.dhutils.DHValidate;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.landslide.SlideOTron;

public class PowerCommand extends AbstractCommand {

	public PowerCommand() {
		super("landslide power", 1, 1);
		setPermissionNode("landslide.commands.power");
		setUsage("/landslide power <power-level>");
	}

	@Override
	public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
		notFromConsole(sender);

		Player player = (Player) sender;
		SlideOTron wand = SlideOTron.getWand(player);
		DHValidate.notNull(wand, "You are not holding a Slide-O-Tron wand");

		try {
			int power = Integer.parseInt(args[0]);
			wand.setPower(power);
		} catch (NumberFormatException e) {
			throw new DHUtilsException("Invalid numeric quantity: " + args[0]);
		}

		return true;
	}

}
