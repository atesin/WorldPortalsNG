package cl.netgamer.worldportalsng;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.Map.Entry;

import org.bukkit.Location;
import org.bukkit.block.Block;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

import cl.netgamer.worldportalsng.Portal;
import cl.netgamer.worldportalsng.Structure;

public final class Main extends JavaPlugin
{
	
	// portal location > portal name > actual portal
	private Map<Location, String> namesByLocation = new HashMap<Location, String>();
	private Map<String, Portal> portalsByName = new TreeMap<String, Portal>(String.CASE_INSENSITIVE_ORDER);
	private Data data;
	private LinkManager links;
	private Structure struc;
	private int healthCost;
	private Set<Integer> allowedWorlds;
	private String[] cardinal;
	private String[] headers;
	private String[] fields;
	private String listTitle;
	private String pathTitle;
	private Map<String, Object> messages;
	private Map<String, BukkitTask> scheduledTeleports = new HashMap<String, BukkitTask>();
	private Map<String, BossBar> bossBars = new HashMap<String, BossBar>();
	
	
	public void onEnable()
	{
		// load configuration
		saveDefaultConfig();
		healthCost = getConfig().getInt("healthCost");
		allowedWorlds = new HashSet<Integer>(getConfig().getIntegerList("allowedWorlds"));
		cardinal = getConfig().getStringList("cardinalDirections").toArray(new String[0]);
		headers = getConfig().getStringList("headers").toArray(new String[0]);
		fields = getConfig().getStringList("fields").toArray(new String[0]);
		listTitle = getConfig().getString("listTitle");
		pathTitle = getConfig().getString("pathTitle");
		messages = getConfig().getConfigurationSection("messages").getValues(false);
		
		// load classes and portals
		struc = new Structure(getConfig().getBoolean("dropSign"));
		data = new Data(this);
		
		// construct and update portal links
		
		// CLEAN UP THE MESS IN LATER VERSIONS 
		links = new LinkManager(portalsByName.values());
		data.loadPortals();
		links.updateDestinations(portalsByName.values());
		
		// ready to work, listen events and commands
		new Events(this);
		getCommand("portal").setExecutor(new PortalCommand(this));
		
		System.out.println("- Hello Admin, this will be the only version of WorldPortalsNG");
		System.out.println("- that converts portals from previous WorldPortals plugin,");
		System.out.println("- read comments in included data.yml file for more information.");
		System.out.println("- If your portals were converted i beg you to use a later version");
		System.out.println("- which won't include the messy legacy code needed for conversion");
		System.out.println("- and will have a more polished, efficient, light and fast code.");
		System.out.println("- https://dev.bukkit.org/projects/world-portals-ng/files");
		
		// debug
		//System.out.println("Portals = "+portalsByName.values());
	}
	
	
	// wipe boss bars before reload to avoid turn orphan
	public void onDisable()
	{
		for (Player player : getServer().getOnlinePlayers())
			if (bossBars.containsKey(player.getUniqueId().toString()))
				bossBars.get(player.getUniqueId().toString()).removePlayer(player);
	}
	
	
	// send localized messages, always return true
	boolean sendMessage(CommandSender sender, String message)
	{
		message = messages.containsKey(message) ? "\u00A7E"+messages.get(message) : "\u00A7DMessage not found in conf: "+message;
		
		if (message.equals("\u00A7Ehide"))
			return true;
		
		if (sender instanceof CommandSender)
			sender.sendMessage(message);
		else
			System.out.println(message);
		return true;
	}
	
	
	// REMOVE THIS IN LATER VERSIONS
	boolean structureDisable(Location portalLocation, float facing)
	{
		return struc.build(portalLocation, facing, null);
	}
	
	
	void listPortals(CommandSender sender, Location location, int page, String filter)
	{
		String rows = "";
		for (Entry<String, Portal> entry : portalsByName.entrySet())
		{
			// skip if name was specified and dont matches
			if (!filter.isEmpty() && !entry.getKey().toLowerCase().contains(filter.toLowerCase()))
				continue;
			
			// skip if world was specified and dont matches
			if (location != null && entry.getValue().getLocation().getWorld() != location.getWorld())
				continue;
			
			rows += "\n\u00A7D"+entry.getKey()+"`\u00A7E/p "+entry.getKey()+"`/p "+entry.getKey()+"\t"+entry.getValue().printLocation(location == null);
		}
		if (rows.isEmpty())
			rows = "#\u00A77"+fields[3];
		
		TablePrinter table = new TablePrinter("\u00A7B", 8, 15);
		int pages = table.setText(rows.substring(1));
		
		TablePrinter.printRaw(sender,
			"\u00A7E"+
			String.format(listTitle, filter, page, pages)+
			"      \t\u00A7D"+
			fields[0]+
			": /p ?`\u00A7E/p ?`/p ?");
		table.print(sender, headers[0]+"\t"+headers[1]);
		table.print(sender, page);
	}
	
	
	boolean pathFind(CommandSender sender, String origin, String destiny)
	{
		Portal start = portalsByName.get(origin), finish;
		
		if (!origin.isEmpty())
		{
			// try origin with specified portal
			if (start == null)
				return sendMessage(sender, "portalNotFound");
		}
		else if (sender instanceof Player)
		{
			// try origin with a temporary portal on player location
			start = new Portal(fields[2], ((Player)sender).getLocation(), 0);
			start.setDestinations(links.searchPortalDestinations(start, portalsByName.values()));
		}
		
		// you didnt specified origin and you don't have a location (are from console)
		else
			return sendMessage(sender, "mustSpecifyStart");
		
		// try destiny with specified portal
		if ((finish = portalsByName.get(destiny)) == null)
			return sendMessage(sender, "portalNotFound");
		
		//return "--- direction --- portal --- distance ---"+pathFind(portal, destination);
		
		if (start.getLocation().getWorld() != finish.getLocation().getWorld())
			return sendMessage(sender, "differentWorlds");
		
		
		String rows = pathFind(start, finish);
		if (rows.isEmpty())
			rows = "#\u00A77"+fields[3];
		
		sender.sendMessage(String.format("\u00A7E"+pathTitle, start.getName(), finish.getName()));
		TablePrinter pathTable = new TablePrinter("\u00A7B", 8, 8, 15);
		pathTable.setText(rows.substring(1));
		pathTable.print(sender, headers[2]+"\t"+headers[4]+"\t"+headers[3]);
		pathTable.print(sender, 0);
		
		
		return true;
	}
	
	
	private String pathFind(Portal start, Portal finish)
	{
		// get relative direction from start to finish, to begin search
		Location startLocation = start.getLocation();
		int direction = links.getDirection(startLocation, finish.getLocation());
		Portal destination = start.getDestination(direction);
		
		String result = "\n"+cardinal[direction]+
						"\t"+destination.getName()+
						"\t"+start.hDistance(direction);
				
		if (destination == finish)
			return result;
		return result+pathFind(destination, finish);
	}
	
	
	// MUST REWRITE THESE 2 METHODS IN LATER VERSIONS
	// test if a built structure is a valid portal
	void testPortalCreation(Player player, Block centerBlock, String name)
	{
		// get portal facing if structure is valid
		float facing = struc.test(centerBlock, player.getLocation().getYaw());
		if (facing >= 0)
			testPortalCreation(player, centerBlock.getLocation(), facing, name, true);
	}
	
	
	// test if built or loaded structure is a valid portal
	boolean testPortalCreation(CommandSender sender, Location portalLocation, float facing, String name, boolean interactive)
	{
		// allow portals in this world?
		//Location portalLocation = centerBlock.getLocation();
		if (!allowedWorlds.contains(Integer.valueOf(getServer().getWorlds().indexOf(portalLocation.getWorld()))))
			return sendMessage(sender, "forbiddenWorld");
		
		// validate name
		String status = testPortalName(name);
		if (status != null)
			return sendMessage(sender, status);
		
		// build portal structure if not overlapping with another, or just register blocks on portal load
		if (interactive)
		{
			if (!struc.build(portalLocation, facing, name))
				return sendMessage(sender, "portalOverlapping");
			
			data.savePortal(name, portalLocation, facing);
		}
		else
			struc.registerPortalBlocks(portalLocation, facing);
		
		
		// all checks passed, save portal in conf file and create it
		
		// register portal structural blocks, already done in previous method after player events
		//if (!interactive)
		//	struc.registerPortalBlocks(portalLocation, facing);
		
		// all checks passed, create portal and cache in memory
		Portal portal = new Portal(name, portalLocation, facing);
		namesByLocation.put(portalLocation, name);
		portalsByName.put(name, portal);
		
		// update destinations, already done after plugin load
		if (interactive)
			//links.setPortalDestinations(portal, portalsByName.values(), false);
			links.updateDestinations(portalsByName.values());
		
		// debug
		//if (interactive)
		//	System.out.println("Portals = "+portalsByName.values());
		
		return true;
	}
	
	
	// return status message key, null = ok
	private String testPortalName(String name)
	{
		// skip reserved words?
		
		// check name spelling (matches() includes anchors)
		if (!name.matches("[a-zA-Z0-9-]*"))
			return "invalidName";
		
		// check name length, could had been with "[...]{1,15}" but this way can separate messages
		if (name.length() > 15)
			return "nameTooLong";
		
		// dismiss number looking names
		try
		{
			Double.parseDouble(name);
			return "looksLikeANumber";
		}
		catch (NumberFormatException e){}
		
		// ensure unique name
		if (portalsByName.containsKey(name))
			return "unavailableName";
		
		return null;
	}
	
	
	boolean isPortalBlock(Block block)
	{
		return (struc.getPortalLocation(block) != null);
	}
	
	
	// destroy a portal if valid
	void testPortalDestroy(Block destroyedBlock)
	{
		// get owner portal location if any
		Location portalLocation = struc.getPortalLocation(destroyedBlock);
		if (portalLocation != null)
			testPortalDestroy(portalLocation);
	}
	
	
	// MERGE WITH PREVIOUS METHOD IN LATER VERSIONS
	void testPortalDestroy(Location portalLocation)
	{	
		// gather info about destroyed portal
		String portalName = namesByLocation.get(portalLocation);
		Portal portal = portalsByName.get(portalName);
		
		// remove portal from file world and memory
		data.deletePortal(portal);
		struc.build(portalLocation, portal.getLocation().getYaw(), null);
		portalsByName.remove(portalName);
		namesByLocation.remove(portalLocation);
		
		// reset nearby portals destinations
		List<Portal> destinations = Arrays.asList(portal.getDestinations());
		for (int direction = 0 ; direction < 4 ; ++direction)
			if (destinations.get(direction) != null)
				destinations.get(direction).resetDestination((direction+2)%4);
		
		// update nearby portals destinations
		//for (int direction = 0 ; direction < 4 ; ++direction)
		//	links.setPortalDestinations(destinations.get(direction), destinations, false);
		links.updateDestinations(portalsByName.values());
		
		// debug
		//System.out.println("Portals = "+portalsByName.values());
	}
	
	
	// check if player exits or enters a portal
	void testPortalWalk(Player player, Location from, Location to)
	{
		// process portal exit
		from = new Location(from.getWorld(), from.getBlockX(), from.getBlockY(), from.getBlockZ());
		if (namesByLocation.containsKey(from))
			leavePortal(player, portalsByName.get(namesByLocation.get(from)));
		
		// process portal enter
		to = new Location(to.getWorld(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
		if (namesByLocation.containsKey(to))
			enterPortal(player, portalsByName.get(namesByLocation.get(to)));
	}
	
	
	// cancel scheduled teleport
	private void leavePortal(Player player, Portal portal)
	{
		// reset and delete scheduled tasks
		String playerId = player.getUniqueId().toString();
		if (bossBars.containsKey(playerId))
		{
			scheduledTeleports.get(playerId).cancel();
			scheduledTeleports.remove(playerId);
			bossBars.get(playerId).removePlayer(player);
			bossBars.remove(playerId);
		}

		// restore player status
		player.removePotionEffect(PotionEffectType.CONFUSION);
		player.getScoreboard().getObjective(player.getName()).unregister();
	}
	

	// wobble, print destinations and waits for player to pick one
	private void enterPortal(Player player, Portal portal)
	{
		player.addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, 1200, 1)); // 1200 = 1 min, 160 = 8sec?
		
		Scoreboard board = getServer().getScoreboardManager().getNewScoreboard();
	 	Objective obj = board.registerNewObjective(player.getName(), "dummy");
		obj.setDisplaySlot(DisplaySlot.SIDEBAR);
		obj.setDisplayName("\u00A7E"+fields[1]);
		
		Portal destination;
		
		for (int i = 0 ; i < 4 ; ++i)
		{
			if ((destination = portal.getDestination(i)) != null)
				// create score just by mentioning
				obj.getScore("\u00A7E"+cardinal[i]+": \u00A7B"+destination.getName()).setScore(portal.hDistance(i));
		}
		
		player.setScoreboard(board);
		
		float yaw = player.getLocation().getYaw();
		while (yaw < 0)
			yaw += 360;
		scheduleTeleport(player, portal, Math.round(yaw/90) % 4);
	}
	
	
	boolean isInsidePortal(Player player)
	{
		return (bossBars.containsKey(player.getUniqueId().toString()));
	}
	
	
	// called just when player turns inside a portal
	void testYaw(Player player, Location from, Location to)
	{
		// facing rounded 90deg horizontal regions, backwarded to match facing
		float yaw = to.getYaw();
		while (yaw < 0)
			yaw += 360;
		int facing = Math.round(yaw/90) % 4;
		
		float fromYaw = from.getYaw();
		while (fromYaw < 0)
			fromYaw += 360;
		int fromFacing = Math.round(fromYaw/90) % 4;
		
		// facing had not changed
		if (facing == fromFacing)
			return;
		
		to = new Location(to.getWorld(), to.getBlockX(), to.getBlockY(), to.getBlockZ());
		
		scheduleTeleport(player, portalsByName.get(namesByLocation.get(to)) , facing);
	}
	
	
	void scheduleTeleport(Player player, Portal portal, int facing)
	{
		String playerId = player.getUniqueId().toString();
		
		// clear previous teleport scheduled task and prepare new
		if (scheduledTeleports.containsKey(playerId))
			scheduledTeleports.get(playerId).cancel();
		
		//Portal portal = getPortalInside(player);
		Portal destination = portal.getDestinations()[facing];
		
		// format bar text
		String name = cardinal[(facing+3)%4]+" <"+
			cardinal[facing]+": "+(destination==null?"(none)":destination.getName())+
			"> "+cardinal[(facing+1)%4];
		String spacer = "                              ".substring((64 - name.length())/2);
		name = "\u00A77"+name;
		name = name.replace("<", "<\u00A7E"+spacer);
		name = name.replace(":", ":\u00A7B");
		name = name.replace(">", spacer+"\u00A77>");
		
		// create or update bossbar
		BossBar bar = bossBars.get(playerId);
		if (bar == null)
		{
			bar = getServer().createBossBar(name, BarColor.BLUE, BarStyle.SOLID);
			bar.setProgress(0.0);
			bar.addPlayer(player);
			bossBars.put(playerId, bar);
		}
		bar.setTitle(name);
		
		// schedule new teleport task
		scheduledTeleports.put(playerId, new BukkitRunnable()
		{
			@Override
			public void run()
			{
				// remember is asynchronous and anything could had changed!
				if (!player.isOnline() || player.isDead() || destination == null)
					return;
				
				// do paybacks if enough health
				if (player.getHealth() <= healthCost)
				{
					//player.getLocation().getWorld().playEffect(player.getLocation(), Effect.EXTINGUISH, 0);
					sendMessage(player, "insufficientHealth");
					player.removePotionEffect(PotionEffectType.CONFUSION);
					return;
				}
				
				// teleport player, do paybacks and remove sickness effect
				destination.teleport(player);
				player.damage(healthCost);
				player.removePotionEffect(PotionEffectType.CONFUSION);
			}
		}.runTaskLater(this, 80));
	}
	
	
	
	
}
