package saros.stf.test.editing;

import static org.junit.Assert.assertEquals;
import static saros.stf.client.tester.SarosTester.ALICE;
import static saros.stf.client.tester.SarosTester.BOB;

import org.junit.BeforeClass;
import org.junit.Test;
import saros.stf.annotation.TestLink;
import saros.stf.client.StfTestCase;
import saros.stf.client.util.Util;
import saros.stf.shared.Constants.SessionInvitationModality;

@TestLink(id = "Saros-108_add_3_new_projects_to_a_existing_session")
public class Editing3ProjectsTest extends StfTestCase {

  @BeforeClass
  public static void selectTesters() throws Exception {
    select(ALICE, BOB);
  }

  @Test
  public void testEditing3Projects() throws Exception {

    ALICE.superBot().internal().createJavaProject("foo");
    ALICE.superBot().internal().createJavaProject("foo1");
    ALICE.superBot().internal().createJavaProject("foo2");

    ALICE.superBot().internal().createFile("foo", "src/bar/HelloAlice.java", "HelloAlice");
    ALICE.superBot().internal().createFile("foo1", "src/bar/HelloBob.java", "HelloBob");
    ALICE.superBot().internal().createFile("foo2", "src/bar/HelloCarl.java", "HelloCarl");

    Util.setUpSessionWithJavaProject("foo", SessionInvitationModality.SEQUENTIALLY, ALICE, BOB);

    BOB.superBot().views().packageExplorerView().waitUntilResourceIsShared("foo");

    Util.addJavaProjectToSessionSequentially("foo1", ALICE, BOB);

    BOB.superBot().views().packageExplorerView().waitUntilResourceIsShared("foo1");

    Util.addJavaProjectToSessionSequentially("foo2", ALICE, BOB);

    BOB.superBot().views().packageExplorerView().waitUntilResourceIsShared("foo2");

    BOB.superBot().views().packageExplorerView().selectClass("foo", "bar", "HelloAlice").open();

    BOB.remoteBot().editor("HelloAlice.java").waitUntilIsActive();

    BOB.superBot().views().packageExplorerView().selectClass("foo1", "bar", "HelloBob").open();

    BOB.remoteBot().editor("HelloBob.java").waitUntilIsActive();

    BOB.superBot().views().packageExplorerView().selectClass("foo2", "bar", "HelloCarl").open();

    BOB.remoteBot().editor("HelloCarl.java").waitUntilIsActive();

    ALICE.superBot().views().packageExplorerView().selectClass("foo", "bar", "HelloAlice").open();

    ALICE.remoteBot().editor("HelloAlice.java").waitUntilIsActive();

    ALICE.remoteBot().editor("HelloAlice.java").typeText("testtext");

    ALICE.superBot().views().packageExplorerView().selectClass("foo1", "bar", "HelloBob").open();

    ALICE.remoteBot().editor("HelloBob.java").waitUntilIsActive();

    ALICE.remoteBot().editor("HelloBob.java").typeText("testtext");

    ALICE.superBot().views().packageExplorerView().selectClass("foo2", "bar", "HelloCarl").open();

    ALICE.remoteBot().editor("HelloCarl.java").waitUntilIsActive();

    ALICE.remoteBot().editor("HelloCarl.java").typeText("testtext");

    ALICE.controlBot().getNetworkManipulator().synchronizeOnActivityQueue(BOB.getJID(), 60 * 1000);

    assertEquals(
        ALICE.remoteBot().editor("HelloAlice.java").getText(),
        BOB.remoteBot().editor("HelloAlice.java").getText());
    assertEquals(
        ALICE.remoteBot().editor("HelloBob.java").getText(),
        BOB.remoteBot().editor("HelloBob.java").getText());
    assertEquals(
        ALICE.remoteBot().editor("HelloCarl.java").getText(),
        BOB.remoteBot().editor("HelloCarl.java").getText());
  }
}
