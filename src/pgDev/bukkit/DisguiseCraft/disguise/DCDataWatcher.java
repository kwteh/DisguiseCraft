package pgDev.bukkit.DisguiseCraft.disguise;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.logging.Level;

import pgDev.bukkit.DisguiseCraft.DisguiseCraft;

import net.minecraft.server.v1_7_R1.DataWatcher;
import net.minecraft.server.v1_7_R1.Entity;
import net.minecraft.server.v1_7_R1.WatchableObject;
import net.minecraft.util.org.apache.commons.lang3.ObjectUtils;

public class DCDataWatcher extends DataWatcher {
	static Method iMethod;
	static Field eBoolean;
	
	static {
		try {
			iMethod = DataWatcher.class.getDeclaredMethod("i", int.class);
			iMethod.setAccessible(true);
		} catch (Exception e) {
			DisguiseCraft.log.log(Level.SEVERE, "Could not find a required i(int) method in the DataWatchers",e);
		}
		
		try {
			eBoolean = DataWatcher.class.getDeclaredField("e");
			eBoolean.setAccessible(true);
		} catch (Exception e) {
			DisguiseCraft.log.log(Level.SEVERE, "Could not find a required boolean field in the DataWatchers",e);
		}
	}

	public DCDataWatcher(Entity arg0) {
		super(arg0);
	}
	
	@Override
	public void watch(int paramInt, Object paramObject) {
		WatchableObject localWatchableObject = null;
		try {
			localWatchableObject = (WatchableObject) iMethod.invoke(this, paramInt);
		} catch (Exception e) {
			DisguiseCraft.log.log(Level.SEVERE, "Error while invoking i(int) method in a DataWatcher", e);
		}
		
		if (ObjectUtils.notEqual(paramObject, localWatchableObject.b())) {
			localWatchableObject.a(paramObject);
			localWatchableObject.a(true);
			try {
				eBoolean.setBoolean(this, true);
			} catch (Exception e) {
				DisguiseCraft.log.log(Level.SEVERE, "Error while setting a boolean in a DataWatcher", e);
			}
		}
	}
}
