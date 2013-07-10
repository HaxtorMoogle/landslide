package me.desht.landslide.commands;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.desht.dhutils.ConfigurationManager;
import me.desht.dhutils.DHUtilsException;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.landslide.LandslidePlugin;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

public class DeleteCfgCommand extends AbstractCommand {

	public DeleteCfgCommand() {
		super("landslide delete", 1, 1);
		setPermissionNode("landslide.commands.delete");
		setUsage("/<command> delete <config-key>");
	}

	private static final Pattern worldPat = Pattern.compile("^worlds\\.(.+?)\\.(.+)");

	@Override
	public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
		String key = args[0];
		Matcher m = worldPat.matcher(key);
		if (!m.find() || m.groupCount() != 2) {
			throw new DHUtilsException("Only per-world (worlds.world.XXX) keys may be deleted");
		}
		ConfigurationManager configManager = ((LandslidePlugin) plugin).getConfigManager();
		configManager.set(key, (String) null);
		MiscUtil.statusMessage(sender, key + " has been deleted (default will be used)");
		return true;
	}
}
