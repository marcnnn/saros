package saros.server.console;

import static java.util.stream.Collectors.partitioningBy;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import org.apache.log4j.Logger;
import saros.net.util.XMPPUtils;
import saros.net.xmpp.JID;
import saros.session.SarosMultiSessionManager;

public class InviteCommand extends ConsoleCommand {
  private static final Logger log = Logger.getLogger(InviteCommand.class);
  private final SarosMultiSessionManager sessionManager;

  public InviteCommand(SarosMultiSessionManager sessionManager, ServerConsole console) {
    this.sessionManager = sessionManager;
    console.registerCommand(this);
  }

  @Override
  public String identifier() {
    return "invite";
  }

  @Override
  public int minArgument() {
    return 2;
  }

  @Override
  public String help() {
    return "invite <sessionID | new> <JID>... - Invite users to session";
  }

  @Override
  public void execute(List<String> args, PrintStream out) {
    String sessionID = args.get(0);
    if (sessionID.equals("new")) {
      sessionID = sessionManager.startSession(new HashSet<>());
    }

    args = args.subList(1, args.size());

    try {
      Map<Boolean, List<JID>> jids =
          args.stream().map(JID::new).collect(partitioningBy(XMPPUtils::validateJID));

      for (JID jid : jids.get(false)) {
        log.warn("Invalid JID skipped: " + jid);
      }

      sessionManager.inviteToSession(sessionID, jids.get(true), "Invitation by server command");
      for (JID jid : jids.get(true)) {
        sessionManager.startSessionSharingReferencePoints(sessionID, jid);
      }
    } catch (Exception e) {
      log.error("Error inviting users", e);
    }
  }
}
