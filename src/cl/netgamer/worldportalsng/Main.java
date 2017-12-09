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
	boolean stopPlugin = false;
	
	
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
		if (stopPlugin)
		{
			getPluginLoader().disablePlugin(this);
			return;
		}
		links = new LinkManager(portalsByName.values());
		
		// ready to work, listen events and commands
		new Events(this);
		getCommand("portal").setExecutor(new PortalCommand(this));
		
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
	
	
	void loadPortal(String name, Location location, float facing)
	{
		portalsByName.put(name, new Portal(name, location, facing));
		namesByLocation.put(location, name);
		struc.registerPortalBlocks(location, facing);
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
		Portal start = portalsByName.get(origin), finish = portalsByName.get(destiny);
		boolean tempPortal = false;
		
		if (!origin.isEmpty())
		{
			// try origin with specified portal
			if (start == null)
				return sendMessage(sender, "portalNotFound");
		}
		else if (sender instanceof Player)
		{
			// try current location with a temporary portal on player location
			start = new Portal(fields[2], ((Player)sender).getLocation(), 0);
			start.setDestinations(links.searchPortalDestinations(start, portalsByName.values()));
			tempPortal = true;
		}
		else
			// you didnt specified origin and you don't have a location (are from console)
			return sendMessage(sender, "mustSpecifyStart");
		
		// try destiny with specified portal
		if (finish == null)
			return sendMessage(sender, "portalNotFound");
		
		// cant reach portal from differente worlds
		if (start.getLocation().getWorld() != finish.getLocation().getWorld())
			return sendMessage(sender, "differentWorlds");
		
		
		String rows = pathFind(start, finish, tempPortal);
		if (rows.isEmpty())
			rows = "#\u00A77"+fields[3];
		
		sender.sendMessage(String.format("\u00A7E"+pathTitle, start.getName(), finish.getName()));
		TablePrinter pathTable = new TablePrinter("\u00A7B", 8, 8, 15);
		pathTable.setText(rows.substring(1));
		pathTable.print(sender, headers[2]+"\t"+headers[4]+"\t"+headers[3]);
		pathTable.print(sender, 0);
		
		return true;
	}
	
	
	private String pathFind(Portal start, Portal finish, boolean tempPortal)
	{
		int direction = -1;
		if (tempPortal)
		{
			// with temporary portal just get the direction to the nearest one
			int hDistance = -1;
			for (int i = 0; i < 4 ; ++i)
				if (start.getHDistanceTo(i) < hDistance || hDistance < 0)
				{
					direction = i;
					hDistance = start.getHDistanceTo(direction);
				}
		}
		else
			// get the direction from start to finish
			direction = links.getDirection(start.getLocation(), finish.getLocation());
		
		Portal next = start.getDestination(direction);
		return "\n"+cardinal[direction]+
			"\t"+next.getName()+
			"\t"+start.getHDistanceTo(direction)+
			(next == finish ? "" : pathFind(start, finish, false));
	}
	
	
	// test if a built structure is a valid portal
	boolean testPortalCreation(Player player, Block centerBlock, String name)
	{
		// get portal facing if structure is valid
		float facing = struc.test(centerBlock, player.getLocation().getYaw());
		if (facing < 0)
			return true;
		
		// allow portals in this world?
		Location portalLocation = centerBlock.getLocation();
		if (!allowedWorlds.contains(Integer.valueOf(getServer().getWorlds().indexOf(portalLocation.getWorld()))))
			return sendMessage(player, "forbiddenWorld");
		
		// validate name
		String status = testPortalName(name);
		if (status != null)
			return sendMessage(player, status);
		
		// build portal structure if not overlapping with another, or just register blocks on portal load
		if (!struc.build(portalLocation, facing, name))
			return sendMessage(player, "portalOverlapping");
		
		// all checks passed, create and save portal
		data.savePortal(name, portalLocation, facing);
		Portal portal = new Portal(name, portalLocation, facing);
		namesByLocation.put(portalLocation, name);
		portalsByName.put(name, portal);
		links.updateDestinations(portalsByName.values());
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
		if (portalLocation == null)
			return;
			
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
		links.updateDestinations(portalsByName.values());
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
				obj.getScore("\u00A7E"+cardinal[i]+": \u00A7B"+destination.getName()).setScore(portal.getHDistanceTo(i));
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
			cardinal[facing]+": "+(destination==null?"\u00A77"+fields[3]:destination.getName())+
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
