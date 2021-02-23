package saros.server.console;

import java.io.PrintStream;
import java.util.List;
import java.util.Set;
import org.apache.log4j.Logger;
import saros.session.ISarosSession;
import saros.session.ISarosSessionManager;
import saros.session.SarosMultiSessionManager;
import saros.session.internal.SarosSession;

public class ListSessionsCommand extends ConsoleCommand {
  private static final Logger log = Logger.getLogger(ListSessionsCommand.class);
  private final SarosMultiSessionManager sessionManager;

  public ListSessionsCommand(SarosMultiSessionManager sessionManager, ServerConsole console) {
    this.sessionManager = sessionManager;
    console.registerCommand(this);
  }

  @Override
  public String identifier() {
    return "list-sessions";
  }

  @Override
  public int minArgument() {
    return 0;
  }

  @Override
  public String help() {
    return "list-sessions - List all active sessions";
  }

  @Override
  public void execute(List<String> args, PrintStream out) {
    try {
      Set<ISarosSession> sessions = sessionManager.getSessions();
      for (ISarosSession session:  sessions){
        out.println(session.getID() + " " + session.getRemoteUsers().toString());
      }
    } catch (Exception e) {
      log.error("Error getting sessions", e);
    }
  }
}
