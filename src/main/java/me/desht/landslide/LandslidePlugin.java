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

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import me.desht.dhutils.ConfigurationListener;
import me.desht.dhutils.ConfigurationManager;
import me.desht.dhutils.DHUtilsException;
import me.desht.dhutils.LogUtils;
import me.desht.dhutils.MiscUtil;
import me.desht.dhutils.commands.CommandManager;
import me.desht.landslide.commands.GetcfgCommand;
import me.desht.landslide.commands.ReloadCommand;
import me.desht.landslide.commands.SetcfgCommand;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import com.avaje.ebean.LogLevel;

public class LandslidePlugin extends JavaPlugin implements Listener, ConfigurationListener {

	private static final BlockFace[] faceChecks = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };

	private final SlideManager slideManager = new SlideManager();
	private final CommandManager cmds = new CommandManager(this);

	private ConfigurationManager configManager;

	@Override
	public void onDisable() {
	}

	@Override
	public void onEnable() {
		LogUtils.init(this);

		configManager = new ConfigurationManager(this, this);

		MiscUtil.init(this);
		MiscUtil.setColouredConsole(getConfig().getBoolean("coloured_console"));

		cmds.registerCommand(new ReloadCommand());
		cmds.registerCommand(new GetcfgCommand());
		cmds.registerCommand(new SetcfgCommand());

		processConfig();

		PluginManager pm = this.getServer().getPluginManager();
		pm.registerEvents(this, this);

		getServer().getScheduler().runTaskTimer(this, new Runnable() {
			@Override
			public void run() {
				slideManager.tick();
			}
		}, 1L, 1L);
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

	public ConfigurationManager getConfigManager() {
		return configManager;
	}

	public void processConfig() {
		String level = getConfig().getString("log_level");
		try {
			LogUtils.setLogLevel(level);
		} catch (IllegalArgumentException e) {
			LogUtils.warning("invalid log level " + level + " - ignored");
		}

		slideManager.setMaxSlidesPerTick(getConfig().getInt("max_slides_per_tick", 20));
		slideManager.setMaxSlidesTotal(getConfig().getInt("max_slides_total", 200));
		slideManager.clearAffectedWorlds();
		for (String worldName : getConfig().getStringList("slidy_worlds")) {
			slideManager.setAffectedWorld(worldName);
		}
		slideManager.resetSlideChances();
		for (String matName : getConfig().getConfigurationSection("slide_chance").getKeys(false)) {
			slideManager.setSlideChance(matName, getConfig().getInt("slide_chance." + matName));
		}
		slideManager.setCliffStability(getConfig().getInt("cliff_stability"));
	}

	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		if (slideManager.isWorldAffected(event.getBlock().getWorld().getName())) {
			checkForSlide(event.getBlock());
		}
	}

	@EventHandler
	public void onBlockLanded(EntityChangeBlockEvent event) {
		if (event.getEntity() instanceof FallingBlock) {
			if (slideManager.isWorldAffected(event.getBlock().getWorld().getName())) {
				FallingBlock fb = (FallingBlock) event.getEntity();
				LogUtils.fine("falling block landed! " + fb.getMaterial() + " -> " + event.getBlock());
				if (checkForSlide(event.getBlock(), event.getTo(), event.getData(), true)) {
					event.setCancelled(true);
				}
			}
		}
	}

	@EventHandler
	public void onBlockPhysics(BlockPhysicsEvent event) {
		if (slideManager.isWorldAffected(event.getBlock().getWorld().getName())) {
			checkForSlide(event.getBlock());
		}
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		if (slideManager.isWorldAffected(event.getBlock().getWorld().getName())) {
			Block above = event.getBlock().getRelative(BlockFace.UP);
			List<Block> l = new ArrayList<Block>();
			List<BlockFace> f = new ArrayList<BlockFace>();
			for (BlockFace face : faceChecks) {
				Block b1 = above.getRelative(face);
				if (slideManager.isSlidy(b1.getType())) {
					l.add(b1);
					f.add(face.getOppositeFace());
				}
			}
			switch (l.size()) {
			case 0:
				break;
			case 1:
				slideManager.scheduleBlockSlide(l.get(0), f.get(0));
				break;
			default:
				int idx = new Random().nextInt(l.size());
				slideManager.scheduleBlockSlide(l.get(idx), f.get(idx));
				break;
			}
		}
	}

	private boolean checkForSlide(Block block, Material mat, byte data, boolean immediate) {
		if (slideManager.isSlidy(mat)) {
			BlockFace face = slideManager.wouldSlide(block);
			if (face != null) {
				return slideManager.scheduleBlockSlide(block, face, mat, data, immediate);
			}
		}
		return false;
	}

	private boolean checkForSlide(Block block) {
		return checkForSlide(block, block.getType(), block.getData(), false);
	}

	@Override
	public void onConfigurationValidate(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
		if (key.startsWith("slide_chance.") || key.equals("cliff_stability")) {
			int pct = (Integer) newVal;
			if (pct < 0 || pct > 100) {
				throw new DHUtilsException("Value must be percentage (0-100 inclusive)");
			}
		} else if (key.equals("log_level")) {
			try {
				LogLevel.valueOf(newVal.toString());
			} catch (IllegalArgumentException e) {
				throw new DHUtilsException(e.getMessage());
			}
		}
	}

	@Override
	public void onConfigurationChanged(ConfigurationManager configurationManager, String key, Object oldVal, Object newVal) {
		if (key.equals("max_slides_per_tick")) {
			slideManager.setMaxSlidesPerTick((Integer) newVal);
		} else if (key.equals("max_slides_total")) {
			slideManager.setMaxSlidesTotal((Integer) newVal);
		} else if (key.equals("slidy_worlds")) {
			slideManager.clearAffectedWorlds();
			for (String worldName : getConfig().getStringList("slidy_worlds")) {
				slideManager.setAffectedWorld(worldName);
			}
		} else if (key.startsWith("slide_chance.")) {
			String matName = key.substring(key.indexOf('.') + 1);
			slideManager.setSlideChance(matName, (Integer) newVal);
		} else if (key.equals("cliff_stability")) {
			slideManager.setCliffStability((Integer) newVal);
		} else if (key.equals("log_level")) {
			LogUtils.setLogLevel(newVal.toString());
		}
	}
}
