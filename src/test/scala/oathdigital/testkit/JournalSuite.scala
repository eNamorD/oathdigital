package oathdigital.testkit

import oathdigital.gameplay.OathRules
import oathdigital.gameplay.setup.FirstGameSetupFixture

/** A journal replay returns the state its events reach and fails naming the
  * first event that does not replay; a replay evolved from a ready game
  * reaches the same state the engine does. */
class JournalSuite extends munit.FunSuite:
  private val rules = new OathRules(FirstGameSetupFixture.catalog)
  // The real setup on purpose: Journal.replayed is tested on a real journal.
  private val setup = FirstGameSetupFixture.setupThroughWake()

  test("replayed returns the state a real setup's events reach"):
    assertEquals(Journal.replayed(rules, setup.events), setup.state)

  test("replayed fails naming the first event that does not replay, and why"):
    // The setup begins the game; beginning it a second time is refused.
    val doubled = setup.events ++ setup.events
    val message = intercept[munit.FailException](
      Journal.replayed(rules, doubled)).getMessage
    assert(message.contains(s"event ${setup.events.size} does not replay"),
      message)

  test("evolved from a ready game midway reaches the state replayed reaches " +
      "from no game"):
    val halfway = setup.events.size / 2
    val midway = Outcomes.ready(Journal.replayed(rules,
      setup.events.take(halfway)))
    assertEquals(Journal.evolved(rules, midway, setup.events.drop(halfway)),
      Right(Journal.replayed(rules, setup.events)))
