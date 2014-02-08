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
import java.util.regex.Pattern;

import com.comphenix.protocol.ProtocolLibrary;
import me.desht.dhutils.*;
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

import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.mcstats.MetricsLite;

import com.sk89q.worldguard.bukkit.WGBukkit;
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

	private int ticks = 0;
	private int snowInterval = 600;
	private boolean protocolLibEnabled = false;

	private static LandslidePlugin instance = null;

	@Override
	public void onEnable() {
		instance = this;

		LogUtils.init(this);

		configManager = new ConfigurationManager(this, this);

		MiscUtil.init(this);

		Debugger.getInstance().setPrefix("[LSL] ");
		Debugger.getInstance().setLevel(getConfig().getInt("debug_level"));
		if (getConfig().getInt("debug_level") > 0) {
			Debugger.getInstance().setTarget(getServer().getConsoleSender());
		}

		setupWorldGuard();
		setupProtocolLib();

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

		if (protocolLibEnabled) {
			ItemGlow.init(this);
		}

		SlideOTron.setupRecipe();

		PluginManager pm = this.getServer().getPluginManager();
		pm.registerEvents(new EventListener(this), this);

		getServer().getScheduler().runTaskTimer(this, new Runnable() {
			@Override
			public void run() {
				slideManager.tick();
				if (snowInterval > 0 && ++ticks % snowInterval == 0) {
					handleSnowAccumulation();
				}
			}
		}, 1L, 1L);

		setupMetrics();
	}

	@Override
	public void onDisable() {
		instance = null;
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

	public boolean isProtocolLibEnabled() {
		return protocolLibEnabled;
	}

	public static LandslidePlugin getInstance() {
		return instance;
	}

	private void setupWorldGuard() {
		Plugin plugin = getServer().getPluginManager().getPlugin("WorldGuard");

		if (plugin != null && (plugin instanceof WorldGuardPlugin)) {
			Debugger.getInstance().debug("WorldGuard detected");
			worldGuardPlugin =  (WorldGuardPlugin) plugin;
		}
	}

	private void setupProtocolLib() {
		Plugin pLib = getServer().getPluginManager().getPlugin("ProtocolLib");
		if (pLib != null && pLib instanceof ProtocolLibrary && pLib.isEnabled()) {
			protocolLibEnabled = true;
			Debugger.getInstance().debug("Hooked ProtocolLib v" + pLib.getDescription().getVersion());
		}
	}

	private void handleSnowAccumulation() {
		//		long now = System.nanoTime();
		boolean snowSmoothing = getConfig().getBoolean("snow.smoothing");
		boolean meltAway = getConfig().getBoolean("snow.melt_away");
		boolean wgChecks = isWorldGuardAvailable() && getSlideManager().isWorldGuardEnabled();
		int meltLightLevel = getConfig().getInt("snow.melt_light_level");

		for (World w : Bukkit.getWorlds()) {
			int limit = w.hasStorm() ? getPerWorldConfig().getSnowFormChance(w) : getPerWorldConfig().getSnowMeltChance(w);
			if (limit <= 0) {
				return;
			}
			limit = (256 * limit) / 100; // 256 blocks per chunk layer
			int modifier = w.hasStorm() ? 1 : - 1;
			for (Chunk c : w.getLoadedChunks()) {
				for (int i = 0; i < limit; i++) {
					int x = getRandom().nextInt(16);
					int z = getRandom().nextInt(16);
					Block block = w.getHighestBlockAt(c.getX() * 16 + x, c.getZ() * 16 + z);
					Block below = block.getRelative(BlockFace.DOWN);
					if (block.getTemperature() < 0.1 && (modifier > 0 || block.getLightLevel() > meltLightLevel)) {
						if (wgChecks) {
							StateFlag flag = modifier > 0 ? DefaultFlag.SNOW_FALL : DefaultFlag.SNOW_MELT;
							if (!WGBukkit.getRegionManager(w).getApplicableRegions(block.getLocation()).allows(flag)) {
								continue;
							}
						}
						if (block.getType() == Material.SNOW) {
							if (snowSmoothing) {
								for (BlockFace face: horizontalFaces) {
									Block neighbour = block.getRelative(face);
									if (neighbour.getType() == Material.SNOW || neighbour.getType() == Material.AIR && neighbour.getRelative(BlockFace.DOWN).getType().isSolid()) {
										if (modifier > 0 && block.getData() - neighbour.getData() >= 2) {
											block = neighbour;
											break;
										} else if (modifier < 0 && block.getData() - neighbour.getData() <= -2) {
											block = neighbour;
											break;
										}
									}
								}
							}
							int newData = block.getData() + modifier;
							if (newData >= 7) {
								block.setTypeIdAndData(Material.SNOW_BLOCK.getId(), (byte)0, true);
							} else if (newData >= 0) {
								block.setData((byte) newData);
							} else if (meltAway || below.getType() == Material.SNOW_BLOCK) {
								block.setTypeIdAndData(Material.AIR.getId(), (byte)0, true);
							}
						} else if (block.getType() == Material.SNOW_BLOCK && modifier < 0) {
							block.setTypeIdAndData(Material.SNOW.getId(), (byte)6, true);
						} else if (block.getType() == Material.AIR && modifier < 0 && below.getType() == Material.SNOW_BLOCK) {
							below.setTypeIdAndData(Material.SNOW.getId(), (byte)6, true);
						}
					}
				}
			}
		}
		//		System.out.println("snow processed in " + (System.nanoTime() - now) + " ns");
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

	public PerWorldConfiguration getPerWorldConfig() {
		return perWorldConfig;
	}

	public boolean isWorldGuardAvailable() {
		return worldGuardPlugin != null && worldGuardPlugin.isEnabled();
	}

	public WorldGuardPlugin getWorldGuardPlugin() {
		return worldGuardPlugin;
	}

	public void processConfig() {
		MiscUtil.setColouredConsole(getConfig().getBoolean("coloured_console"));

		slideManager.setMaxSlidesPerTick(getConfig().getInt("max_slides_per_tick", 20));
		slideManager.setMaxSlidesTotal(getConfig().getInt("max_slides_total", 200));

		if (isWorldGuardAvailable()) {
			slideManager.setWorldGuardEnabled(getConfig().getBoolean("worldguard.enabled"));
			slideManager.setWorldGuardFlag(getConfig().getString("worldguard.use_flag"));
		}

		snowInterval = getConfig().getInt("snow.check_interval") * 20;

		slideManager.setBracingMaterials(getConfig().getStringList("bracing_materials"));
		slideManager.setBracingDistance(getConfig().getInt("bracing_distance"));
		slideManager.setFullBracingScan(getConfig().getBoolean("full_bracing_scan"));
		slideManager.setStickyPistonsRetracted(getConfig().getBoolean("sticky_pistons.retracted"));
		slideManager.setStickyPistonsExtended(getConfig().getBoolean("sticky_pistons.extended"));
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

	private static final Pattern pctPat = Pattern.compile("(slide_chance\\.|drop_chance\\.|cliff_stability|explode_effect_chance)");

	@Override
	public void onConfigurationValidate(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
		if (key.startsWith("worldguard.") && !isWorldGuardAvailable()) {
			throw new DHUtilsException("WorldGuard plugin is not enabled");
		}

		if (newVal != null && pctPat.matcher(key).find()) { // key.contains("slide_chance.")  || key.contains("cliff_stability") || key.contains("explode_effect_chance"))) {
			int pct = (Integer) newVal;
			DHValidate.isTrue(pct >= 0 && pct <= 100, "Value must be a percentage (0-100 inclusive)");
		} else if (key.equals("bracing_materials")) {
			for (String s : (List<String>) newVal) {
				if (!s.startsWith("-") && Material.matchMaterial(s) == null) {
					throw new DHUtilsException("Invalid material: " + s);
				}
			}
		} else if (key.equals("bracing_distance")) {
			DHValidate.isTrue((Integer) newVal >= 0, "Value must be >= 0");
		} else if (key.startsWith("transform.")) {
			String s = key.substring(key.indexOf('.') + 1);
			Material from = Material.matchMaterial(s);
			DHValidate.notNull(from, "Invalid material: " + s);
			Material to = Material.matchMaterial((String) newVal);
			DHValidate.notNull(to, "Invalid material: " + s);
		} else if (key.equals("worldguard.use_flag")) {
			validateWorldGuardFlag((String) newVal);
		}
	}

	@Override
	public void onConfigurationChanged(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
		if (key.equals("max_slides_per_tick")) {
			slideManager.setMaxSlidesPerTick((Integer) newVal);
		} else if (key.equals("max_slides_total")) {
			slideManager.setMaxSlidesTotal((Integer) newVal);
		} else if (key.equals("debug_level")) {
			Debugger dbg = Debugger.getInstance();
			dbg.setLevel((Integer) newVal);
			if (dbg.getLevel() > 0) {
				dbg.setTarget(getServer().getConsoleSender());
			} else {
				dbg.setTarget(null);
			}
		} else if (key.equals("coloured_console")) {
			MiscUtil.setColouredConsole((Boolean) newVal);
		} else if (key.equals("worldguard.enabled")) {
			slideManager.setWorldGuardEnabled((Boolean) newVal);
			slideManager.setWorldGuardFlag(getConfig().getString("worldguard.use_flag"));
		} else if (key.equals("worldguard.use_flag")) {
			slideManager.setWorldGuardFlag((String) newVal);
		} else if (key.equals("snow.check_interval")) {
			snowInterval = (Integer) newVal * 20;
		} else if (key.equals("bracing_materials")) {
			slideManager.setBracingMaterials((List<String>) newVal);
		} else if (key.equals("bracing_distance")) {
			slideManager.setBracingDistance((Integer) newVal);
		} else if (key.equals("full_bracing_scan")) {
			slideManager.setFullBracingScan((Boolean) newVal);
		} else if (key.equals("sticky_pistons.retracted")) {
			slideManager.setStickyPistonsRetracted((Boolean) newVal);
		} else if (key.equals("sticky_pistons.extended")) {
			slideManager.setStickyPistonsExtended((Boolean) newVal);
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
