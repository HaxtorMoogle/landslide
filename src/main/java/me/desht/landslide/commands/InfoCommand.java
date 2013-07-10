package me.desht.landslide.commands;

import java.util.HashMap;
import java.util.Map;

import me.desht.dhutils.DHValidate;
import me.desht.dhutils.MessagePager;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.AbstractCommand;
import me.desht.landslide.LandslidePlugin;
import me.desht.landslide.PerWorldConfiguration;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class InfoCommand extends AbstractCommand {

	public InfoCommand() {
		super("landslide info", 0, 1);
		setPermissionNode("landslide.commands.info");
		setUsage("/<command> info [<world-name>]");
	}

	private static final String BULLET = ChatColor.LIGHT_PURPLE + "\u2022 " + ChatColor.RESET;

	@Override
	public boolean execute(Plugin plugin, CommandSender sender, String[] args) {
		World w;
		if (args.length == 0) {
			notFromConsole(sender);
			w = ((Player) sender).getWorld();
		} else {
			w = Bukkit.getWorld(args[0]);
			DHValidate.notNull(w, "Unknown world: " + args[0]);
		}

		MessagePager pager = MessagePager.getPager(sender).clear().setParseColours(true);

		LandslidePlugin lPlugin = (LandslidePlugin) plugin;
		PerWorldConfiguration pwc = lPlugin.getPerWorldConfig();
		pager.add("Landslide information for world " + ChatColor.YELLOW + w.getName() + ":");
		pager.add(BULLET + "Landslides are " + (pwc.isEnabled(w) ? "enabled" : "disabled"));
		pager.add(BULLET + "Blocks may " + (pwc.getHorizontalSlides(w) ? "slide horizontally off other blocks" : "only drop vertically"));
		pager.add(BULLET + "Cliff stability is " + pwc.getCliffStability(w) + "%");
		pager.add(BULLET + "Falling blocks will " + (pwc.getDropItems(w) ? "drop an item" : "be destroyed") + " if they can't be placed");
		pager.add(BULLET + "Falling blocks have a " + pwc.getExplodeEffectChance(w) + "% chance to play an explosion effect on landing");
		pager.add(BULLET + "Falling blocks will " + (pwc.getFallingBlocksBounce(w) ? "bounce down slopes" : "always stop where they land"));
		pager.add(BULLET + "Falling blocks will do " + pwc.getFallingBlockDamage(w) + " damage to entities in the way");
		pager.add(BULLET + "Snow must be " + pwc.getSnowSlideThickness(w) + " layers thick before it will slide");
		pager.add(BULLET + "Snow accumulation/melting is checked every " + plugin.getConfig().getInt("snow.check_interval") + "s");
		pager.add(BULLET + "Snow has a " + pwc.getSnowFormChance(w) + "% chance to accumulate when snowing");
		pager.add(BULLET + "Snow has a " + pwc.getSnowMeltChance(w) + "% chance to evaporate when sunny");
		pager.add(BULLET + "Snow can " + (!plugin.getConfig().getBoolean("snow.melt_away") ? "not " : "") + "melt away completely");

		Map<String,Integer> slideChances = getSlideChances(plugin.getConfig(), w.getName());
		if (!slideChances.isEmpty()) {
			pager.add(BULLET + "Block slide chances:");
			for (String mat : MiscUtil.asSortedList(slideChances.keySet())) {
				pager.add("  " + BULLET + mat.toUpperCase() + ": " + slideChances.get(mat) + "%");
			}
		}

		Map<String,String> transforms = getTransforms(plugin.getConfig(), w.getName());
		if (!transforms.isEmpty()) {
			pager.add(BULLET + "Material transformations when blocks slide:");
			for (String mat : MiscUtil.asSortedList(transforms.keySet())) {
				pager.add("  " + BULLET + mat.toUpperCase() + " => " + transforms.get(mat));
			}
		}

		pager.showPage();

		return true;
	}

	private Map<String, Integer> getSlideChances(Configuration config, String worldName) {
		Map<String, Integer> res = new HashMap<String, Integer>();
		ConfigurationSection cs = config.getConfigurationSection("slide_chance");
		if (cs != null) {
			for (String k : cs.getKeys(false)) {
				if (cs.getInt(k) > 0) {
					res.put(k, cs.getInt(k));
				}
			}
		}

		ConfigurationSection wcs = config.getConfigurationSection("worlds." + worldName + ".slide_chance");
		if (wcs != null) {
			for (String k : wcs.getKeys(false)) {
				if (wcs.getInt(k) > 0) {
					res.put(k, wcs.getInt(k));
				}
			}
		}
		return res;
	}

	private Map<String,String> getTransforms(Configuration config, String worldName) {
		Map<String, String> res = new HashMap<String, String>();
		ConfigurationSection cs = config.getConfigurationSection("transform");
		if (cs != null) {
			for (String k : cs.getKeys(false)) {
				if (!cs.getString(k).equalsIgnoreCase(k)) {
					res.put(k, cs.getString(k).toUpperCase());
				}
			}
		}

		ConfigurationSection wcs = config.getConfigurationSection("worlds." + worldName + ".transform");
		if (wcs != null) {
			for (String k : wcs.getKeys(false)) {
				if (!wcs.getString(k).equalsIgnoreCase(k)) {
					res.put(k, wcs.getString(k).toUpperCase());
				}
			}
		}
		return res;
	}
}
