package saros.server.console;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import org.apache.log4j.Logger;
import saros.session.SarosMultiSessionManager;

public class StartSessionCommand extends ConsoleCommand {
  private static final Logger log = Logger.getLogger(StartSessionCommand.class);
  private final SarosMultiSessionManager sessionManager;

  public StartSessionCommand(SarosMultiSessionManager sessionManager, ServerConsole console) {
    this.sessionManager = sessionManager;
    console.registerCommand(this);
  }

  @Override
  public String identifier() {
    return "start-session";
  }

  @Override
  public int minArgument() {
    return 0;
  }

  @Override
  public String help() {
    return "start-session - create a new session";
  }

  @Override
  public void execute(List<String> args, PrintStream out) {
    try {
      String sessionID = sessionManager.startSession(new HashSet<>());
      out.println("Started new session with ID: " + sessionID);
    } catch (Exception e) {
      log.error("Failed to start a new session", e);
    }
  }
}
