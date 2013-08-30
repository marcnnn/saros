/*
 * DPP - Serious Distributed Pair Programming
 * (c) Freie Universität Berlin - Fachbereich Mathematik und Informatik - 2006
 * (c) Riad Djemili - 2006
 *
 * This program is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 1, or (at your option)
 * any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program; if not, write to the Free Software
 * Foundation, Inc., 675 Mass Ave, Cambridge, MA 02139, USA.
 */
package de.fu_berlin.inf.dpp;

import de.fu_berlin.inf.dpp.net.JID;
import de.fu_berlin.inf.dpp.net.SarosNet;
import de.fu_berlin.inf.dpp.net.util.RosterUtils;
import de.fu_berlin.inf.dpp.project.ISarosSession;

/**
 * A user is a representation of a person sitting in front of an eclipse
 * instance for the use in one Saros session.
 * 
 * A user object always has the following immutable characteristics: He/she
 * belongs to a single ISarosSession, has a final favorite color, and fixed JID.
 * 
 * There is one user who is the host, all others are clients.
 * 
 * There is one local user representing the person in front of the current
 * eclipse instance, all others are remote users.
 * 
 * The public and mutable properties are the {@link User.Permission}.
 * 
 * @entityObject A user is a entity object, i.e. it can change over time.
 */
public class User {

    public enum Permission {
        WRITE_ACCESS, READONLY_ACCESS
    }

    private final ISarosSession sarosSession;

    private final JID jid;

    private volatile int colorID;

    private final int favoriteColorID;

    private Permission permission = Permission.WRITE_ACCESS;

    public User(ISarosSession sarosSession, JID jid, int colorID,
        int favoriteColorID) {
        if (sarosSession == null || jid == null)
            throw new IllegalArgumentException();
        this.sarosSession = sarosSession;
        this.jid = jid;
        this.colorID = colorID;
        this.favoriteColorID = favoriteColorID;
    }

    public JID getJID() {
        return this.jid;
    }

    /**
     * set the current user {@link User.Permission} of this user inside the
     * current project.
     * 
     * @param permission
     */
    public void setPermission(Permission permission) {
        this.permission = permission;
    }

    /**
     * Gets current project {@link User.Permission} of this user.
     * 
     * @return
     */
    public Permission getPermission() {
        return this.permission;
    }

    /**
     * Utility method to determine whether this user has
     * {@link User.Permission#WRITE_ACCESS}
     * 
     * @return <code>true</code> if this User has
     *         {@link User.Permission#WRITE_ACCESS}, <code>false</code>
     *         otherwise.
     * 
     *         This is always !{@link #hasReadOnlyAccess()}
     */
    public boolean hasWriteAccess() {
        return this.permission == Permission.WRITE_ACCESS;
    }

    /**
     * Utility method to determine whether this user has
     * {@link User.Permission#READONLY_ACCESS}
     * 
     * @return <code>true</code> if this User has
     *         {@link User.Permission#READONLY_ACCESS}, <code>false</code>
     *         otherwise.
     * 
     *         This is always !{@link #hasWriteAccess()}
     */
    public boolean hasReadOnlyAccess() {
        return this.permission == Permission.READONLY_ACCESS;
    }

    public boolean isInSarosSession() {
        return sarosSession.getUser(getJID()) != null;
    }

    @Override
    public String toString() {
        return this.jid.getName();
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result + ((jid == null) ? 0 : jid.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (obj == null)
            return false;
        if (getClass() != obj.getClass())
            return false;
        User other = (User) obj;
        if (jid == null) {
            if (other.jid != null)
                return false;
        } else if (!jid.equals(other.jid))
            return false;
        return true;
    }

    public int getColorID() {
        return this.colorID;
    }

    public int getFavoriteColorID() {
        return this.favoriteColorID;
    }

    /**
     * Returns true if this User object identifies the user which is using the
     * local Eclipse instance as opposed to the remote users in different
     * Eclipse instances.
     */
    public boolean isLocal() {
        return this.equals(sarosSession.getLocalUser());
    }

    /**
     * Returns true if this User is not the local user.
     */
    public boolean isRemote() {
        return !isLocal();
    }

    /**
     * Returns true if this user is the one that initiated the SarosSession
     * session and thus is responsible for synchronization,
     * {@link User.Permission} management,
     */
    public boolean isHost() {
        return this.equals(sarosSession.getHost());
    }

    /**
     * Returns true if this user is not the host.
     */
    public boolean isClient() {
        return !isHost();
    }

    /**
     * Returns the alias for the user (if any set) with JID in brackets,
     * Example: "Alice (alice@saros-con.imp.fu-berlin.de)"
     */

    public String getHumanReadableName() {
        return User.getHumanReadableName(null, getJID());
    }

    /**
     * Returns the alias for the user (if any set) with JID in brackets,
     * Example: "Alice (alice@saros-con.imp.fu-berlin.de)"
     * 
     * @param sarosNet
     * @param user
     * @return
     */
    public static String getHumanReadableName(SarosNet sarosNet, JID user) {
        String nickName = RosterUtils.getNickname(sarosNet, user);
        String jidBase = user.getBase();
        if (nickName != null && !nickName.equals(jidBase)) {
            jidBase = nickName + " (" + jidBase + ")";
        }
        return jidBase;
    }

    public String getShortHumanReadableName() {

        String nickName = RosterUtils.getNickname(null, getJID());

        if (nickName != null && !nickName.equals(getJID().getBase())) {
            return nickName;
        }

        return getJID().getName();
    }

    /**
     * FOR INTERNAL USE ONLY
     * 
     * @param colorID
     * @deprecated this must only be called by the component that handles color
     *             changes
     */
    @Deprecated
    public void setColorID(int colorID) {
        this.colorID = colorID;
    }
}
