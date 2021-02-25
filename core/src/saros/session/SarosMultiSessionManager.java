package saros.session;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import org.apache.log4j.Level;
import org.apache.log4j.Logger;
import saros.annotations.Component;
import saros.communication.connection.ConnectionHandler;
import saros.communication.connection.IConnectionStateListener;
import saros.context.IContainerContext;
import saros.filesystem.IReferencePoint;
import saros.negotiation.NegotiationTools.CancelOption;
import saros.negotiation.ResourceNegotiation;
import saros.negotiation.ResourceNegotiationCollector;
import saros.negotiation.ResourceNegotiationData;
import saros.negotiation.ResourceNegotiationFactory;
import saros.negotiation.SessionNegotiation;
import saros.negotiation.SessionNegotiationFactory;
import saros.negotiation.hooks.ISessionNegotiationHook;
import saros.negotiation.hooks.SessionNegotiationHookManager;
import saros.net.ConnectionState;
import saros.net.IReceiver;
import saros.net.ITransmitter;
import saros.net.xmpp.JID;
import saros.preferences.IPreferenceStore;
import saros.preferences.PreferenceStore;
import saros.session.internal.SarosSession;
import saros.util.StackTrace;

/**
 * The SessionManager is responsible for initiating new Saros sessions and for reacting to
 * invitations. The user can be only part of one session at most. This is a new version of the
 */
@Component(module = "core")
public class SarosMultiSessionManager implements ISarosSessionManager {

  /**
   * @JTourBusStop 6, Architecture Overview, Invitation Management:
   *
   * <p>While Activities are used to keep a running session consistent, we use MESSAGES whenever the
   * Session itself is modified. This means adding users or reference points to the session.
   *
   * <p>The Invitation Process is managed by the "Invitation Management"-Component. This class is
   * the main entrance point of this Component. During the invitation Process, the Network Layer is
   * used to send MESSAGES between the host and the invitees and the Session Management is informed
   * about joined users and added reference points.
   *
   * <p>For more information about the Invitation Process see the "Invitation Process"-Tour.
   */
  private static final Logger log = Logger.getLogger(SarosMultiSessionManager.class.getName());

  private static final Random SESSION_ID_GENERATOR = new Random();

  private static final long LOCK_TIMEOUT = 10000L;

  private static final long NEGOTIATION_TIMEOUT = 10000L;

  private volatile SarosSession session; // change to list
  private volatile Set<ISarosSession> sessions = new HashSet<ISarosSession>();
  private volatile Map<String, SarosSessionHolder> holderHashMap = new HashMap<>();

  private volatile ResourceNegotiationFactory resourceNegotiationFactory;

  private final IContainerContext context;

  private final SessionNegotiationFactory sessionNegotiationFactory;

  private final NegotiationPacketListener negotiationPacketLister;

  private final SessionNegotiationHookManager hookManager;

  private final SessionNegotiationObservable currentSessionNegotiations;

  private final ResourceNegotiationObservable currentResourceNegotiations;

  private final ResourceNegotiationCollector nextResourceNegotiation =
      new ResourceNegotiationCollector();
  private Thread nextResourceNegotiationWorker;

  private final ConnectionHandler connectionHandler;

  private final List<ISessionLifecycleListener> sessionLifecycleListeners =
      new CopyOnWriteArrayList<ISessionLifecycleListener>();

  private final Lock startStopSessionLock = new ReentrantLock();

  private volatile boolean sessionStartup = false;

  private volatile boolean sessionShutdown = false;

  private volatile INegotiationHandler negotiationHandler;

  private final IConnectionStateListener connectionListener =
      (state, error) -> {
        if (state == ConnectionState.DISCONNECTING) {
          stopSession(SessionEndReason.CONNECTION_LOST);
        }
      };

  public SarosMultiSessionManager(
      IContainerContext context,
      SessionNegotiationFactory sessionNegotiationFactory,
      SessionNegotiationHookManager hookManager,
      ConnectionHandler connectionHandler,
      ITransmitter transmitter,
      IReceiver receiver) {

    log.setLevel(Level.ALL);
    this.context = context;
    this.connectionHandler = connectionHandler;
    this.currentSessionNegotiations = new SessionNegotiationObservable();
    this.currentResourceNegotiations = new ResourceNegotiationObservable();
    this.connectionHandler.addConnectionStateListener(connectionListener);

    this.sessionNegotiationFactory = sessionNegotiationFactory;
    this.hookManager = hookManager;

    this.negotiationPacketLister =
        new NegotiationPacketListener(
            this, currentSessionNegotiations, currentResourceNegotiations, transmitter, receiver);
  }

  @Override
  public void setNegotiationHandler(INegotiationHandler handler) {
    negotiationHandler = handler;
  }

  /**
   * @JTourBusStop 3, Invitation Process:
   *
   * <p>This class manages the current Saros session.
   *
   * <p>Saros makes a distinction between a session and a shared reference point. A session is an
   * on-line collaboration between users which allows users to carry out activities. The main
   * activity is to share reference points. Hence, before you share a reference point, a session has
   * to be started and all users added to it.
   *
   * <p>(At the moment, this separation is invisible to the user. They must share a reference point
   * in order to start a session.)
   *
   * @return
   */
  @Override
  public String startSession(final Set<IReferencePoint> referencePoints) {

    /*
     * FIXME split the logic, start a session without anything and then add
     * resources !
     */
    try {
      if (!startStopSessionLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
        log.warn(
            "could not start a new session because another operation still tries to start or stop a session");
        return null;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return null;
    }

    try {

      if (sessionStartup) {
        log.warn("recursive execution detected, ignoring session start request", new StackTrace());
        return null;
      }

      if (negotiationPacketLister.isRejectingSessionNegotiationsRequests()) {
        log.warn("starting session while another session invitation is pending");
      }

      sessionStartup = true;

      final String sessionID = String.valueOf(SESSION_ID_GENERATOR.nextInt(Integer.MAX_VALUE));

      negotiationPacketLister.setRejectSessionNegotiationRequests(true);

      JID localUserJID = connectionHandler.getLocalJID();

      IPreferenceStore hostProperties = new PreferenceStore();
      if (hookManager != null) {
        for (ISessionNegotiationHook hook : hookManager.getHooks()) {
          hook.setInitialHostPreferences(hostProperties);
        }
      }

      session = new SarosSession(sessionID, localUserJID, hostProperties, context);
      sessions.add(session);

      sessionStarting(session);
      session.start();
      sessionStarted(session);

      resourceNegotiationFactory = session.getComponent(ResourceNegotiationFactory.class);

      for (IReferencePoint referencePoint : referencePoints) {
        String referencePointId = String.valueOf(SESSION_ID_GENERATOR.nextInt(Integer.MAX_VALUE));

        session.addSharedReferencePoint(referencePoint, referencePointId);
      }

      log.info("session started");
    } finally {
      sessionStartup = false;
      startStopSessionLock.unlock();
    }
    return session.getID();
  }

  // FIXME offer a startSession method for the client and host !
  @Override
  public ISarosSession joinSession(
      String id, JID host, IPreferenceStore hostProperties, IPreferenceStore localProperties) {
    log.error("joinSession() should not be called in Multi-Session-Manager.", new StackTrace());
    return null;
  }

  /** @nonSWT */
  @Override
  public void stopSession(SessionEndReason reason) {
    log.error(
        "StopSession() should not be called in Multi-Session-Manager. Reason of request: "
            + reason.toString(),
        new StackTrace());
  }

  public void stopSessionByID(String sessionID, SessionEndReason reason) {
    holderHashMap.get(sessionID).stopSession(reason);
  }

  /**
   * This method and the sarosSessionObservable are dangerous to use. The session might be in the
   * process of being destroyed while you call this method. The caller needs to save the returned
   * value to a local variable and do a null check. For new code you should consider being scoped by
   * the SarosSession and get the SarosSession in the constructor.
   *
   * @deprecated Error prone method, which produces NPE if not handled correctly. Will soon get
   *     removed.
   */
  @Override
  @Deprecated
  public ISarosSession getSession() {
    log.warn("getSession is deprecated and should not be called in MultiSessionManager");
    return null;
  }

  public void sessionNegotiationRequestReceived(
      JID remoteAddress,
      String sessionID,
      String negotiationID,
      String version,
      String description) {
    holderHashMap
        .get(sessionID)
        .sessionNegotiationRequestReceived(
            remoteAddress, sessionID, negotiationID, version, description);
  }

  public ISarosSession getSessionByID(String sessionID) {
    for (ISarosSession s : sessions) {
      if (s.getID().equals(sessionID)) return s;
    }
    return null;
  }

  public final Set<ISarosSession> getSessions() {
    return sessions;
  }

  public void resourceNegotiationRequestReceived(
      JID remoteAddress,
      List<ResourceNegotiationData> resourceNegotiationData,
      String negotiationID) {
    log.warn("resourceNegotiationRequestReceived should not be called in MultiSessionManager");
  }

  @Override
  public void invite(JID toInvite, String description) {
    log.warn("unexpected use in MultiSessionManager. Use inviteToSession");
  }

  @Override
  public void invite(Collection<JID> jidsToInvite, String description) {
    log.warn("unexpected use in MultiSessionManager. Use inviteToSession");
  }

  public void inviteToSession(String sessionID, Collection<JID> jidsToInvite, String description) {
    SarosSessionHolder holder = holderHashMap.get(sessionID);

    if (holder == null) {
      log.error("Unknown Session ID");
      return;
    }
    holder.invite(jidsToInvite, description);
  }

  void registerHolder(SarosSessionHolder holder, String sessionID) {
    holderHashMap.put(sessionID, holder);
  }

  void unregisterHolder(String sessionID) {
    holderHashMap.remove(sessionID);
    sessions.remove(getSessionByID(sessionID));
  }

  /**
   * Adds reference points to an existing session.
   *
   * @param referencePoints to reference points to add
   */
  @Override
  public synchronized void addReferencePointsToSession(Set<IReferencePoint> referencePoints) {}

  public void addReferencePointsToSessionByID(
      String sessionID, Set<IReferencePoint> referencePoints) {
    holderHashMap.get(sessionID).addReferencePointsToSession(referencePoints);
  }

  @Override
  public void startSharingReferencePoints(JID user) {
    log.warn("unexpected use of startSharingReferencePoints");
  }

  public void startSessionSharingReferencePoints(String sessionID, JID user) {
    if (holderHashMap.get(sessionID) == null) {
      log.warn("No known session with ID: " + sessionID);
      return;
    }
    holderHashMap.get(sessionID).startSharingReferencePoints(user);
  }

  @Override
  public void addSessionLifecycleListener(ISessionLifecycleListener listener) {
    sessionLifecycleListeners.add(listener);
  }

  @Override
  public void removeSessionLifecycleListener(ISessionLifecycleListener listener) {
    sessionLifecycleListeners.remove(listener);
  }

  @Override
  public void sessionStarting(ISarosSession sarosSession) {
    try {
      for (ISessionLifecycleListener listener : sessionLifecycleListeners) {
        listener.sessionStarting(sarosSession);
      }
    } catch (RuntimeException e) {
      log.error("error in notifying listener of session starting: ", e);
    }
  }

  @Override
  public void sessionStarted(ISarosSession sarosSession) {
    for (ISessionLifecycleListener listener : sessionLifecycleListeners) {
      try {
        listener.sessionStarted(sarosSession);
      } catch (RuntimeException e) {
        log.error("error in notifying listener of session start: ", e);
      }
    }
  }

  private void sessionEnding(ISarosSession sarosSession) {
    for (ISessionLifecycleListener listener : sessionLifecycleListeners) {
      try {
        listener.sessionEnding(sarosSession);
      } catch (RuntimeException e) {
        log.error("error in notifying listener of session ending: ", e);
      }
    }
  }

  private void sessionEnded(ISarosSession sarosSession, SessionEndReason reason) {
    for (ISessionLifecycleListener listener : sessionLifecycleListeners) {
      try {
        listener.sessionEnded(sarosSession, reason);
      } catch (RuntimeException e) {
        log.error("error in notifying listener of session end: ", e);
      }
    }
  }

  private boolean terminateNegotiations() {

    for (SessionNegotiation negotiation : currentSessionNegotiations.list()) {
      negotiation.localCancel(null, CancelOption.NOTIFY_PEER);
    }

    for (ResourceNegotiation negotiation : currentResourceNegotiations.list())
      negotiation.localCancel(null, CancelOption.NOTIFY_PEER);

    log.trace("waiting for all session and resource negotiations to terminate");

    long startTime = System.currentTimeMillis();

    boolean terminated = false;

    while (System.currentTimeMillis() - startTime < NEGOTIATION_TIMEOUT) {
      if (currentSessionNegotiations.list().isEmpty() && currentResourceNegotiations.isEmpty()) {
        terminated = true;
        break;
      }

      try {
        Thread.sleep(100);
      } catch (InterruptedException e) {
        Thread.currentThread().interrupt();
        break;
      }
    }

    return terminated;
  }
}
