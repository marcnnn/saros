package saros;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import saros.communication.connection.ConnectionHandler;
import saros.context.AbstractContextLifecycle;
import saros.context.ContainerContext;
import saros.context.IContextFactory;
import saros.context.SarosEclipseContextFactory;
import saros.session.ISarosSessionManager;
import saros.session.SessionEndReason;

/**
 * Extends the {@link AbstractContextLifecycle} for an Eclipse plug-in. It contains additional
 * Eclipse specific fields and methods.
 *
 * <p>This class is a singleton.
 */
public class EclipsePluginLifecycle extends AbstractContextLifecycle {

  private static EclipsePluginLifecycle instance;

  /**
   * @param saros A reference to the Saros/E plug-in class.
   * @return the EclipsePluginLifecycle singleton instance.
   */
  public static synchronized EclipsePluginLifecycle getInstance(Saros saros) {
    if (instance == null) {
      instance = new EclipsePluginLifecycle(saros);
    }
    return instance;
  }

  private Saros saros;

  private EclipsePluginLifecycle(Saros saros) {
    this.saros = saros;
  }

  @Override
  protected Collection<IContextFactory> additionalContextFactories() {
    List<IContextFactory> nonCoreFactories = new ArrayList<IContextFactory>();

    nonCoreFactories.add(new SarosEclipseContextFactory(saros));

    return nonCoreFactories;
  }

  @Override
  protected void finalizeContext(final ContainerContext containerContext) {
    containerContext
        .getComponent(ISarosSessionManager.class)
        .stopSession(SessionEndReason.LOCAL_USER_LEFT);
    containerContext.getComponent(ConnectionHandler.class).disconnect();
  }
}
