package me.desht.landslide.commands;

import java.util.HashMap;
import java.util.Map;

import me.desht.dhutils.DHValidate;
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
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;

public class InfoCommand extends AbstractCommand {

	public InfoCommand() {
		super("landslide info", 0, 1);
		setPermissionNode("landslide.commands.info");
		setUsage("/landslide info [<world-name>]");
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

		LandslidePlugin lPlugin = (LandslidePlugin) plugin;
		PerWorldConfiguration pwc = lPlugin.getPerWorldConfig();
		MiscUtil.statusMessage(sender, "Landslide information for world " + ChatColor.YELLOW + w.getName() + ":");
		MiscUtil.statusMessage(sender, BULLET + "Landslides are " + (pwc.isEnabled(w) ? "enabled" : "disabled"));
		MiscUtil.statusMessage(sender, BULLET + "Blocks may " + (pwc.getHorizontalSlides(w) ? "slide horizontally off other blocks" : "only drop vertically"));
		MiscUtil.statusMessage(sender, BULLET + "Cliff stability is " + pwc.getCliffStability(w) + "%");
		MiscUtil.statusMessage(sender, BULLET + "Falling blocks will " + (pwc.getDropItems(w) ? "drop an item" : "be destroyed") + " if they can't be placed");
		MiscUtil.statusMessage(sender, BULLET + "Falling blocks have a " + pwc.getExplodeEffectChance(w) + "% chance to play an explosion effect on landing");
		MiscUtil.statusMessage(sender, BULLET + "Falling blocks will " + (pwc.getFallingBlocksBounce(w) ? "bounce down slopes" : "always stop where they land"));
		MiscUtil.statusMessage(sender, BULLET + "Falling blocks will do " + pwc.getFallingBlockDamage(w) + " damage to entities in the way");

		Map<String,Integer> slideChances = getSlideChances(plugin.getConfig(), w.getName());
		if (!slideChances.isEmpty()) {
			MiscUtil.statusMessage(sender, BULLET + "Block slide chances:");
			for (String mat : MiscUtil.asSortedList(slideChances.keySet())) {
				MiscUtil.statusMessage(sender, "  " + BULLET + mat.toUpperCase() + ": " + slideChances.get(mat) + "%");
			}
		}

		Map<String,String> transforms = getTransforms(plugin.getConfig(), w.getName());
		if (!transforms.isEmpty()) {
			MiscUtil.statusMessage(sender, BULLET + "Material transformations when blocks slide:");
			for (String mat : MiscUtil.asSortedList(transforms.keySet())) {
				MiscUtil.statusMessage(sender, "  " + BULLET + mat.toUpperCase() + " => " + transforms.get(mat));
			}
		}
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
