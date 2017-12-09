package cl.netgamer.worldportalsng;

import org.bukkit.Bukkit;
import org.bukkit.Effect;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

class Portal
{
	// now name is stored in an external array, anyway it is just to print list names
	
	private Location location;
	private String name;
	private Portal[] destinations = new Portal[4];
	
	
	Portal(String name, Location centerLocation, float facing)
	{
		this.name = name;
		location = centerLocation.clone();
		location.setYaw(facing);
	}
	
	
	String getName()
	{
		return name;
	}
	
	void setDestinations(Portal[] destinations)
	{
		this.destinations = destinations;
	}
	
	
	void resetDestination(int direction)
	{
		destinations[direction] = null;
	}
	
	
	Portal getDestination(int direction)
	{
		return destinations[direction];
	}
	
	
	Portal[] getDestinations()
	{
		return destinations;
	}
	
	
	Location getLocation()
	{
		return location;
	}
	
	
	String printLocation(boolean includeWorld)
	{
		return
			(includeWorld ? ""+Bukkit.getWorlds().indexOf(location.getWorld()) : "")+
			(location.getBlockX()<0?"":"+")+location.getBlockX()+
			(location.getBlockY()<0?"":"+")+location.getBlockY()+
			(location.getBlockZ()<0?"":"+")+location.getBlockZ();
	}
	
	
	// debug
	public String toString()
	{
		String dest = "";
		for (Portal d : destinations)
			dest += (d == null) ? ",-" : ","+d.getName();
		return "\n"+name+","+printLocation(true)+"+"+(int)location.getYaw()+",{"+dest.substring(1)+"}";
	}
	
	
	// fast horizontal distance
	// https://math.stackexchange.com/questions/2533022/fast-approximated-hypotenuse-without-squared-root
	int getHDistanceTo(int index)
	{
		Portal destination = destinations[index];
		if (destination == null)
			return -1;
		
		double deltaX = Math.abs(location.getX() - destination.getLocation().getX());
		double deltaZ = Math.abs(location.getZ() - destination.getLocation().getZ());
		if (deltaZ > deltaX)
			deltaX = deltaX+deltaZ-(deltaZ=deltaX); // swap values
		double proportionalCathetus = deltaZ/deltaX;
		return (int) (deltaX*(1 + 0.43*proportionalCathetus*proportionalCathetus));
	}
	
	
	void teleport(Player player)
	{
		// teleport player
		Location landPoint = this.location.clone().add(0.5D, 1.0D, 0.5D);
		if (!landPoint.getChunk().isLoaded())
			landPoint.getChunk().load();
		player.teleport(landPoint);
		
		// highlight the event playing some nice effects
		landPoint.getWorld().playEffect(landPoint, Effect.EXTINGUISH, 0);
		if (!player.hasPotionEffect(PotionEffectType.NIGHT_VISION))
			player.addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, 10, 1));
	}
	
	

	
}
