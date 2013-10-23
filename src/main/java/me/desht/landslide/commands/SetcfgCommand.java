package me.desht.landslide.commands;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.desht.dhutils.ConfigurationManager;
import me.desht.dhutils.DHUtilsException;
import me.desht.dhutils.DHValidate;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.landslide.LandslidePlugin;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

public class SetcfgCommand extends AbstractCommand {

	public SetcfgCommand() {
		super("landslide setcfg", 2);
		setPermissionNode("landslide.commands.setcfg");
		setUsage("/<command> setcfg <config-key> <value>");
		setQuotedArgs(true);
	}

	private static final Pattern worldPat = Pattern.compile("^worlds\\.(.+?)\\.(.+)");

	@Override
	public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
		String key = args[0], val = args[1];

		ConfigurationManager configManager = ((LandslidePlugin) plugin).getConfigManager();

		String worldName;
		String subKey = key;
		Matcher m = worldPat.matcher(key);
		if (m.find() && m.groupCount() == 2) {
			worldName = m.group(1);
			DHValidate.notNull(Bukkit.getWorld(worldName), "Unknown world: " + worldName);
			subKey = m.group(2);
		}

		try {
			Object def = getDefault(configManager, subKey);
			if (def != null) {
				if (configManager.check(key) == null) {
					configManager.insert(key, def);
				}
			}
			configManager.set(key, val);
			Object res = configManager.get(key);
			MiscUtil.statusMessage(sender, key + " is now set to '&e" + res + "&-'");
		} catch (DHUtilsException e) {
			MiscUtil.errorMessage(sender, e.getMessage());
		} catch (IllegalArgumentException e) {
			MiscUtil.errorMessage(sender, e.getMessage());
		}
		return true;
	}

	private Object getDefault(ConfigurationManager mgr, String key) {
		if (key.startsWith("slide_chance.")) {
			String mat = key.substring(key.indexOf(".") + 1);
			DHValidate.notNull(Material.matchMaterial(mat), "Unknown material: " + mat);
			return 0;
		} else if (key.startsWith("transform.")) {
			String mat = key.substring(key.indexOf(".") + 1);
			DHValidate.notNull(Material.matchMaterial(mat), "Unknown material: " + mat);
			return "";
		} else if (mgr.check(key) != null) {
			return mgr.getConfig().getDefaults().get(key);
		}
		return null;
	}

	@Override
	public List<String> onTabComplete(Plugin plugin, CommandSender sender, String[] args) {
		ConfigurationSection config = plugin.getConfig();
		switch (args.length) {
		case 1:
			return getConfigCompletions(sender, config, args[0]);
		case 2:
			return getConfigValueCompletions(sender, args[0], config.get(args[0]), "", args[1]);
		default:
			return noCompletions(sender);
		}
	}
}
