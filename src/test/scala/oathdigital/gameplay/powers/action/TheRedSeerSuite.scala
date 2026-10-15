package oathdigital.gameplay.powers.action

import oathdigital.gameplay.phases.PhaseMenu
import oathdigital.gameplay.powers.{NoteText, OathkeeperGoalChoice,
  PhasePowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerPowers
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, ParkedNode, PhasePowerHarness,
  Table}
import oathdigital.testkit.Outcomes.assertWireRoundTrips
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** The Red Seer: with the Darkest Secret, change the Oathkeeper goal to
  * anything but Devotion. */
class TheRedSeerSuite extends munit.FunSuite:
  import PhasePowerHarness._

  private val seer = CatalogNames.denizen("The Red Seer")
  private val source = DecisionOptionRef.Denizen(seer)

  /** p1's Act holding The Red Seer as an adviser, 3 faceup secrets and,
    * when `holds`, the Darkest Secret, under the goal `goal`. */
  private def seerHeldUnder(goal: OathkeeperGoal,
      holds: Boolean = true): ReadyGame =
    Table.start.turn(p1, Phase.Act).adviser(p1, seer).secrets(p1, 3)
      .darkestSecret(Option.when(holds)(p1), 1).oathkeeperGoal(goal).ready

  private def button(goal: OathkeeperGoal): DecisionOption.Button =
    DecisionOption.Button(OathkeeperGoalChoice.ref(goal),
      OathkeeperGoalChoice.label(goal))

  private def unavailable(goal: OathkeeperGoal, reason: String)
      : DecisionOption = DecisionOption.Unavailable(button(goal),
    OptionReason.Written(reason))

  /** The options of the decision the use is parked on. */
  private def parkedOptions(t: OathTransition): Vector[DecisionOption] =
    ParkedNode.of(t.state, catalog,
      phasePowers = PhasePowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) =>
        decide.query match
          case DecisionQuery.ChooseOne(options, _, _) => options
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a parked decision, got $other")

  /** Why the Act menu shows The Red Seer unavailable, if it does. */
  private def menuReason(ready: ReadyGame): Option[OptionReason] =
    PhaseMenu.offers(PhaseMenu.Menu.Act, catalog, ready, p1,
      PhasePowerCatalog.default(catalog), WalkerPowers.empty)
      .map(_.option).collectFirst {
        case DecisionOption.Unavailable(shown, reason)
            if shown.ref == source => reason
      }

  test("it pays its cost, offers every goal but the current one and " +
      "Devotion, and sets the one chosen"):
    val ready = seerHeldUnder(OathkeeperGoal.Supremacy)
    val started = accepted(use(ready, TheRedSeer, source))
    assert(awaits(started, TheRedSeer.decisionId))
    assertEquals(parkedOptions(started), Vector[DecisionOption](
      unavailable(OathkeeperGoal.Supremacy, OathkeeperGoalChoice.currentGoal),
      button(OathkeeperGoal.Protection), button(OathkeeperGoal.ThePeople),
      unavailable(OathkeeperGoal.Devotion, TheRedSeer.notDevotion)))
    // 1 secret placed on the card and 2 burnt.
    assertEquals(Look(after(started)).tokensOn(seer), Tokens(0, 1))
    assertEquals(Look(after(started)).faceUpSecrets(p1), 0)
    val done = accepted(answer(started, p1, TheRedSeer.decisionId,
      pick(OathkeeperGoalChoice.ref(OathkeeperGoal.ThePeople))))
    assertEquals(after(done).game.campaign.oathkeeperGoal,
      OathkeeperGoal.ThePeople)
    val events = started.events ++ done.events
    assertEquals(replayed(ready, events), Right(done.state))
    assertWireRoundTrips(events)
    assertEquals(NoteText.said(TheRedSeer, done.events), Vector(
      NoteText.Said(NoteKey.Used,
        "Changed the Oathkeeper goal to the People.", covers = false)))

  test("under Devotion, Devotion is the current goal and the other three " +
      "are offered"):
    val started = accepted(use(seerHeldUnder(OathkeeperGoal.Devotion),
      TheRedSeer, source))
    assertEquals(parkedOptions(started), Vector[DecisionOption](
      button(OathkeeperGoal.Supremacy), button(OathkeeperGoal.Protection),
      button(OathkeeperGoal.ThePeople),
      unavailable(OathkeeperGoal.Devotion, OathkeeperGoalChoice.currentGoal)))

  test("an unavailable goal is refused"):
    val started = accepted(use(seerHeldUnder(OathkeeperGoal.Supremacy),
      TheRedSeer, source))
    Vector(OathkeeperGoal.Supremacy, OathkeeperGoal.Devotion).foreach { goal =>
      assert(answer(started, p1, TheRedSeer.decisionId,
        pick(OathkeeperGoalChoice.ref(goal))).isLeft, goal.key)
    }

  test("without the Darkest Secret it is unavailable"):
    val ready = seerHeldUnder(OathkeeperGoal.Supremacy, holds = false)
    assert(!usableNow(ready).exists(_.power.id == TheRedSeer.id))
    assertEquals(menuReason(ready),
      Some(OptionReason.Written("Nothing to do now")))

  test("with Yew Staff and no Darkest Secret it is usable"):
    val ready = Table.from(seerHeldUnder(OathkeeperGoal.Supremacy,
      holds = false)).relic(p1, "Yew Staff").ready
    assert(usableNow(ready).exists(_.power.id == TheRedSeer.id))
    assert(awaits(accepted(use(ready, TheRedSeer, source)),
      TheRedSeer.decisionId))
