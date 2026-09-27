package oathdigital.gameplay

import oathdigital.gameplay.powerresolver.{ContributingPower, Contribution,
  OptionRestriction, Restriction}
import oathdigital.gameplay.setup.FirstGameSetupFixture.initialReady
import oathdigital.gameplay.walker.{PowerNoted, ProcedureWalker, WalkerOutcome,
  WalkerPowers}
import oathdigital.model._

object PowerNoteHideSuite:
  val hideSource: PowerSourceRef = PowerSourceRef.Site(SiteId("test-site"))
  val hidden: OathViolation = OathViolation.InvalidEventOrder("hidden by test")

  def hid(ref: DecisionOptionRef): PowerNote = PowerNote(hideSource, "hid",
    ref match
      case DecisionOptionRef.Site(site) => Vector(NoteArg.Site(site))
      case _ => Vector.empty)

  /** Hides `forbidden` at `hook`, and notes each one it hid when `says`. */
  final case class HidingPower(id: PowerId, hook: PowerWindow,
      forbidden: Set[DecisionOptionRef], says: Boolean = true)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(OptionRestriction(
        (_, ref) => Option.when(forbidden(ref))(hidden),
        (_, ref) => Option.when(says)(hid(ref)))))

  /** Rejects any action whose tree opens `hook`, and says so. */
  final case class ForbiddingPower(id: PowerId, hook: PowerWindow)
      extends ContributingPower:
    def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] =
      Map(hook -> Vector(Restriction((_, _) => Some(hidden),
        (_, _) => Some(PowerNote(hideSource, "forbade", Vector.empty)))))

class PowerNoteHideSuite extends munit.FunSuite:
  import PowerNoteHideSuite._

  private val window = PowerWindow.ChallengeAmountSelection
  private def site(id: String) = DecisionOptionRef.Site(SiteId(id))
  private def pick(ready: ReadyGame) = Sequence(Vector[Operation](Decide("pick",
    ready.game.current.turn.activePlayer, DecisionQuery.ChooseOne(
      Vector("a", "b", "c").map(id => DecisionOption.Site(site(id)))),
    window = Some(window))))
  private def notes(events: Vector[OathEvent]): Vector[OathEvent] =
    events.filter(_.isInstanceOf[PowerNoted])

  private val ready = initialReady
  private val actor = ready.game.current.turn.activePlayer
  private val hider = PowerId("test.hider")

  test("each option a power hides is noted before the decision parks"):
    val powers = WalkerPowers(Vector(HidingPower(hider, window,
      Set(site("b"), site("c")))))
    val Right(WalkerOutcome.Parked(_, events)) =
      ProcedureWalker.advance(ready, pick(ready), None, powers): @unchecked
    assertEquals(notes(events), Vector[OathEvent](
      PowerNoted(hider, hid(site("b")), covers = false),
      PowerNoted(hider, hid(site("c")), covers = false)))

  test("answering the decision does not note the hidden options again"):
    val powers = WalkerPowers(Vector(HidingPower(hider, window, Set(site("b")))))
    val tree = pick(ready)
    val Right(WalkerOutcome.Parked(pending, _)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    val Right(WalkerOutcome.Finished(_, events)) = ProcedureWalker.resolve(ready,
      tree, pending, Answered("pick", DecisionAnswer.ChooseOneAnswer(site("a")),
        actor), powers): @unchecked
    assertEquals(notes(events), Vector.empty)

  test("a power that says nothing hides silently"):
    val powers = WalkerPowers(Vector(HidingPower(hider, window, Set(site("b")),
      says = false)))
    val Right(WalkerOutcome.Parked(_, events)) =
      ProcedureWalker.advance(ready, pick(ready), None, powers): @unchecked
    assertEquals(notes(events), Vector.empty)

  test("an option the look-ahead hides is noted by the restriction it would break"):
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
    val Right(WalkerOutcome.Parked(pending, events)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    assertEquals(notes(events), Vector[OathEvent](PowerNoted(forbidder,
      PowerNote(hideSource, "forbade", Vector.empty), covers = false)))
    val Right(WalkerOutcome.Finished(_, answered)) = ProcedureWalker.resolve(
      ready, tree, pending, Answered("ask", DecisionAnswer.ChooseOneAnswer(
        DecisionOptionRef.Button("no")), actor), powers): @unchecked
    assertEquals(notes(answered), Vector.empty)

  test("a restriction on a tree with no decision to narrow writes no note"):
    // Rejecting a whole action is OathRules' check before the walk, which
    // journals nothing; the walker notes only options it hid.
    val powers = WalkerPowers(Vector(ForbiddingPower(PowerId("test.forbidder"),
      PowerWindow.CampaignActionEligibility)))
    val tree = Sequence(Vector[Operation](ModifyDicePool(PoolKey("p"), 1)),
      Some(PowerWindow.CampaignActionEligibility))
    val Right(WalkerOutcome.Finished(_, events)) =
      ProcedureWalker.advance(ready, tree, None, powers): @unchecked
    assertEquals(notes(events), Vector.empty)
