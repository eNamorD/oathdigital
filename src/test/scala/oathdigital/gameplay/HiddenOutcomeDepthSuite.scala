package oathdigital.gameplay

import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome}
import oathdigital.model._
import oathdigital.testkit.{CatalogNames, Table}
import oathdigital.testkit.Table.p1

/** The legality search stops at hidden information wherever the pipeline
  * would run it, not only at the top of a built batch (hidden outcome depth
  * decision). A batch hands the pipeline whole operations, and the pipeline
  * runs each one's leaves, so a hidden operation nested in a composite runs
  * exactly as a top-level one does and must stop the search just the same.
  *
  * Each test offers the same two options and differs only in where the peek
  * sits. Locked refuses to discard the faceup Sealing Ward, which is what
  * makes the path fail once the search walks past the peek.
  */
class HiddenOutcomeDepthSuite extends munit.FunSuite:
  private val powers = WalkerPowerCatalog.default(catalog)
  private val lockedCard = CatalogNames.denizen("Sealing Ward")
  private val facedownCard = CatalogNames.denizen("Horse Archers")
  private val peaks = CatalogNames.site("Broken Peaks")
  private val onward = "onward"
  private val stop = "stop"

  private val ready = Table.start
    .adviser(p1, lockedCard)
    .denizen(facedownCard, peaks, facedown = true)
    .bankFavor(Suit.Discord, 1)
    .ready

  /** A peek the pipeline accepts: the card is at that site, facedown. */
  private val peek: Peek = Peek(p1, facedownCard, Location.Site(peaks))

  /** A favor take the pipeline accepts, carrying `leaving` under it. */
  private def takeFavor(leaving: Vector[CoreOperation]): Take =
    Take(Piece.Favor(1), p1, Location.FavorBank(Suit.Discord),
      Location.PlayArea(p1), leaving = leaving)

  /** Discarding the Locked adviser, which the restriction refuses. */
  private val discardLocked: Operation =
    Discard.Denizen(lockedCard, PositionedLocation(Location.PlayArea(p1)),
      Region.Provinces, catalog.suitOf(lockedCard).get, 0, 0, p1,
      required = true)

  private def button(key: String) =
    DecisionOption.Button(DecisionOptionRef.Button(key), key)
  private def ref(key: String): DecisionOptionRef =
    DecisionOptionRef.Button(key)

  /** Asks onward-or-stop; onward runs `batch`, then the refused discard. */
  private def askingThenDiscarding(batch: Vector[CoreOperation]): Operation =
    Sequence(Vector[Operation](
      Decide("test.ask", p1, DecisionQuery.ChooseOne(
        Vector(button(onward), button(stop)))),
      Branch((_, pending) =>
        pending.answered.collectFirst {
          case Answered("test.ask", DecisionAnswer.ChooseOneAnswer(c), _) => c
        } match
          case Some(chosen) if chosen == ref(onward) =>
            Vector(BuildOps((_, _) => Right(batch)), discardLocked)
          case _ => Vector.empty)))

  private def offered(tree: Operation): Vector[DecisionOptionRef] =
    ProcedureWalker.advance(ready, tree, None, powers) match
      case Right(WalkerOutcome.Parked(pending, _)) =>
        ProcedureWalker.openDecisions(ready, tree, pending, powers).head
          .query match
          case one: DecisionQuery.ChooseOne => one.options.map(_.ref)
          case other => fail(s"expected a choose-one, got $other")
      case other => fail(s"expected a park, got $other")

  test("an option whose path ends in a refused discard is hidden when " +
      "nothing on the way conceals the outcome"):
    assertEquals(offered(askingThenDiscarding(Vector(GainSupply(p1, 1)))),
      Vector(ref(stop)))

  test("a peek at the top of the batch stops the search, so the option " +
      "past it stays offered"):
    assertEquals(offered(askingThenDiscarding(Vector(peek))),
      Vector(ref(onward), ref(stop)))

  test("a peek nested in a composite the batch carries stops the search " +
      "just the same"):
    assertEquals(offered(askingThenDiscarding(Vector(takeFavor(
      Vector(peek))))), Vector(ref(onward), ref(stop)))
