package saros.server.session;

import saros.filesystem.checksum.IChecksumCache;
import saros.filesystem.checksum.NullChecksumCache;
import saros.repackaged.picocontainer.MutablePicoContainer;
import saros.server.editor.ServerEditorManager;
import saros.session.ISarosSession;
import saros.session.ISarosSessionContextFactory;
import saros.session.SarosCoreSessionContextFactory;
import saros.session.SarosSessionHolder;

/** Server implementation of the {@link ISarosSessionContextFactory} interface. */
public class ServerSessionContextFactory extends SarosCoreSessionContextFactory {

  @Override
  public final void createNonCoreComponents(ISarosSession session, MutablePicoContainer container) {
    container.addComponent(ServerEditorManager.class);
    container.addComponent(FileActivityExecutor.class);
    container.addComponent(FolderActivityExecutor.class);
    container.addComponent(TextEditActivityExecutor.class);
    container.addComponent(SarosSessionHolder.class);

    // Checksum cache support
    container.addComponent(IChecksumCache.class, NullChecksumCache.class);
  }
}
