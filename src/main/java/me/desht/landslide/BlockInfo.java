package me.desht.landslide;

import java.util.BitSet;

import org.bukkit.Material;
import org.bukkit.block.Block;

public class BlockInfo {
	private static final BitSet solidSet = new BitSet(256);

	static {
		for (int i = 0; i < 256; i++) {
			if (Material.getMaterial(i) != null && Material.getMaterial(i).isSolid()) {
				solidSet.set(i, true);
			}
		}
	}

	public static boolean isSolid(Material mat) {
		return mat.isBlock() && solidSet.get(mat.getId());
	}

	public static boolean isSolid(Block b) {
		return isSolid(b.getType());
	}
}
