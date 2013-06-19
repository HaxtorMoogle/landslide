package me.desht.landslide.commands;

import org.bukkit.block.Block;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

import me.desht.dhutils.DHUtilsException;
import me.desht.dhutils.commands.AbstractCommand;

public class KaboomCommand extends AbstractCommand {

	public KaboomCommand() {
		super("landslide kaboom", 0, 1);
		setPermissionNode("landslide.commands.kaboom");
		setUsage("/landslide kaboom [<power-level>]");
	}

	@Override
	public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
		notFromConsole(sender);

		float power;
		if (args.length == 0) {
			power = 4.0f;
		} else {
			try {
				power = Float.parseFloat(args[0]);
			} catch (NumberFormatException e) {
				throw new DHUtilsException("invalid numeric argument: " + args[0]);
			}
		}

		Player player = (Player) sender;
		Block b = player.getTargetBlock(null, 140);
		player.getWorld().createExplosion(b.getLocation(), power);

		return true;
	}

}
