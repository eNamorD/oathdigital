package oathdigital.gameplay.walker

import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.operations.OperationConsent
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Look, Outcomes, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.{accepted, present}

/** The walker's search shows an option askable when only waivable
  * operation refusals stop its path and a grant would open it (catalog
  * batch 8, slice 10, part 2). */
class OperationAsksSuite extends munit.FunSuite:
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val ancientCity = CatalogNames.site("Ancient City")
  private val alchemistRef = DecisionOptionRef.Denizen(alchemist)
  private val wrestlersRef = DecisionOptionRef.Denizen(wrestlers)
  private val ban = PowerId("test.no-favor-on-alchemist")
  private val covers = ConsentKey(ban, p1, "power.use", alchemistRef)
  private val notes = NoteSet(ban)
  private val asked = notes.key("asked",
    Vector(NotePart.Text("Ask first.")))
  private val said = notes.key("said",
    Vector(NotePart.Text("No favor here.")))
  private val alchemistSource = PowerSourceRef.Card(alchemist)
  /** Alchemist shown with the ban's plain reason. */
  private val alchemistPlain = DecisionOption.Unavailable(
    DecisionOption.Denizen(alchemistRef),
    OptionReason.Noted(ban, said(alchemistSource)))

  /** A power whose operation restriction refuses any payment onto
    * Alchemist, waivable by `granter` under `covers`. */
  private def favorOnAlchemistBannedBy(granter: Option[PlayerId])
      : ContributingPower = new ContributingPower:
    def id: PowerId = ban
    def source: RuleSourceRef = RuleSourceRef.GameRule(ban.value)
    def contributions: Map[PowerWindow, Vector[Contribution]] = Map.empty
    override def operationRestrictions: Vector[OperationRestriction] =
      Vector(new OperationRestriction:
        override def reason(ready: ReadyGame, operation: CoreOperation)
            : Option[OperationReason] = operation match
          case pay: PayCost if pay.placedAt == Location.OnCard(alchemist) =>
            Some(OperationReason("test-ban", "no favor on Alchemist",
              OperationReasonKind.Impossible, Some(OperationWaiver(ban,
                granter, covers, asked(alchemistSource),
                said(alchemistSource)))))
          case _ => None)

  /** p1 at Ancient City with Alchemist and, when `beside`, Wrestlers to
    * muster from; 4 favor, 7 Supply and 3 warbands on the board. */
  private def musterAtAncientCity(beside: Boolean = true): ReadyGame =
    val table = Table.start.pawn(p1, at = ancientCity)
      .denizen(alchemist, at = ancientCity)
      .favor(p1, 4).supply(p1, 7).boardWarbands(p1, 3)
    (if beside then table.denizen(wrestlers, at = ancientCity) else table)
      .ready

  private def granted(ready: ReadyGame): ReadyGame =
    ready.copy(consent = ConsentState(
      answers = Map(covers -> ConsentAnswer.Granted.by(p2)),
      turn = Some(ConsentTurn.of(ready))))

  /** The Muster tree with `power`, parked on its source decision. */
  private final case class ParkedSource(tree: Operation,
      pending: PendingTree, powers: WalkerPowers)

  private def parkedSource(ready: ReadyGame, power: ContributingPower)
      : ParkedSource =
    val powers = WalkerPowers(Vector(power))
    val tree = accepted(MusterProcedure.build(catalog, ready, p1))
    ParkedSource(tree, Outcomes.parked(
      ProcedureWalker.advance(ready, tree, None, powers)).tree, powers)

  private def sourceOptions(ready: ReadyGame, power: ContributingPower)(
      using munit.Location): Vector[DecisionOption] =
    val walk = parkedSource(ready, power)
    present(ProcedureWalker.parkedDecide(ready, walk.tree, walk.pending,
      walk.powers), "the source decision").query match
      case one: DecisionQuery.ChooseOne => one.options
      case other => fail(s"expected a choose-one, got $other")

  test("a card only a waivable refusal stops is askable of its granter"):
    assertEquals(sourceOptions(musterAtAncientCity(),
      favorOnAlchemistBannedBy(Some(p2))), Vector(
      DecisionOption.Unavailable(DecisionOption.Denizen(alchemistRef),
        OptionReason.Askable(ban, asked(PowerSourceRef.Card(alchemist)),
          Vector(ConsentAsk(p2, Vector(ban))), Vector(covers))),
      DecisionOption.Denizen(wrestlersRef)))

  test("with nobody to grant it, the card shows its plain reason"):
    assertEquals(sourceOptions(musterAtAncientCity(),
      favorOnAlchemistBannedBy(None)),
      Vector(alchemistPlain, DecisionOption.Denizen(wrestlersRef)))

  test("a refusal remembered this turn shows the plain reason"):
    val ready = musterAtAncientCity()
    val refused = ready.copy(consent = ConsentState(
      answers = Map(covers -> ConsentAnswer.Refused.by(p2)),
      turn = Some(ConsentTurn.of(ready))))
    assertEquals(sourceOptions(refused, favorOnAlchemistBannedBy(Some(p2))),
      Vector(alchemistPlain, DecisionOption.Denizen(wrestlersRef)))

  test("a required decision whose only option is plain is not kept"):
    // Nothing can be chosen and nobody may be asked: the Muster has
    // nothing to offer, as without the search's reasons.
    val ready = musterAtAncientCity(beside = false)
    val tree = accepted(MusterProcedure.build(catalog, ready, p1))
    ProcedureWalker.advance(ready, tree, None, WalkerPowers(Vector(
        favorOnAlchemistBannedBy(None)))) match
      case Left(OathViolation.WaivableOperationRejected("test-ban", _, _)) =>
      case other => fail(s"expected the ban's rejection, got $other")

  test("a search reaching a decision with only plain options does not " +
      "offer the answer that leads there"):
    // Going on leads to a choice of Alchemist alone, whose payment nobody
    // may be asked about; stopping leads nowhere.
    val goOn = DecisionOptionRef.Button("go-on")
    val stop = DecisionOptionRef.Button("stop")
    val alchemistOnly = Decide("test.inner", p1, DecisionQuery.ChooseOne(
      Vector(DecisionOption.Denizen(alchemistRef))))
    val payOntoAlchemist = PayCost(p1, Location.OnCard(alchemist),
      Cost(favor = 1))
    val tree = Sequence(
      Decide("test.outer", p1, DecisionQuery.ChooseOne(Vector(
        DecisionOption.Button(goOn, "Go on"),
        DecisionOption.Button(stop, "Stop")))),
      Branch((_, pending) =>
        if pending.answered.exists(_.answer ==
            DecisionAnswer.ChooseOneAnswer(goOn))
        then Vector(alchemistOnly, payOntoAlchemist)
        else Vector.empty))
    val ready = musterAtAncientCity(beside = false)
    val powers = WalkerPowers(Vector(favorOnAlchemistBannedBy(None)))
    val pending = Outcomes.parked(ProcedureWalker.advance(ready, tree, None,
      powers)).tree
    val options = present(ProcedureWalker.parkedDecide(ready, tree, pending,
      powers), "the outer decision").query match
      case one: DecisionQuery.ChooseOne => one.options
      case other => fail(s"expected a choose-one, got $other")
    assert(!options.find(_.ref == goOn).exists(_.available), options.toString)
    assert(options.find(_.ref == stop).exists(_.available), options.toString)

  test("the plain reason names a refusal nobody may be asked about"):
    // The first refusal is askable of p2; the second names nobody.
    val other = PowerId("test.other")
    def refusal(power: PowerId, granter: Option[PlayerId]): OperationReason =
      OperationReason(power.value, "refused", OperationReasonKind.Impossible,
        Some(OperationWaiver(power, granter, ConsentKey(power, p1,
          "power.use", alchemistRef), asked(alchemistSource),
          said(alchemistSource))))
    assertEquals(OperationConsent.shown(musterAtAncientCity(), p1, Vector(
      refusal(ban, Some(p2)), refusal(other, None))),
      Some(OptionReason.Noted(other, said(alchemistSource))))

  test("a refusal from a player who no longer may grant leaves the card " +
      "askable of the one who may"):
    val ready = musterAtAncientCity()
    val refusedByP3 = ready.copy(consent = ConsentState(
      answers = Map(covers -> ConsentAnswer.Refused.by(Table.p3)),
      turn = Some(ConsentTurn.of(ready))))
    assert(sourceOptions(refusedByP3, favorOnAlchemistBannedBy(Some(p2)))
      .find(_.ref == alchemistRef).exists(_.askable))

  test("a decision whose every option is askable still parks"):
    assertEquals(sourceOptions(musterAtAncientCity(beside = false),
      favorOnAlchemistBannedBy(Some(p2))).map(_.askable), Vector(true))

  test("a grant remembered this turn opens the card, and the Muster " +
      "places its favor there"):
    val ready = granted(musterAtAncientCity())
    val power = favorOnAlchemistBannedBy(Some(p2))
    assert(sourceOptions(ready, power).contains(
      DecisionOption.Denizen(alchemistRef)))
    val walk = parkedSource(ready, power)
    val done = Outcomes.finished(ProcedureWalker.resolve(ready, walk.tree,
      walk.pending, Answered(MusterProcedure.decisionId,
        DecisionAnswer.ChooseOneAnswer(alchemistRef), p1), walk.powers))
      .treeless
    assertEquals(Look(done).tokensOn(alchemist), Tokens(1, 0))
