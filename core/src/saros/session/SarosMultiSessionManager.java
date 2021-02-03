package saros.session;

import java.util.ArrayList;
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
import saros.negotiation.AbstractIncomingResourceNegotiation;
import saros.negotiation.AbstractOutgoingResourceNegotiation;
import saros.negotiation.IncomingSessionNegotiation;
import saros.negotiation.NegotiationListener;
import saros.negotiation.NegotiationTools.CancelOption;
import saros.negotiation.OutgoingSessionNegotiation;
import saros.negotiation.ResourceNegotiation;
import saros.negotiation.ResourceNegotiationCollector;
import saros.negotiation.ResourceNegotiationData;
import saros.negotiation.ResourceNegotiationFactory;
import saros.negotiation.ResourceSharingData;
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
import saros.util.ThreadUtils;

/**
 * The SessionManager is responsible for initiating new Saros sessions and for reacting to
 * invitations. The user can be only part of one session at most.
 * This is a new version of the
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

  private final NegotiationListener negotiationListener =
      new NegotiationListener() {
        @Override
        public void negotiationTerminated(final SessionNegotiation negotiation) {
          currentSessionNegotiations.remove(negotiation);
        }

        @Override
        public void negotiationTerminated(final ResourceNegotiation negotiation) {
          currentResourceNegotiations.remove(negotiation);

          if (session != null
              && session.isHost()
              && negotiation instanceof AbstractIncomingResourceNegotiation
              && !negotiation.isCanceled()) {
            AbstractIncomingResourceNegotiation ipn =
                (AbstractIncomingResourceNegotiation) negotiation;

            ResourceSharingData resourceSharingData = new ResourceSharingData();
            for (ResourceNegotiationData resourceNegotiationData :
                ipn.getResourceNegotiationData()) {
              String referencePointId = resourceNegotiationData.getReferencePointID();
              IReferencePoint referencePoint = session.getReferencePoint(referencePointId);

              resourceSharingData.addReferencePoint(referencePoint, referencePointId);
            }

            User originUser = session.getUser(negotiation.getPeer());
            executeOutgoingResourceNegotiation(resourceSharingData, originUser);
          }

          if (currentResourceNegotiations.isEmpty()) {
            synchronized (nextResourceNegotiation) {
              nextResourceNegotiation.notifyAll();
            }
          }
        }
      };

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
   */
  @Override
  public void startSession(final Set<IReferencePoint> referencePoints) {

    /*
     * FIXME split the logic, start a session without anything and then add
     * resources !
     */
    try {
      if (!startStopSessionLock.tryLock(LOCK_TIMEOUT, TimeUnit.MILLISECONDS)) {
        log.warn(
            "could not start a new session because another operation still tries to start or stop a session");
        return;
      }
    } catch (InterruptedException e) {
      Thread.currentThread().interrupt();
      return;
    }

    try {

      if (sessionStartup) {
        log.warn("recursive execution detected, ignoring session start request", new StackTrace());
        return;
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
  }

  // FIXME offer a startSession method for the client and host !
  @Override
  public ISarosSession joinSession(
      String id, JID host, IPreferenceStore hostProperties, IPreferenceStore localProperties) {

    assert session == null;

    JID localUserJID = connectionHandler.getLocalJID();

    session = new SarosSession(id, localUserJID, host, localProperties, hostProperties, context);
    sessions.add(session);
    resourceNegotiationFactory = session.getComponent(ResourceNegotiationFactory.class);

    log.info("joined uninitialized Saros session");

    return session;
  }

  /** @nonSWT */
  @Override
  public void stopSession(SessionEndReason reason) {
    log.error("StopSession() should not be called in Multi-Session-Manager. Reason of request: "
        + reason.toString(), new StackTrace());
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

    INegotiationHandler handler = negotiationHandler;

    if (handler == null) {
      log.warn("could not accept invitation because no handler is installed");
      return;
    }

    IncomingSessionNegotiation negotiation;

    synchronized (this) {
      if (!startStopSessionLock.tryLock()) {
        log.warn("could not accept invitation because the current session is about to stop");
        return;
      }

      try {

        // should not happen
        if (negotiationPacketLister.isRejectingSessionNegotiationsRequests()) {
          log.error("could not accept invitation because there is already a pending invitation");
          return;
        }

        negotiationPacketLister.setRejectSessionNegotiationRequests(true);

        negotiation =
            sessionNegotiationFactory.newIncomingSessionNegotiation(
                remoteAddress, negotiationID, sessionID, version, this, description);

        negotiation.setNegotiationListener(negotiationListener);
        currentSessionNegotiations.add(negotiation);

      } finally {
        startStopSessionLock.unlock();
      }
    }
    handler.handleIncomingSessionNegotiation(negotiation);
  }

  public ISarosSession getSessionByID(String sessionID){
    for (ISarosSession s : sessions){
      if(s.getID().equals(sessionID))
        return s;
    }
    return null;
  }

  public final Set<ISarosSession> getSessions(){
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

  public void inviteToSession(String sessionID, Collection<JID> jidsToInvite, String description){
    SarosSessionHolder holder = holderHashMap.get(sessionID);

    if (holder == null){
      log.error("Unknown Session ID");
      return;
    }
    holder.invite(jidsToInvite, description);
  }

  void registerHolder(SarosSessionHolder holder, String sessionID){
    holderHashMap.put(sessionID, holder);
  }

  void unregisterHolder(String sessionID){
    holderHashMap.remove(sessionID);
    sessions.remove(getSessionByID(sessionID));
  }

  /**
   * Adds reference points to an existing session.
   *
   * @param referencePoints to reference points to add
   */
  @Override
  public synchronized void addReferencePointsToSession(Set<IReferencePoint> referencePoints) {
    if (referencePoints == null) {
      return;
    }

    /*
     * To prevent multiple concurrent negotiations per user. 1. Collect all
     * new mappings, 2. If active negotiations are running wait till they
     * finish (collect new mappings in the meantime), 3. Create one
     * negotiation with all collected resources.
     */

    nextResourceNegotiation.addReferencePoints(referencePoints);

    if (nextResourceNegotiationWorker != null && nextResourceNegotiationWorker.isAlive()) {
      return;
    } else if (currentResourceNegotiations.isEmpty()) {
      /* shortcut to direct handling */
      startNextResourceNegotiation();
      return;
    }

    /* else create a worker thread */
    Runnable worker =
        new Runnable() {
          @Override
          public void run() {
            synchronized (nextResourceNegotiation) {
              while (!currentResourceNegotiations.isEmpty()) {
                try {
                  nextResourceNegotiation.wait();
                } catch (InterruptedException e) {
                  Thread.currentThread().interrupt();
                  return;
                }
              }
            }

            startNextResourceNegotiation();
          }
        };
    nextResourceNegotiationWorker = ThreadUtils.runSafeAsync(log, worker);
  }

  /**
   * This method handles new resource negotiations for already invited user (not the first in the
   * process of inviting to the session).
   */
  private synchronized void startNextResourceNegotiation() {
    ISarosSession currentSession = session;

    if (currentSession == null) {
      log.warn("could not add resources because there is no active session");
      return;
    }

    /*
     * TODO: there are race conditions, USER A restricts USER B to read-only
     * while this code is executed
     */

    if (!currentSession.hasWriteAccess()) {
      log.error(
          "current local user has not enough privileges to add resources to the current session");
      return;
    }

    ResourceSharingData referencePointsToShare = new ResourceSharingData();
    Set<IReferencePoint> referencePoints = nextResourceNegotiation.getReferencePoints();

    /*
     * Put all information about which reference points and resources to share into a
     * referencePointsToShare, for passing to OutgoingResourceNegotiation. On the way, generate
     * session-wide ID's for the reference points that don't have them yet.
     */
    for (IReferencePoint referencePoint : referencePoints) {
      String referencePointId = currentSession.getReferencePointId(referencePoint);

      if (referencePointId == null) {
        referencePointId = String.valueOf(SESSION_ID_GENERATOR.nextInt(Integer.MAX_VALUE));
      }
      referencePointsToShare.addReferencePoint(referencePoint, referencePointId);

      /*
       * If this is the host, add the reference points directly to the session
       * before sending it to the other clients. (Non-hosts, on the other
       * hand, wait until the host has accepted the reference points and offers it
       * back with a second reference point negotiation.)
       *
       * Note that partial reference points are re-added even if they were already
       * registered as being part of the session. This is because their
       * lists of shared resources may have changed.
       */
      if (currentSession.isHost() && !currentSession.isShared(referencePoint)) {
        currentSession.addSharedReferencePoint(referencePoint, referencePointId);
      }
    }

    if (referencePointsToShare.isEmpty()) {
      log.warn(
          "skipping resource negotiation because no new reference points were added to the current session");
      return;
    }

    executeOutgoingResourceNegotiation(referencePointsToShare, session.getLocalUser());
  }

  private void executeOutgoingResourceNegotiation(
      ResourceSharingData resourceSharingData, User originUser) {
    INegotiationHandler handler = negotiationHandler;
    if (handler == null) {
      log.warn("could not start a resource negotiation because no handler is installed");
      return;
    }

    List<AbstractOutgoingResourceNegotiation> negotiations =
        new ArrayList<AbstractOutgoingResourceNegotiation>();

    if (!startStopSessionLock.tryLock()) {
      log.warn(
          "could not start a resource negotiation because the current session is about to stop");
      return;
    }

    ResourceNegotiationFactory currentResourceNegotiationFactory = resourceNegotiationFactory;

    if (currentResourceNegotiationFactory == null) {
      log.warn("could not start a resource negotiation as no session is running");

      return;
    }

    List<User> recipients = new ArrayList<>();
    if (session.isHost()) {
      /*
       * If we received these reference points from a non-host user previously,
       * that user already has the reference point.
       */
      for (User user : session.getRemoteUsers()) {
        if (!user.equals(originUser)) {
          recipients.add(user);
        }
      }
    } else {
      /*
       * As a non-host, we share the reference point to the host only, who
       * takes care of sharing it with all other users in the session
       * (see negotiationListener in this class).
       */
      recipients.add(session.getHost());
    }

    try {
      for (User user : recipients) {
        AbstractOutgoingResourceNegotiation negotiation =
            currentResourceNegotiationFactory.newOutgoingResourceNegotiation(
                user.getJID(), resourceSharingData, this, session);

        negotiation.setNegotiationListener(negotiationListener);
        currentResourceNegotiations.add(negotiation);
        negotiations.add(negotiation);
      }
    } finally {
      startStopSessionLock.unlock();
    }

    for (AbstractOutgoingResourceNegotiation negotiation : negotiations)
      handler.handleOutgoingResourceNegotiation(negotiation);
  }


  @Override
  public void startSharingReferencePoints(JID user) {
    log.warn("unexpected use of startSharingReferencePoints");
  }

  public void startSessionSharingReferencePoints(String sessionID, JID user){
    if(holderHashMap.get(sessionID) == null){
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