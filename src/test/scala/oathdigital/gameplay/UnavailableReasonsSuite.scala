package oathdigital.gameplay

import oathdigital.gameplay.PowerNoteHideSuite.{ForbiddingPower, HidingPower,
  hid, hideSource}
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerPowers}
import oathdigital.model._
import oathdigital.testkit.Table
import oathdigital.testkit.Outcomes.parked

/** An option a power hides is still shown, as unavailable, when the power
  * says why (board decisions design, "Unavailable options"): the reason is
  * the power's own note. An option no note explains is removed as before,
  * and an unavailable option is never an answer.
  */
class UnavailableReasonsSuite extends munit.FunSuite:
  private val ready = Table.start.ready
  private val actor = ready.game.current.turn.activePlayer
  private val window = PowerWindow.ChallengeAmountSelection
  private val hider = PowerId("test.hider")
  private def site(id: String) = DecisionOptionRef.Site(SiteId(id))
  private def option(id: String) = DecisionOption.Site(site(id))
  private val pick = Sequence(Vector[Operation](Decide("pick", actor,
    DecisionQuery.ChooseOne(Vector(option("a"), option("b"))),
    window = Some(window))))

  private def asked(tree: Operation, powers: WalkerPowers): DecisionQuery =
    val pending = parked(ProcedureWalker.advance(ready, tree, None, powers)).tree
    ProcedureWalker.openDecisions(ready, tree, pending, powers).head.query

  test("an option a power forbids and explains is shown unavailable"):
    val powers = WalkerPowers(Vector(HidingPower(hider, window, Set(site("b")))))
    assertEquals(asked(pick, powers), DecisionQuery.ChooseOne(Vector(
      option("a"), DecisionOption.Unavailable(option("b"),
        OptionReason.Noted(hider, hid(site("b")))))))

  test("an option a power forbids silently is removed"):
    val powers = WalkerPowers(Vector(HidingPower(hider, window, Set(site("b")),
      says = false)))
    assertEquals(asked(pick, powers),
      DecisionQuery.ChooseOne(Vector(option("a"))))

  test("an option the look-ahead hides is shown with the note of the " +
      "restriction it would break, and is still refused"):
    val nested = PowerWindow.CampaignActionEligibility
    val forbidder = PowerId("test.forbidder")
    val powers = WalkerPowers(Vector(ForbiddingPower(forbidder, nested)))
    def button(key: String) =
      DecisionOption.Button(DecisionOptionRef.Button(key), key)
    val tree = Sequence(Vector[Operation](Decide("ask", actor,
        DecisionQuery.ChooseOne(Vector(button("yes"), button("no")))),
      Branch((_, pending) => if pending.answered.exists {
        case Answered("ask", DecisionAnswer.ChooseOneAnswer(ref), _) =>
          ref == DecisionOptionRef.Button("yes")
        case _ => false
      } then Vector(Sequence(Vector.empty, Some(nested))) else Vector.empty)))
    assertEquals(asked(tree, powers), DecisionQuery.ChooseOne(Vector(
      DecisionOption.Unavailable(button("yes"), OptionReason.Noted(forbidder,
        PowerNote(hideSource, "forbade", Vector.empty))), button("no"))))
    val pending = parked(ProcedureWalker.advance(ready, tree, None, powers)).tree
    assertEquals(ProcedureWalker.resolve(ready, tree, pending,
      Answered("ask", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("yes")), actor), powers),
      Left(OathViolation.InvalidEventOrder(
        "decision ask does not offer the selected option")))
