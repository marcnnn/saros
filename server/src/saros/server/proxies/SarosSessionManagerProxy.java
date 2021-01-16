package saros.server.proxies;

import java.util.Collection;
import java.util.Set;
import saros.filesystem.IReferencePoint;
import saros.net.xmpp.JID;
import saros.preferences.IPreferenceStore;
import saros.session.INegotiationHandler;
import saros.session.ISarosSession;
import saros.session.ISarosSessionManager;
import saros.session.ISessionLifecycleListener;
import saros.session.SessionEndReason;

public class SarosSessionManagerProxy implements ISarosSessionManager {
  private final ISarosSessionManager sessionManager = null;
  /**
   * @return the active session or <code>null</code> if there is no active session.
   */
  @Override
  public ISarosSession getSession() {
    System.out.println("getSession was called in SarosSessionManagerProxy");
    return sessionManager.getSession();
  }

  /**
   * Starts a new Saros session with the local user as the only participant.
   *
   * @param referencePoints the local reference points which should be shared
   */
  @Override
  public void startSession(Set<IReferencePoint> referencePoints) {
    sessionManager.startSession(referencePoints);
  }

  /**
   * Creates a DPP session. The session is NOT started!
   *
   * @param host the host of the session.
   * @return a new session.
   */
  @Override
  public ISarosSession joinSession(String id, JID host, IPreferenceStore hostProperties,
      IPreferenceStore clientProperties) {
    return sessionManager.joinSession(id,host,hostProperties,clientProperties);
  }

  /**
   * Stops the currently active session. If the local user is the host, this will close the session
   * for everybody.
   *
   * @param reason the reason why the session ended.
   */
  @Override
  public void stopSession(SessionEndReason reason) {
    sessionManager.stopSession(reason);
  }

  /**
   * Add the given session life-cycle listener.
   *
   * @param listener the listener that is to be added.
   */
  @Override
  public void addSessionLifecycleListener(ISessionLifecycleListener listener) {
    sessionManager.addSessionLifecycleListener(listener);
  }

  /**
   * Removes the given session life-cycle listener.
   *
   * @param listener the listener that is to be removed.
   */
  @Override
  public void removeSessionLifecycleListener(ISessionLifecycleListener listener) {
    sessionManager.removeSessionLifecycleListener(listener);
  }

  /**
   * Starts sharing all reference points of the current session with the given session user. This
   * should be called after the user joined the current session.
   *
   * @param user JID of the user to share reference points with
   */
  @Override
  public void startSharingReferencePoints(JID user) {
    sessionManager.startSharingReferencePoints(user);
  }

  /**
   * Invites a user to a running session. Does nothing if no session is running, the user is already
   * part of the session, or is currently joining the session.
   *
   * @param toInvite the JID of the user that is to be invited.
   */
  @Override
  public void invite(JID toInvite, String description) {
    sessionManager.invite(toInvite,description);
  }

  /**
   * Invites users to the shared reference point.
   *
   * @param jidsToInvite the JIDs of the users that should be invited.
   */
  @Override
  public void invite(Collection<JID> jidsToInvite, String description) {
    sessionManager.invite(jidsToInvite,description);
  }

  /**
   * Adds reference points to an existing session.
   *
   * @param referencePoints the reference points to add
   */
  @Override
  public void addReferencePointsToSession(Set<IReferencePoint> referencePoints) {
    sessionManager.addReferencePointsToSession(referencePoints);
  }

  /**
   * Call this before a ISarosSession is started.
   *
   * @deprecated the manager should notify its listeners not any other component
   */
  @Override
  public void sessionStarting(ISarosSession sarosSession) {
    sessionManager.sessionStarting(sarosSession);
  }

  /**
   * Call this after a ISarosSession has been started.
   *
   * @deprecated the manager should notify its listeners not any other component
   */
  @Override
  public void sessionStarted(ISarosSession sarosSession) {
    sessionManager.sessionStarted(sarosSession);
  }

  /**
   * Sets the {@link INegotiationHandler negotiation handler} that will handle incoming and outgoing
   * session and resource negotiations requests.
   *
   * @param handler a handler to handle negotiation request or <code>null</code> if requests should
   * not be handled at all.
   */
  @Override
  public void setNegotiationHandler(INegotiationHandler handler) {
    sessionManager.setNegotiationHandler(handler);
  }
}
