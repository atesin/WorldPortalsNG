package cl.netgamer.worldportalsng;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockFromToEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class Events implements Listener
{
	
	private Main plugin;
	
	
	public Events(Main plugin)
	{
		this.plugin = plugin;
		plugin.getServer().getPluginManager().registerEvents(this, plugin);
	}
	
	
	// endermen does not grab portal block types so checks on they are unnecessary
	
	
	// set nailed signpost text: possible portal naming and activation
	@EventHandler
	public void onSignChange(SignChangeEvent e)
	{
		if (e.getBlock().getRelative(BlockFace.DOWN).getType() != Material.OBSIDIAN)
			return;
		
		String portalName = e.getLine(0).trim();
		if (!portalName.isEmpty())
			plugin.testPortalCreation(e.getPlayer(), e.getBlock(), portalName);
	}
	
	
	// possible portal destroying by hand
	@EventHandler
	public void onBlockBreak(BlockBreakEvent e)
	{
		Material blockType = e.getBlock().getType();
		if (blockType == Material.SEA_LANTERN || blockType == Material.OBSIDIAN || blockType == Material.STATIONARY_WATER)
			plugin.testPortalDestroy(e.getBlock());
	}
	
	
	// cancel overwriting (water) portal block attempts
	@EventHandler
	public void onBlockPlace(BlockPlaceEvent e)
	{
		// i know this is not so efficient, but i haven't found any other accurate way to do it
		// https://bukkit.org/threads/prevent-water-overwriting-by-placing-a-block-over.468005/
		if (plugin.isPortalBlock(e.getBlock()))
			e.setCancelled(true);
	}
	
	
	// possible portal destroying by explosion
	@EventHandler
	public void onEntityExplode(EntityExplodeEvent e)
	{
		Material blockType;
		for (Block destroyedBlock : e.blockList())
		{
			blockType = destroyedBlock.getType();
			if (blockType == Material.SEA_LANTERN || blockType == Material.OBSIDIAN || blockType == Material.STATIONARY_WATER)
				plugin.testPortalDestroy(destroyedBlock);
		}
	}
	
	
	// self contained waterfall (energy beam)
	@EventHandler
	public void onBlockFromTo(BlockFromToEvent e)
	{
		if (plugin.isPortalBlock(e.getBlock()))
			e.setCancelled(true);
	}
	
	
	// prevent "energy beam" (waterfall) to be grabbed with a bucket
	@EventHandler
	public void onPlayerBucketFill(PlayerBucketFillEvent e)
	{
		if (plugin.isPortalBlock(e.getBlockClicked()))
			e.setCancelled(true);
	}
	
	
	// prevent portal blocks were pushed by pistons
	@EventHandler
	public void onBlockPistonExtend(BlockPistonExtendEvent e)
	{
		for (Block pushedBlock : e.getBlocks())
			if (plugin.isPortalBlock(pushedBlock))
			{
				e.setCancelled(true);
				return;
			}
	}
	
	
	// prevent portal blocks were pulled by sticky pistons
	@EventHandler
	public void onBlockPistonRetract(BlockPistonRetractEvent e)
	{
		for (Block pulledBlock : e.getBlocks())
			if (plugin.isPortalBlock(pulledBlock))
			{
				e.setCancelled(true);
				return;
			}
	}
	
	
	// test if player stepped to another block for possible portal entering/exiting, and if selecting destination
	@EventHandler
	public void onPlayerMove(PlayerMoveEvent e)
	{
		// test if player stepped from/to another block
		Location f = e.getFrom();
		Location t = e.getTo();
		if (f.getBlockX() != t.getBlockX() || f.getBlockY() != t.getBlockY() || f.getBlockZ() != t.getBlockZ())
			plugin.testPortalWalk(e.getPlayer(), f, t);
		
		// test if player turns inside a portal
		if (plugin.isInsidePortal(e.getPlayer()))
			plugin.testYaw(e.getPlayer(), e.getFrom(), e.getTo());
		
	}
	
}
