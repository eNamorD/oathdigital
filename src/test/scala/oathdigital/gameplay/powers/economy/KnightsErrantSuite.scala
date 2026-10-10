package oathdigital.gameplay.powers.economy

import oathdigital.testkit.MenuPlay
import oathdigital.gameplay.{FollowUpPlay, ModifierPlay, OathRules,
  WalkerDiceFixture}
import oathdigital.gameplay.actions.economy.MusterProcedure
import oathdigital.gameplay.powers.WalkerPowerCatalog
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer
import oathdigital.model.OathState.Ready
import oathdigital.testkit.{CatalogNames, Look, Outcomes, ParkedNode, Table}
import oathdigital.testkit.Table.{p1, p2}
import oathdigital.testkit.Unwrap.accepted

class KnightsErrantSuite extends munit.FunSuite:
  private val knights = CatalogNames.denizen("Knights Errant")
  private val alchemist = CatalogNames.denizen("Alchemist")
  private val modifiers = Vector(KnightsErrant.id)
  private val ancientCity = CatalogNames.site("Ancient City")
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    walkerDice = WalkerDiceFixture.blanks)

  /** p1 holds Knights Errant and stands at Ancient City with the token-free
    * Alchemist to muster from; p2 and p3 stand elsewhere. Two bandits rule
    * the site, so a Conquest is legal, unless `campaignLegal` is false. p1 has
    * `supply` Supply, 4 favor and 3 warbands.
    */
  private def staged(supply: Int, campaignLegal: Boolean = true): Table =
    Table.start
      .pawn(p1, at = ancientCity).pawn(p2, at = "Broken Peaks")
      .pawn(Table.p3, at = "Buried Giant")
      .adviser(p1, knights)
      .denizen(alchemist, at = ancientCity)
      .bandits(ancientCity, if campaignLegal then 2 else 0)
      .supply(p1, supply).favor(p1, 4).boardWarbands(p1, 3)

  private def ready(transition: OathTransition): ReadyGame =
    Outcomes.ready(transition.state)

  /** Starts a Muster with `selected` and answers its source. */
  private def musterFrom(state: ReadyGame, selected: Vector[PowerId])
      : OathTransition =
    val started = accepted(ModifierPlay.started(rules, Ready(state),
      ActionRef.Muster, p1,
      PowerWindow.MusterModifierSelection, selected))
    val done = accepted(rules.resolveWalker(started.state, p1,
      MusterProcedure.decisionId,
      ChooseOneAnswer(DecisionOptionRef.Denizen(alchemist))))
    done.copy(events = started.events ++ done.events)

  /** The warbands the actor has once the Muster has gained: the
    * matching-adviser bonus depends on the cards drawn, so it is read from a
    * Muster without the power.
    */
  private def afterMuster: Int =
    val plain = musterFrom(staged(supply = 1).ready, Vector.empty)
    Look(ready(plain)).boardWarbands(p1)

  private val owed = QueuedProcedure(p1, ActionRef.Campaign, Vector.empty,
      Some(PowerSourceRef.Card(knights)), QueueKind.Offer)

  /** The entry the Muster's offer asks about, if it parked one. */
  private def offered(transition: OathTransition)
      : Option[QueuedProcedure] = FollowUpPlay.offered(transition.state)

  test("the Muster offers a Campaign owed to its player"):
    val mustered = musterFrom(staged(supply = 1).ready, modifiers)
    assertEquals(offered(mustered), Some(owed))
    assertEquals(Look(ready(mustered)).boardWarbands(p1), afterMuster)

  test("without the selection the Muster offers nothing"):
    val mustered = musterFrom(staged(supply = 1).ready, Vector.empty)
    assertEquals(offered(mustered), None)
    assertEquals(MenuPlay.pendingBeyondMenu(ready(mustered)), None)

  test("the offer is unconditional: nothing to campaign against still " +
      "offers it"):
    val mustered = musterFrom(staged(supply = 1, campaignLegal = false).ready,
      modifiers)
    assertEquals(offered(mustered), Some(owed))

  test("the taken Campaign sees the warbands the Muster gained and spends " +
      "no Supply"):
    // The Muster spends p1's only Supply; the Campaign's 2 is not spent.
    val mustered = musterFrom(staged(supply = 1).ready, modifiers)
    val started = accepted(FollowUpPlay.taken(rules, mustered.state, p1))
    assertEquals(Look(ready(started)).supply(p1), 0)
    assertEquals(ready(started).game.current.walkerProcedure,
      Some(ActionRef.Campaign))
    assertEquals(Look(ready(started)).boardWarbands(p1), afterMuster)

  test("it cannot be selected for a Campaign, or for any other action"):
    val state = staged(supply = 1).ready
    val offered = (action: ActionRef) =>
      accepted(rules.offerableWalkerPowers(state, p1, action)).map(_.id)
    assert(offered(ActionRef.Muster).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Campaign).contains(KnightsErrant.id))
    assert(!offered(ActionRef.Trade).contains(KnightsErrant.id))

  test("Vow of Peace shows on the offer, and Skip clears it"):
    val vowed = staged(supply = 1).adviser(p1, "Vow of Peace").ready
    val mustered = musterFrom(vowed, modifiers)
    assert(FollowUpPlay.taken(rules, mustered.state, p1).isLeft)
    val shown = ParkedNode.of(mustered.state, catalog,
      WalkerPowerCatalog.default(catalog)).toOption.flatten.collect {
        case ParkedNode.Decision(_, decide, _) => decide.query
      }.collect { case one: DecisionQuery.ChooseOne => one.options }
      .getOrElse(Vector.empty)
    assertEquals(shown.headOption.map(_.available), Some(false))
    assertEquals(shown.headOption.collect {
      case DecisionOption.Unavailable(_, OptionReason.Written(text)) => text
    }, Some("Vow of Peace prevents its ruler from campaigning"))
    val skipped = accepted(FollowUpPlay.skipped(rules, mustered.state, p1))
    assertEquals(MenuPlay.pendingBeyondMenu(ready(skipped)), None)
    assertEquals(ready(skipped).game.current.queue, Vector.empty)
