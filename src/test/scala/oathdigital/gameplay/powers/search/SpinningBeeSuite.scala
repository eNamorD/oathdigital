package oathdigital.gameplay.powers.search

import oathdigital.gameplay.ModifierPlay
import oathdigital.gameplay.actions.search.SearchProcedure
import oathdigital.gameplay.powerresolver.ModifierPicks
import oathdigital.gameplay.powers.{SearchFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.WalkerCompleted
import oathdigital.model._
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, ParkedNode, Table}
import oathdigital.testkit.Table.p1
import oathdigital.testkit.Unwrap.accepted

/** Spinning Bee (card 232): declare a suit, then draw from the actor's own
  * discard pile until a card of that suit is drawn. No other Search
  * modifier may be used with it (catalog batch 6, slice 4). */
class SpinningBeeSuite extends munit.FunSuite:
  import SearchFixture.rules

  private val spinningBee = CatalogNames.denizen("Spinning Bee")
  private val augury = CatalogNames.denizen("Augury")
  private val modifiers = Vector(SpinningBee.id)
  private val modifierDecision =
    ModifierPicks.decisionId(PowerWindow.SearchModifierSelection)
  // The Cradle discard pile, bottom first: Hearth, Order, Discord.
  private val homesteaders = CatalogNames.denizen("Homesteaders")
  private val wrestlers = CatalogNames.denizen("Wrestlers")
  private val defame = CatalogNames.denizen("Defame")

  /** p1 at Ancient City (Cradle) holding Spinning Bee faceup, with 5
    * Supply. The Cradle discard pile holds Homesteaders under Wrestlers
    * under Defame unless `emptyPile`. */
  private def holdingBee(emptyPile: Boolean = false): Table =
    val table = Table.start.pawn(p1, "Ancient City").adviser(p1, spinningBee)
      .supply(p1, 5)
    if emptyPile then table
    else table.discarded(Region.Cradle, homesteaders, wrestlers, defame)

  /** The decision `state` is parked on, as the projector rebuilds it. */
  private def parkedDecide(state: OathState)(using munit.Location): Decide =
    ParkedNode.of(state, catalog, WalkerPowerCatalog.default(catalog)) match
      case Right(Some(ParkedNode.Decision(_, decide, _))) => decide
      case other => fail(s"expected a parked decision, got $other")

  /** p1 starts a Search with Spinning Bee picked. */
  private def picked(table: Table)(using munit.Location): OathTransition =
    accepted(ModifierPlay.started(rules, Ready(table.ready), ActionRef.Search,
      p1, PowerWindow.SearchModifierSelection, modifiers))

  /** p1 starts a Search with Spinning Bee picked, and declares `suit`. */
  private def declared(table: Table, suit: Suit)(using munit.Location)
      : OathTransition =
    accepted(rules.resolveWalker(picked(table).state, p1,
      SpinningBee.suitDecisionId,
      DecisionAnswer.ChooseOneAnswer(DecisionOptionRef.FavorBank(suit))))

  /** How `power`'s option is shown at the modifier decision once p1 has
    * picked `first`, as (state, reason). */
  private def shownAfterPicking(ready: ReadyGame, first: PowerId,
      power: DenizenId)(using munit.Location)
      : Option[(String, Option[String])] =
    val begun = accepted(rules.startWalker(Ready(ready), ActionRef.Search,
      p1))
    val chosen = accepted(rules.resolveWalker(begun.state, p1,
      modifierDecision, ModifierPlay.pick(first)))
    ShownOptions.optionFor(ShownOptions.atParkedDecision(chosen.state),
      DecisionOptionRef.Denizen(power))
      .map(option => (option.state, option.reason))

  test("picked, the source decision gives way to declaring one of the six " +
      "suits"):
    val decide = parkedDecide(picked(holdingBee()).state)
    assertEquals(decide.decisionId, SpinningBee.suitDecisionId)
    assertEquals(decide.query, DecisionQuery.ChooseOne(
      Suit.all.map(suit =>
        DecisionOption.FavorBank(DecisionOptionRef.FavorBank(suit))),
      heading = Some(SpinningBee.heading)))

  test("it draws its own pile from the top through the first card of the " +
      "suit, for 2 Supply, and the keep choice follows"):
    val searched = declared(holdingBee(), Suit.Order)
    val after = SearchFixture.after(searched)
    assertEquals(after.game.current.temporaryHands(p1),
      Vector(defame, wrestlers))
    assertEquals(Look(after).supply(p1), 5 - 2)
    assertEquals(Look(after).discardPile(Region.Cradle),
      Vector(homesteaders))
    assertEquals(parkedDecide(searched.state).decisionId,
      SearchProcedure.cardDecisionId)

  test("with no card of the suit, every drawn card is discarded and " +
      "nothing is kept"):
    val searched = declared(holdingBee(), Suit.Nomad)
    val after = SearchFixture.after(searched)
    assertEquals(after.game.current.temporaryHands.getOrElse(p1,
      Vector.empty), Vector.empty)
    assertEquals(Look(after).discardPile(Region.Cradle), Vector.empty)
    // Discarded in draw order, to the pile after the actor's region's.
    assertEquals(Look(after).discardPile(Region.Provinces).takeRight(3),
      Vector(defame, wrestlers, homesteaders))
    assert(searched.events.exists(_.isInstanceOf[WalkerCompleted]),
      searched.events)

  test("picked, it makes every other Search modifier unavailable"):
    val ready = holdingBee().adviser(p1, augury).ready
    assertEquals(shownAfterPicking(ready, SpinningBee.id, augury),
      Some(("unavailable",
        Some("Spinning Bee: You cannot use other Search modifiers."))))

  test("it is unavailable once another modifier is picked"):
    val ready = holdingBee().adviser(p1, augury).ready
    assertEquals(shownAfterPicking(ready, Augury.id, spinningBee),
      Some(("unavailable", Some(SpinningBee.notWithOthers))))

  test("it is unavailable while the actor's own discard pile is empty"):
    // Augury makes the modifier decision ask; Spinning Bee alone could not.
    val ready = holdingBee(emptyPile = true).adviser(p1, augury).ready
    val begun = accepted(rules.startWalker(Ready(ready), ActionRef.Search,
      p1))
    assertEquals(ShownOptions.optionFor(
      ShownOptions.atParkedDecision(begun.state),
      DecisionOptionRef.Denizen(spinningBee))
      .map(option => (option.state, option.reason)),
      Some(("unavailable", Some("The Cradle discard pile is empty"))))
