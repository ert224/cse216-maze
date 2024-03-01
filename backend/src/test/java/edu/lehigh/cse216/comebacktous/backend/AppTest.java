package edu.lehigh.cse216.comebacktous.backend;

import junit.framework.Test;
import junit.framework.TestCase;
import junit.framework.TestSuite;

/**
 * Unit test for simple App.
 */
public class AppTest extends TestCase {

  /**
   * Create the test case
   *
   * @param testName name of the test case
   */
  public AppTest(String testName) {
    super(testName);
  }

  /**
   * @return the suite of tests being tested
   */
  public static Test suite() {
    return new TestSuite(AppTest.class);
  }

  /**
   * Rigourous Test :-)
   */
  public void testApp() {
    String db_url =
      "postgres://nrhaggnqicsbno:1ffebc514f79d9ba02032a2e174a8f37972c793cb2f9dbc856eba4f9a88d5630@ec2-3-220-207-90.compute-1.amazonaws.com:5432/dam4fg1iidcoa9";
    final Database dataBase = Database.getDatabase(db_url);
    assert (dataBase.login("46129492", "test@gmail.com") != -1);
    assert (
      dataBase.insertUser(
        "1",
        "alreadyExists",
        "a@mail.com",
        "first",
        "last",
        "m",
        "s",
        "Cool!"
      ) !=
      1
    ); // user should already exist
    assert (dataBase.insertIdea("Title", "Good Idea", "1") == 1);
    assert (dataBase.insertComment("com", 8, "1") == 1);
    assert (dataBase.updateLikes(8, "1", 1, 1) == 1);
    assert (dataBase.selectOneUser("1") != null);
  }
}
