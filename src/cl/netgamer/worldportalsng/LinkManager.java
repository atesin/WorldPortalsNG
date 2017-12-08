package cl.netgamer.worldportalsng;

import java.util.Collection;

import org.bukkit.Location;

class LinkManager
{
	
	LinkManager(Collection<Portal> portals)
	{
		//updateDestinations(portals);
	}
	
	
	// i didnt find any other better and reliable way that doing in exponential time
	// if you found another one please tell me so
	/*
		for each {} search destinations on {}           (0 iterations with nested 0 iterations = 0^2)
		for each {A} search destinations on {A}         (1 iterations with nested 1 iterations = 1^2)
		for each {A,B} search destinations on {A,B}     (2 iterations with nested 2 iterations = 2^2)
		for each {A,B,C} search destinations on {A,B,C} (3 iterations with nested 3 iterations = 3^2)
		...
	*/
	void updateDestinations(Collection<Portal> portals)
	{
		for (Portal portal : portals)
			portal.setDestinations(searchPortalDestinations(portal, portals));
	}
	
	
	// get an array of the 4 nearest portals (z,-x,-z,x = SWNE), relative to portal, between portals
	Portal[] searchPortalDestinations(Portal portal, Collection<Portal> portals)
	{
		Portal[] destinations = new Portal[4];
		Location from = portal.getLocation(), to;
		int[] sqDistances = new int[] {-1, -1, -1, -1};
		int fromX = from.getBlockX(), fromZ = from.getBlockZ(), direction, deltaX, deltaZ, sqDistance;
		
		for (Portal destination : portals)
		{
			// skip self destination
			if (destination == portal)
				continue;
			
			// get the direction where destination is facing, skip locations from different worlds
			to = destination.getLocation();
			direction = getDirection(from, to);
			if (direction < 0)
				continue;
			
			// calculate new squared distance to compare with saved
			deltaX = fromX - to.getBlockX();
			deltaZ = fromZ - to.getBlockZ();
			sqDistance = deltaX*deltaX + deltaZ*deltaZ;
			
			// overwrite destination and distance if closer than previous or undefined
			if (sqDistance < sqDistances[direction] || sqDistances[direction] < 0)
			{
				destinations[direction] = destination;
				sqDistances[direction] = sqDistance;
			}
		}
		return destinations;
	}
	
	
	// @return 0,1,2,3 if destiny are to south, west, north or east, or -1 if is in another world
	int getDirection(Location from, Location to)
	{
		/*
		coordinates are like if you had a hanging/standing board to your north and drawn the axes on it
		X axis is horizontal, positive to the right; Y is vertical, positive to the top
		Z axis is like if were projected perpendicular to the board, positive to you (south)
		so the horizontal plane is XZ, and yaw angles are measured clockwise from south
		
		 \ N 180 /
		  \ -z  /
		   \   /
		W-x \P/ x E
		90   X  270
		    /z\
		   /S 0\
		
		facings = yaw/90 = 0, 1, 2, 3 = south, west, north, east = z, -x, -z, x
		*/
		
		if (from.getWorld() != to.getWorld())
			return -1;
		
		int deltaX = to.getBlockX() - from.getBlockX();
		int deltaZ = to.getBlockZ() - from.getBlockZ();
		
		// can be south or west
		if (deltaX < deltaZ)
			if (deltaX > -deltaZ)
				return 0;
			else
				return 1;
		// can be east or north
		else
			if (deltaX > -deltaZ)
				return 3;
			else
				return 2;
	}
	
}
