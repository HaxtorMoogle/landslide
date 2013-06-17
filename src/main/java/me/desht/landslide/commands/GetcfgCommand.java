package me.desht.landslide.commands;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import me.desht.dhutils.DHUtilsException;
import me.desht.dhutils.MessagePager;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;

import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.plugin.Plugin;

public class GetcfgCommand extends AbstractCommand {

	public GetcfgCommand() {
		super("landslide getcfg", 0, 1);
		setPermissionNode("landslide.commands.getcfg");
		setUsage("/<command> getcfg  [<config-key>]");
	}

	@Override
	public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
		List<String> lines = getPluginConfiguration(plugin, args.length >= 1 ? args[0] : null);
		if (lines.size() > 1) {
			MessagePager pager = MessagePager.getPager(sender).clear().setParseColours(true);
			for (String line : lines) {
				pager.add(line);
			}
			pager.showPage();
		} else if (lines.size() == 1) {
			MiscUtil.statusMessage(sender, lines.get(0));
		}
		return true;
	}

	public List<String> getPluginConfiguration(Plugin plugin, String section) {
		ArrayList<String> res = new ArrayList<String>();
		Configuration config = plugin.getConfig();
		ConfigurationSection cs = config.getRoot();

		Set<String> items;
		if (section == null) {
			items = config.getKeys(true);
		} else {
			if (config.isConfigurationSection(section)) {
				cs = config.getConfigurationSection(section);
				items = cs.getKeys(true);
			} else {
				items = new HashSet<String>();
				if (config.contains(section)) {
					items.add(section);
				} else {
					throw new DHUtilsException("No such config item: " + section);
				}
			}
		}

		for (String k : items) {
			if (cs.isConfigurationSection(k))
				continue;
			res.add("&f" + k + "&- = '&e" + cs.get(k) + "&-'");
		}
		Collections.sort(res);
		return res;
	}

	@Override
	public List<String> onTabComplete(Plugin plugin, CommandSender sender, String[] args) {
		switch (args.length) {
		case 1:
			return getConfigCompletions(sender, plugin.getConfig(), args[0]);
		default:
			return noCompletions(sender);
		}
	}
}
