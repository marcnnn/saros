package saros.server.console;

import java.io.PrintStream;
import java.util.HashSet;
import java.util.List;
import org.apache.log4j.Logger;
import saros.session.SarosMultiSessionManager;
import saros.session.SessionEndReason;

public class StopSessionCommand extends ConsoleCommand {
  private static final Logger log = Logger.getLogger(StopSessionCommand.class);
  private final SarosMultiSessionManager sessionManager;

  public StopSessionCommand(SarosMultiSessionManager sessionManager, ServerConsole console) {
    this.sessionManager = sessionManager;
    console.registerCommand(this);
  }

  @Override
  public String identifier() {
    return "stop-session";
  }

  @Override
  public int minArgument() {
    return 1;
  }

  @Override
  public String help() {
    return "stop-session - stop a specific session";
  }

  @Override
  public void execute(List<String> args, PrintStream out) {
    try{
      sessionManager.stopSessionByID(args.get(0), SessionEndReason.HOST_LEFT);
    } catch (Exception e){
      log.error("Failed to stop session" + args.get(0), e);
    }

  }
}
