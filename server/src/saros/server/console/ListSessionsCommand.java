package saros.server.console;

import java.io.PrintStream;
import java.util.List;
import org.apache.log4j.Logger;
import saros.session.ISarosSessionManager;

public class ListSessionsCommand extends ConsoleCommand {
  private static final Logger log = Logger.getLogger(ListSessionsCommand.class);
  private final ISarosSessionManager sessionManager;

  public ListSessionsCommand(ISarosSessionManager sessionManager, ServerConsole console) {
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
      out.println(sessionManager.getSession().getID());
    } catch (Exception e) {
      log.error("Error getting sessions", e);
    }
  }
}
