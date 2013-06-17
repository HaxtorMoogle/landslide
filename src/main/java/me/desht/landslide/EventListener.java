package me.desht.landslide;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import me.desht.dhutils.LogUtils;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;

public class EventListener implements Listener {

	private static final BlockFace[] faceChecks = { BlockFace.NORTH, BlockFace.EAST, BlockFace.SOUTH, BlockFace.WEST };

	private LandslidePlugin plugin;

	public EventListener(LandslidePlugin plugin) {
		this.plugin = plugin;
	}

	@EventHandler
	public void onBlockPlace(BlockPlaceEvent event) {
		if (plugin.getSlideManager().isWorldAffected(event.getBlock().getWorld().getName())) {
			checkForSlide(event.getBlock());
		}
	}

	@EventHandler
	public void onBlockLanded(EntityChangeBlockEvent event) {
		if (event.getEntity() instanceof FallingBlock) {
			if (plugin.getSlideManager().isWorldAffected(event.getBlock().getWorld().getName())) {
				FallingBlock fb = (FallingBlock) event.getEntity();
				LogUtils.fine("falling block landed! " + fb.getMaterial() + " -> " + event.getBlock());
				if (checkForSlide(event.getBlock(), event.getTo(), event.getData(), true)) {
					// the block continues to slide
					event.setCancelled(true);
				} else {
					if (new Random().nextInt(100) < plugin.getConfig().getInt("explode_effect_chance")) {
						fb.getWorld().createExplosion(fb.getLocation(), 0.0f);
					}
				}
			}
		}
	}

	@EventHandler
	public void onBlockPhysics(BlockPhysicsEvent event) {
		if (plugin.getSlideManager().isWorldAffected(event.getBlock().getWorld().getName())) {
			checkForSlide(event.getBlock());
		}
	}

	@EventHandler
	public void onBlockBreak(BlockBreakEvent event) {
		if (plugin.getSlideManager().isWorldAffected(event.getBlock().getWorld().getName())) {
			Block above = event.getBlock().getRelative(BlockFace.UP);
			List<Block> l = new ArrayList<Block>();
			List<BlockFace> f = new ArrayList<BlockFace>();
			for (BlockFace face : faceChecks) {
				Block b1 = above.getRelative(face);
				if (plugin.getSlideManager().isSlidy(b1.getType())) {
					l.add(b1);
					f.add(face.getOppositeFace());
				}
			}
			switch (l.size()) {
			case 0:
				break;
			case 1:
				plugin.getSlideManager().scheduleBlockSlide(l.get(0), f.get(0));
				break;
			default:
				int idx = new Random().nextInt(l.size());
				plugin.getSlideManager().scheduleBlockSlide(l.get(idx), f.get(idx));
				break;
			}
		}
	}

	private boolean checkForSlide(Block block, Material mat, byte data, boolean immediate) {
		if (plugin.getSlideManager().isSlidy(mat)) {
			BlockFace face = plugin.getSlideManager().wouldSlide(block);
			if (face != null) {
				return plugin.getSlideManager().scheduleBlockSlide(block, face, mat, data, immediate);
			}
		}
		return false;
	}

	private boolean checkForSlide(Block block) {
		return checkForSlide(block, block.getType(), block.getData(), false);
	}

}
