package me.desht.landslide;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import me.desht.dhutils.MiscUtil;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class SlideOTron {
	private static final String DISPLAY_PREFIX = ChatColor.YELLOW + "Slide-O-Tron: " + ChatColor.GOLD;
	private static final String POWER_PREFIX = ChatColor.ITALIC.toString();
	private static final Material WAND_MATERIAL = Material.BLAZE_ROD;

	// for parsing data out of the item meta display name
	private static final Pattern textPat = Pattern.compile(DISPLAY_PREFIX + "(.+?)" + POWER_PREFIX + " ([0-9]+)");

	public enum Mode {
		KABOOM ("Block Exploder"),
		FORCE_SLIDE ("Slide Initiator");

		private static final Map<String, Mode> map = new HashMap<String, SlideOTron.Mode>();
		static {
			for (Mode m : values()) {
				map.put(m.getText(), m);
			}
		}
		private final String text;

		private Mode(String text) {
			this.text = text;
		}

		public String getText() {
			return text;
		}

		public Mode nextMode() {
			int idx = (ordinal() + 1) % Mode.values().length;
			return Mode.values()[idx];
		}

		public static Mode getModeForText(String text) {
			return map.get(text);
		}
	}

	private Mode mode;
	private int power;

	private SlideOTron() {
		mode = Mode.KABOOM;
		power = 2;
	}

	private SlideOTron(ItemMeta meta) {
		String s = meta.getDisplayName();
		Matcher m = textPat.matcher(s);
		if (m.find() && m.groupCount() == 2) {
			mode = Mode.getModeForText(m.group(1));
			if (mode == null) {
				mode = Mode.KABOOM;
			}
			try {
				this.power = Integer.parseInt(m.group(2));
			} catch (NumberFormatException e) {
				this.power = 2;
			}
		}
	}

	/**
	 * @return the mode
	 */
	public Mode getMode() {
		return mode;
	}

	/**
	 * @param mode the mode to set
	 */
	public void setMode(Mode mode) {
		this.mode = mode;
	}

	/**
	 * @return the power
	 */
	public int getPower() {
		return power;
	}

	/**
	 * @param power the power to set
	 */
	public void setPower(int power) {
		this.power = Math.max(power, 0);
	}

	/**
	 * Get a wand item for this Slide-O-Tron.
	 *
	 * @return an ItemStack with the appropriate metadata set
	 */
	public ItemStack toItemStack(int quantity) {
		ItemStack item = new ItemStack(WAND_MATERIAL, quantity);
		ItemMeta meta = item.getItemMeta();
		meta.setDisplayName(DISPLAY_PREFIX + mode.getText() + POWER_PREFIX + " " + power);
		List<String> lore = new ArrayList<String>();
		lore.add(ChatColor.GRAY + ChatColor.ITALIC.toString() + "Left-click: use wand");
		lore.add(ChatColor.GRAY + ChatColor.ITALIC.toString() + "Shift + Left-click: change mode");
		lore.add(ChatColor.GRAY + ChatColor.ITALIC.toString() + "Right-click: increase power");
		lore.add(ChatColor.GRAY + ChatColor.ITALIC.toString() + "Shift + Right-click: decrease power");
		meta.setLore(lore);
		item.setItemMeta(meta);
		return item;
	}

	private ItemStack toItemStack() {
		return toItemStack(1);
	}

	@Override
	public String toString() {
		return "Slide-O-Tron: mode=" + mode + " power=" + power;
	}

	public static ItemStack makeWand() {
		return new SlideOTron().toItemStack();
	}

	/**
	 * Get the Slide-O-Tron that the player is holding, if any.
	 *
	 * @param player
	 * @return returns the Slide-O-Tron, or null if the player isn't holding one
	 */
	public static SlideOTron getWand(Player player) {
		ItemStack item = player.getItemInHand();
		if (item.getType() != WAND_MATERIAL) {
			return null;
		}
		ItemMeta meta = item.getItemMeta();
		if (!meta.getDisplayName().startsWith(DISPLAY_PREFIX)) {
			return null;
		}
		return new SlideOTron(meta);
	}

	private static HashSet<Byte> transparent = new HashSet<Byte>();
	static {
		transparent.add((byte)Material.STATIONARY_WATER.getId());
		transparent.add((byte)Material.AIR.getId());
	}

	public void activate(LandslidePlugin plugin, Player player) {
		Block b;
		switch (mode) {
		case KABOOM:
			b = player.getTargetBlock(transparent, 140);
			if (b != null && b.getY() != 0 && b.getType() != Material.AIR) {
				player.getWorld().createExplosion(b.getLocation(), power);
			} else {
				player.playSound(player.getLocation(), Sound.NOTE_BASS, 1.0f, 1.0f);
			}
			break;
		case FORCE_SLIDE:
			b = player.getTargetBlock(transparent, 140);
			if (b != null && b.getY() != 0 && b.getType() != Material.AIR) {
				forceSlide(plugin, player, b);
			} else {
				player.playSound(player.getLocation(), Sound.NOTE_BASS, 1.0f, 1.0f);
			}
			break;
		default:
			break;
		}
	}

	private void forceSlide(LandslidePlugin plugin, Player player, Block b) {
		int x0 = b.getX(), y0 = b.getY(), z0 = b.getZ();
		World w = b.getWorld();
		int size = Math.min(power, 20);
		int n = 0;
		for (int x = x0 - size; x <= x0 + size; x++) {
			for (int y = y0 - size; y <= y0 + size; y++) {
				for (int z = z0 - size; z <= z0 + size; z++) {
					Block b1 = w.getBlockAt(x, y, z);
					if (b1.getType() == b.getType()) {
						BlockFace face = plugin.getSlideManager().wouldSlide(b1);
						if (face != null) {
							plugin.getSlideManager().scheduleBlockSlide(b1, face, b1.getType(), b1.getData(), false);
							n++;
						}
					}
				}
			}
		}
		String s = n == 1 ? "" : "s";
		MiscUtil.statusMessage(player, "Scheduled " + n + " block" + s + " to slide");
	}
}
