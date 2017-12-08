package cl.netgamer.worldportalsng;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.inventory.ItemStack;

public class Structure
{
	
	/*
	I DON'T RECOMMEND TO USE NETHER PORTAL FRAMES BECAUSE ARE HARD TO IMPLEMENT
	AND IF SOME PLAYER ENTERS A PORTAL IN NETHER HE COULD APPEAR IN A WORLD PORTAL
	
	I DON'T RECOMMEND METADATA TO STORE INFO INSIDE BLOCKS BECAUSE IS NOT PERSISTENT
	*/
	
	
	private Map<Location, Location> portalBlocks = new HashMap<Location, Location>();
	private boolean dropSign;
	
	
	Structure(boolean dropSign)
	{
		this.dropSign = dropSign;
	}
	
	
	// check if surrounding structure matches with a portal one
	float test(Block centerBlock, float yaw)
	{
		// start matching center column
		if (centerBlock.getRelative(BlockFace.DOWN).getType() != Material.OBSIDIAN)
			return -1;
		if (centerBlock.getType() != Material.SIGN_POST)
			return -1;
		if (centerBlock.getRelative(BlockFace.UP).getType() != Material.AIR)
			return -1;
		if (centerBlock.getRelative(BlockFace.UP, 2).getType() != Material.OBSIDIAN) {
			return -1;
		}
		
		while (yaw < 0)
			yaw += 360;
		while (yaw >= 360)
			yaw -= 360;
		
		float[] facings;
		if (yaw < 45)
			facings = new float[]{180, 270};
		else if (yaw < 90)
			facings = new float[]{270, 180};
		else if (yaw < 135)
			facings = new float[]{270, 0};
		else if (yaw < 180)
			facings = new float[]{0, 270};
		else if (yaw < 225)
			facings = new float[]{0, 90};
		else if (yaw < 270)
			facings = new float[]{90, 0};
		else if (yaw < 325)
			facings = new float[]{90, 180};
		else
			facings = new float[]{180, 90};
		
		// loop possible facings, check if side columns match with a portal
		Block sideBlock, otherBlock;
		for (float facing : facings)
		{
			// find side columns
			if (facing == 0 || facing == 180)
			{
				sideBlock = centerBlock.getRelative(BlockFace.EAST);
				otherBlock = centerBlock.getRelative(BlockFace.WEST);
			}
			else
			{
				sideBlock = centerBlock.getRelative(BlockFace.NORTH);
				otherBlock = centerBlock.getRelative(BlockFace.SOUTH);
			}
			
			// check if side columns are full obsidian
			if (
				sideBlock.getType() == Material.OBSIDIAN &&
				otherBlock.getType() == Material.OBSIDIAN &&
				sideBlock.getRelative(BlockFace.UP).getType() == Material.OBSIDIAN &&
				otherBlock.getRelative(BlockFace.UP).getType() == Material.OBSIDIAN)
				return facing;
		}
		// no full obsidian side columns found
		return -1;
	}
	
	
	/** modify center column to look like an activated or deactivated portal, rely on previous checks
	 * @param portalLocation center portal location, to the height of player legs
	 * @param facing portal location yaw to check and register portal structure orientation
	 * @param name to put the label visible in an invisible entity, if null portal structure will be deactivated
	 * @return true on success, false on portal overlapping
	 */
	boolean build(Location portalLocation, float facing, String name)
	{
		if (name == null)
		{
			// build disabled center column
			Block base = portalLocation.getBlock();
			base.getRelative(BlockFace.DOWN).setType(Material.OBSIDIAN);
			base.setType(Material.AIR);
			base.getRelative(BlockFace.UP).setType(Material.AIR);
	 		
			// find and delete name label armor stands
			// REMOVE IN LATER VERSIONS: Loc2 and Loc3 are for backward compatibility
			Location labelLoc = portalLocation.clone().add(0.5, 1.1, 0.5);
			Location labelLoc2 = portalLocation.clone().add(0.5, 0.1, 0.5);
			Location labelLoc3 = portalLocation.clone().add(0.5, -0.3, 0.5);
			for (Entity e : labelLoc.getWorld().getNearbyEntities(labelLoc, 2, 2, 2))
				if (e instanceof ArmorStand)
					if (e.getLocation().equals(labelLoc) || e.getLocation().equals(labelLoc2) || e.getLocation().equals(labelLoc3))
						e.remove();
			
			// unregister portal structural blocks
			for (Iterator<Entry<Location, Location>> iter = portalBlocks.entrySet().iterator(); iter.hasNext();)
				if (iter.next().getValue() == portalLocation)
					 iter.remove();
			
			if (dropSign)
				portalLocation.getWorld().dropItem(portalLocation, new ItemStack(Material.SIGN));
		}
		else
		{
			// check portal overlapping first
			Map<Location, Location> structureLocations = findStructureLocations(portalLocation, facing);
			for (Location structureLocation : structureLocations.keySet())
				if (portalBlocks.containsKey(structureLocation))
					return false;
			
			// build enabled center column
			Block base = portalLocation.getBlock();
			base.getRelative(BlockFace.DOWN).setType(Material.SEA_LANTERN);
			base.setType(Material.STATIONARY_WATER);
			base.getRelative(BlockFace.UP).setType(Material.STATIONARY_WATER);
			
			// spawn armor stand with own name
			ArmorStand labelName = (ArmorStand)portalLocation.getWorld().spawnEntity(portalLocation.clone().add(0.5, 1.1, 0.5), EntityType.ARMOR_STAND);
			labelName.setVisible(false);
			labelName.setInvulnerable(true);
			labelName.setGravity(false);
			labelName.setMarker(true);
			labelName.setCustomName("\u00A7L"+name);
			labelName.setCustomNameVisible(true);
			
			// register portal structural blocks
			portalBlocks.putAll(structureLocations);
		}
		return true;
	}
	
	
	void registerPortalBlocks(Location portalLocation, float facing)
	{
		portalBlocks.putAll(findStructureLocations(portalLocation, facing));
	}
	
	
	Location getPortalLocation(Block block)
	{
		return portalBlocks.get(block.getLocation());
	}
	
	
	// locations of blocks forming the specified portal, passed by value (i.e. copied to the method scope)
	private Map<Location, Location> findStructureLocations(Location portalLocation, float facing)
	{	
		// collect portal blocks, start adding center column locations
		Map<Location, Location> blockLocations = new HashMap<Location, Location>();
		blockLocations.put(portalLocation.clone().add(0.0D, -1.0D, 0.0D), portalLocation);
		blockLocations.put(portalLocation, portalLocation);
		blockLocations.put(portalLocation.clone().add(0.0D, 1.0D, 0.0D), portalLocation);
		blockLocations.put(portalLocation.clone().add(0.0D, 2.0D, 0.0D), portalLocation);
		
		// now add sides columns according structure facing
		Location sideLocation;
		Location otherLocation;
		if (facing % 180 == 0)
		{
			sideLocation = portalLocation.clone().add(1.0D, 0.0D, 0.0D);
			otherLocation = portalLocation.clone().add(-1.0D, 0.0D, 0.0D);
		}
		else
		{
			sideLocation = portalLocation.clone().add(0.0D, 0.0D, 1.0D);
			otherLocation = portalLocation.clone().add(0.0D, 0.0D, -1.0D);
		}
		blockLocations.put(sideLocation, portalLocation);
		blockLocations.put(otherLocation, portalLocation);
		blockLocations.put(sideLocation.clone().add(0.0D, 1.0D, 0.0D), portalLocation);
		blockLocations.put(otherLocation.clone().add(0.0D, 1.0D, 0.0D), portalLocation);
		
		// return structure blocks locations
		
		return blockLocations;
	}
	
}
