package me.desht.landslide.commands;

import org.bukkit.command.CommandSender;
import org.bukkit.plugin.Plugin;

import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.landslide.LandslidePlugin;

public class ReloadCommand extends AbstractCommand {

	public ReloadCommand() {
		super("landslide reload");
		setUsage("/landslide reload");
		setPermissionNode("landslide.commands.reload");
	}

	@Override
	public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
		LandslidePlugin lPlugin = (LandslidePlugin) plugin;
		lPlugin.reloadConfig();
		lPlugin.processConfig();
		lPlugin.getPerWorldConfig().processConfig();
		MiscUtil.statusMessage(sender, "The plugin configuration has been reloaded.");
		return true;
	}

}
