package me.desht.landslide;

/*
    This file is part of Landslide

    Landslide is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    Landslide is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with Landslide.  If not, see <http://www.gnu.org/licenses/>.
 */

import java.io.IOException;
import java.util.List;
import java.util.Random;
import java.util.logging.Level;

import me.desht.dhutils.ConfigurationListener;
import me.desht.dhutils.ConfigurationManager;
import me.desht.dhutils.DHUtilsException;
import me.desht.dhutils.DHValidate;
import me.desht.dhutils.LogUtils;
import me.desht.dhutils.MessagePager;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.CommandManager;
import me.desht.landslide.commands.DeleteCfgCommand;
import me.desht.landslide.commands.GetcfgCommand;
import me.desht.landslide.commands.InfoCommand;
import me.desht.landslide.commands.KaboomCommand;
import me.desht.landslide.commands.PageCommand;
import me.desht.landslide.commands.PowerCommand;
import me.desht.landslide.commands.ReloadCommand;
import me.desht.landslide.commands.SetcfgCommand;
import me.desht.landslide.commands.WandCommand;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.MetricsLite;

import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.DefaultFlag;
import com.sk89q.worldguard.protection.flags.Flag;
import com.sk89q.worldguard.protection.flags.StateFlag;

public class LandslidePlugin extends JavaPlugin implements Listener, ConfigurationListener {

	public static final BlockFace[] horizontalFaces = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };
	public static final BlockFace[] allFaces = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN };

	private final SlideManager slideManager = new SlideManager(this);
	private final CommandManager cmds = new CommandManager(this);

	private ConfigurationManager configManager;
	private final Random random = new Random();
	private WorldGuardPlugin worldGuardPlugin = null;
	private PerWorldConfiguration perWorldConfig;

	@Override
	public void onEnable() {
		LogUtils.init(this);

		configManager = new ConfigurationManager(this, this);

		MiscUtil.init(this);

		setupWorldGuard();

		cmds.registerCommand(new ReloadCommand());
		cmds.registerCommand(new GetcfgCommand());
		cmds.registerCommand(new SetcfgCommand());
		cmds.registerCommand(new DeleteCfgCommand());
		cmds.registerCommand(new KaboomCommand());
		cmds.registerCommand(new PageCommand());
		cmds.registerCommand(new PowerCommand());
		cmds.registerCommand(new WandCommand());
		cmds.registerCommand(new InfoCommand());

		processConfig();
		perWorldConfig = new PerWorldConfiguration(this);

		MessagePager.setPageCmd("/landslide page [#|n|p]");
		MessagePager.setDefaultPageSize(getConfig().getInt("pager.lines", 0));

		PluginManager pm = this.getServer().getPluginManager();
		pm.registerEvents(new EventListener(this), this);

		getServer().getScheduler().runTaskTimer(this, new Runnable() {
			@Override
			public void run() {
				slideManager.tick();
			}
		}, 1L, 1L);

		setupMetrics();
	}

	@Override
	public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
		try {
			return cmds.dispatch(sender, command, label, args);
		} catch (DHUtilsException e) {
			MiscUtil.errorMessage(sender, e.getMessage());
			return true;
		}
	}

	@Override
	public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
		return cmds.onTabComplete(sender, command, label, args);
	}

	private void setupMetrics() {
		try {
			MetricsLite metrics = new MetricsLite(this);
			metrics.start();
		} catch (IOException e) {
			LogUtils.warning("Couldn't submit metrics stats: " + e.getMessage());
		}
	}

	private void setupWorldGuard() {
	    Plugin plugin = getServer().getPluginManager().getPlugin("WorldGuard");

	    if (plugin != null && (plugin instanceof WorldGuardPlugin)) {
	    	LogUtils.fine("WorldGuard detected");
		    worldGuardPlugin =  (WorldGuardPlugin) plugin;
	    }
	}

	public Random getRandom() {
		return random;
	}

	public ConfigurationManager getConfigManager() {
		return configManager;
	}

	public SlideManager getSlideManager() {
		return slideManager;
	}

	/**
	 * @return the perWorldConfig
	 */
	public PerWorldConfiguration getPerWorldConfig() {
		return perWorldConfig;
	}

	public boolean isWorldGuardEnabled() {
		return worldGuardPlugin != null;
	}

	public WorldGuardPlugin getWorldGuardPlugin() {
		return worldGuardPlugin;
	}

	public void processConfig() {
		String level = getConfig().getString("log_level");
		try {
			LogUtils.setLogLevel(level);
		} catch (IllegalArgumentException e) {
			LogUtils.warning("invalid log level " + level + " - ignored");
		}

		MiscUtil.setColouredConsole(getConfig().getBoolean("coloured_console"));

		slideManager.setMaxSlidesPerTick(getConfig().getInt("max_slides_per_tick", 20));
		slideManager.setMaxSlidesTotal(getConfig().getInt("max_slides_total", 200));

		if (isWorldGuardEnabled()) {
			slideManager.setWorldGuardEnabled(getConfig().getBoolean("worldguard.enabled"));
			slideManager.setWorldGuardFlag(getConfig().getString("worldguard.use_flag"));
		}
	}

	public void validateWorldGuardFlag(String flagName) {
		String flagName2 = flagName.replace("-", "");
		for (Flag<?> flag : DefaultFlag.getFlags()) {
			if (flag.getName().replace("-", "").equalsIgnoreCase(flagName2)) {
				if (flag instanceof StateFlag) {
					return;
				} else {
					throw new DHUtilsException("Flag " + flagName + " is not a WorldGuard state flag");
				}
            }
        }
		throw new DHUtilsException("Unknown WorldGuard flag " + flagName);
	}

	@Override
	public void onConfigurationValidate(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
		if (newVal != null && (key.contains("slide_chance.") && newVal != null || key.contains("cliff_stability") || key.contains("explode_effect_chance"))) {
			int pct = (Integer) newVal;
			DHValidate.isTrue(pct >= 0 && pct <= 100, "Value must be a percentage (0-100 inclusive)");
		} else if (key.equals("log_level")) {
			try {
				Level.parse(newVal.toString().toUpperCase());
			} catch (IllegalArgumentException e) {
				throw new DHUtilsException(e.getMessage());
			}
		} else if (key.startsWith("transform.")) {
			String s = key.substring(key.indexOf('.') + 1);
			Material from = Material.matchMaterial(s);
			DHValidate.notNull(from, "Invalid material: " + s);
			Material to = Material.matchMaterial((String) newVal);
			DHValidate.notNull(to, "Invalid material: " + s);
		} else if (key.equals("worldguard.use_flag") && isWorldGuardEnabled()) {
			validateWorldGuardFlag((String) newVal);
		}
	}

	@Override
	public void onConfigurationChanged(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
		System.out.println("config changed: " + key + ": " + oldVal + " -> " + newVal);
		if (key.equals("max_slides_per_tick")) {
			slideManager.setMaxSlidesPerTick((Integer) newVal);
		} else if (key.equals("max_slides_total")) {
			slideManager.setMaxSlidesTotal((Integer) newVal);
		} else if (key.equals("log_level")) {
			LogUtils.setLogLevel(newVal.toString());
		} else if (key.equals("coloured_console")) {
			MiscUtil.setColouredConsole((Boolean) newVal);
		} else if (key.equals("worldguard.enabled") && isWorldGuardEnabled()) {
			slideManager.setWorldGuardEnabled((Boolean) newVal);
			slideManager.setWorldGuardFlag(getConfig().getString("worldguard.use_flag"));
		} else if (key.equals("worldguard.use_flag") && isWorldGuardEnabled()) {
			slideManager.setWorldGuardFlag((String) newVal);
		} else {
			getPerWorldConfig().processKey(getConfig(), key);
		}
	}

	public boolean isOrphan(Block block) {
		for (BlockFace f : LandslidePlugin.allFaces) {
			if (block.getRelative(f).getType().isSolid()) {
				return false;
			}
		}
		return true;
	}

}
