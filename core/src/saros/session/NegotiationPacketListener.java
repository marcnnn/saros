package saros.session;

import java.util.List;
import org.apache.log4j.Logger;
import org.jivesoftware.smack.PacketListener;
import org.jivesoftware.smack.packet.Packet;
import org.jivesoftware.smack.packet.PacketExtension;
import saros.communication.extensions.CancelInviteExtension;
import saros.communication.extensions.CancelResourceNegotiationExtension;
import saros.communication.extensions.InvitationAcknowledgedExtension;
import saros.communication.extensions.InvitationOfferingExtension;
import saros.communication.extensions.ResourceNegotiationOfferingExtension;
import saros.negotiation.ResourceNegotiation;
import saros.negotiation.ResourceNegotiationData;
import saros.negotiation.SessionNegotiation;
import saros.net.IReceiver;
import saros.net.ITransmitter;
import saros.net.xmpp.JID;

/**
 * This class is responsible for receiving, handling, and/or forwarding specific network messages
 * (packets) to the Saros session {@linkplain SarosSessionManager manager}.
 *
 * <p><b>Restriction:</b> This class must only instantiated by the <code>SarosSessionManager</code>
 * itself.
 */
final class NegotiationPacketListener {

  private static final Logger log = Logger.getLogger(NegotiationPacketListener.class);

  private final ITransmitter transmitter;
  private final IReceiver receiver;

  private final SarosSessionManager sessionManager;

  private final SessionNegotiationObservable sessionNegotiations;
  private final ResourceNegotiationObservable resourceNegotiations;

  private boolean rejectSessionNegotiationRequests;

  // TODO maybe this should be controlled by the SessionManager itself
  private final ISessionLifecycleListener sessionLifecycleListener =
      new ISessionLifecycleListener() {

        @Override
        public void sessionStarted(final ISarosSession session) {
          receiver.addPacketListener(
              resourceNegotiationRequestListener,
              ResourceNegotiationOfferingExtension.PROVIDER.getPacketFilter(session.getID()));

          receiver.addPacketListener(
              resourceNegotiationCanceledListener,
              CancelResourceNegotiationExtension.PROVIDER.getPacketFilter(session.getID()));
        }

        @Override
        public void sessionEnded(ISarosSession session, SessionEndReason reason) {
          receiver.removePacketListener(resourceNegotiationRequestListener);
          receiver.removePacketListener(resourceNegotiationCanceledListener);
        }
      };

  /*
   * ******************** Packet Listeners START ************************
   */
  private final PacketListener sessionNegotiationCanceledListener =
      new PacketListener() {

        @Override
        public void processPacket(final Packet packet) {

          final CancelInviteExtension extension = CancelInviteExtension.PROVIDER.getPayload(packet);

          if (extension == null) {
            log.error("received malformed session negotiation packet from " + packet.getFrom());
            return;
          }

          sessionNegotiationCanceled(
              new JID(packet.getFrom()), extension.getNegotiationID(), extension.getErrorMessage());
        }
      };

  private final PacketListener sessionNegotiationRequestListener =
      new PacketListener() {

        @Override
        public void processPacket(final Packet packet) {

          final InvitationOfferingExtension extension =
              InvitationOfferingExtension.PROVIDER.getPayload(packet);

          if (extension == null) {
            log.error("received malformed session negotiation packet from " + packet.getFrom());
            return;
          }

          sessionNegotiationRequest(
              new JID(packet.getFrom()),
              extension.getNegotiationID(),
              extension.getVersion(),
              extension.getSessionID(),
              extension.getDescription());
        }
      };

  private final PacketListener resourceNegotiationCanceledListener =
      new PacketListener() {

        @Override
        public void processPacket(Packet packet) {

          final CancelResourceNegotiationExtension extension =
              CancelResourceNegotiationExtension.PROVIDER.getPayload(packet);

          if (extension == null) {
            log.error("received malformed resource negotiation packet from " + packet.getFrom());
            return;
          }

          resourceNegotiationCanceled(
              new JID(packet.getFrom()), extension.getNegotiationID(), extension.getErrorMessage());
        }
      };

  private final PacketListener resourceNegotiationRequestListener =
      new PacketListener() {

        @Override
        public void processPacket(final Packet packet) {

          final ResourceNegotiationOfferingExtension extension =
              ResourceNegotiationOfferingExtension.PROVIDER.getPayload(packet);

          if (extension == null) {
            log.error("received malformed resource negotiation packet from " + packet.getFrom());
            return;
          }

          resourceNegotiationRequest(
              new JID(packet.getFrom()),
              extension.getNegotiationID(),
              extension.getResourceNegotiationData());
        }
      };

  /*
   * ******************** Packet Listeners END*******************************
   */

  NegotiationPacketListener(
      final SarosSessionManager sessionManager,
      final SessionNegotiationObservable sessionNegotiations,
      final ResourceNegotiationObservable resourceNegotiations,
      final ITransmitter transmitter,
      final IReceiver receiver) {
    this.sessionManager = sessionManager;

    this.sessionNegotiations = sessionNegotiations;
    this.resourceNegotiations = resourceNegotiations;
    this.transmitter = transmitter;
    this.receiver = receiver;

    init();
  }

  /**
   * Allows to reject incoming session negotiation requests.
   *
   * @param reject <code>true</code> if requests should be rejected, <code>false</code> otherwise
   */
  void setRejectSessionNegotiationRequests(final boolean reject) {
    rejectSessionNegotiationRequests = reject;
  }

  /**
   * Determines if incoming session negotiations requests are currently rejected.
   *
   * @return <code>true</code> if requests are rejected, <code>false</code> otherwise
   */
  boolean isRejectingSessionNegotiationsRequests() {
    return rejectSessionNegotiationRequests;
  }

  private void init() {
    receiver.addPacketListener(
        sessionNegotiationCanceledListener, CancelInviteExtension.PROVIDER.getPacketFilter());

    receiver.addPacketListener(
        sessionNegotiationRequestListener, InvitationOfferingExtension.PROVIDER.getPacketFilter());

    sessionManager.addSessionLifecycleListener(sessionLifecycleListener);
  }

  private void sessionNegotiationCanceled(
      final JID sender, final String sessionNegotiationID, final String errorMessage) {

    final SessionNegotiation negotiation = sessionNegotiations.get(sender, sessionNegotiationID);

    if (negotiation == null) {
      log.error(
          "received session negotiation cancel from "
              + sender
              + " for a nonexisting instance with id: "
              + sessionNegotiationID);
      return;
    }

    log.error(
        sender
            + " canceled session negotiation [id="
            + sessionNegotiationID
            + ", reason="
            + errorMessage
            + "]");

    negotiation.remoteCancel(errorMessage);
  }

  private void sessionNegotiationRequest(
      final JID sender,
      final String negotiationID,
      final String remoteVersion,
      final String sessionID,
      final String description) {

    log.info(
        "received invitation from "
            + sender
            + " [negotiation id: "
            + negotiationID
            + ", "
            + "session id: "
            + sessionID
            + ", "
            + "version: "
            + remoteVersion
            + "]");

    if (rejectSessionNegotiationRequests) {
      log.info("rejecting session negotiation request with id: " + negotiationID);

      /*
       * FIXME This text should be replaced with a cancel ID. This is GUI
       * logic here.
       */
      final PacketExtension response =
          CancelInviteExtension.PROVIDER.create(
              new CancelInviteExtension(
                  negotiationID,
                  "I am already in a Saros session and so cannot accept your invitation."));

      transmitter.sendPacketExtension(sender, response);
      return;
    }

    /* *
     *
     * @JTourBusStop 6, Invitation Process:
     *
     * (3b) If the invited user (from now on referred to as "client")
     * receives an invitation (and if he is not already in a running
     * session), Saros will send an automatic response to the inviter
     * (host). Afterwards, the control is handed over to the SessionManager.
     */

    final PacketExtension response =
        InvitationAcknowledgedExtension.PROVIDER.create(
            new InvitationAcknowledgedExtension(negotiationID));

    transmitter.sendPacketExtension(sender, response);

    /*
     * SessionManager will set rejectSessionNegotiationRequests to true in
     * this call
     */
    sessionManager.sessionNegotiationRequestReceived(
        sender, sessionID, negotiationID, remoteVersion, description);
  }

  private void resourceNegotiationCanceled(
      final JID sender, final String negotiationID, final String errorMessage) {

    final ResourceNegotiation negotiation = resourceNegotiations.get(sender, negotiationID);

    if (negotiation != null) {
      log.error(
          sender
              + " canceled resource negotiation [id="
              + negotiationID
              + ", reason="
              + errorMessage
              + "]");

      negotiation.remoteCancel(errorMessage);
    } else {
      log.error(
          "received resource negotiation cancel from "
              + sender
              + " for a nonexistent instance with id: "
              + negotiationID);
    }
  }

  private void resourceNegotiationRequest(
      final JID sender,
      final String negotiationID,
      final List<ResourceNegotiationData> resourceNegotiationData) {

    log.info(
        "received resource negotiation from " + sender + " with negotiation id: " + negotiationID);

    sessionManager.resourceNegotiationRequestReceived(
        sender, resourceNegotiationData, negotiationID);
  }
}
