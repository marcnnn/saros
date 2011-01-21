package de.fu_berlin.inf.dpp.whiteboard.net;

import java.util.ArrayList;
import java.util.List;

import de.fu_berlin.inf.dpp.User.UserRole;
import de.fu_berlin.inf.dpp.util.Util;

/**
 * I use a separate class for role management to reduce interconnectivity and
 * doubled code.
 * 
 * The observer role may have to be set on session start and the driver role at
 * the end. Role changes always have to transmitted to the view in the SWT
 * thread.
 * 
 * @author jurke
 * 
 */
public class RoleChangeManager {

	public interface RoleChangeListener {
		public void roleChanged(UserRole role);
	}

	private final List<RoleChangeListener> roleChangeListeners = new ArrayList<RoleChangeListener>();

	public void addRoleChangeListener(RoleChangeListener listener) {
		roleChangeListeners.add(listener);
	}

	public void removeRoleChangeListener(RoleChangeListener listener) {
		roleChangeListeners.remove(listener);
	}

	public void roleChanged(final UserRole role) {
		Util.runSafeSWTAsync(null, new Runnable() {

			@Override
			public void run() {
				for (RoleChangeListener l : roleChangeListeners) {
					l.roleChanged(role);
				}
			}
		});

	}
}
