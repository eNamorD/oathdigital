package oathdigital.gameplay.powers.whenplayed

import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, Transform}
import oathdigital.gameplay.powers.PowerFixture
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._

/** The window on Conspiracy's target decision: a power can remove targets, and
  * when it removes every one the decision is not asked and nothing is taken.
  */
class ConspiracyTargetWindowSuite extends munit.FunSuite {
  import PowerFixture._

  private val conspiracy = VisionRules.Conspiracy
  private val enemy: PlayerId = base.game.current.players.map(_.player)
    .find(_ != actor).get
  private val relics = Vector(RelicId("R10"), RelicId("R11"))

  /** The actor holds Conspiracy in a temporary hand. The enemy stands on the
    * actor's site holding `relics`, faceup.
    */
  private def staged: ReadyGame = {
    val current = base.game.current
    val site = player(base).pawnSite
    base.updateCurrent(_.copy(
      players = current.players.map(p =>
        if (p.player == enemy) p.copy(pawnSite = site,
          relics = relics.map(RelicState(_, Orientation.FaceUp, Tokens.empty)))
        else p),
      banners = current.banners.copy(
        peoplesFavor = current.banners.peoplesFavor.copy(holder = None),
        darkestSecret = current.banners.darkestSecret.copy(holder = None)),
      commonCards = current.commonCards.copy(
        worldDeck = current.commonCards.worldDeck.filterNot(_ == conspiracy),
        relicDeck = current.commonCards.relicDeck.filterNot(relics.contains)),
      map = current.map.copy(sites = current.map.sites.map { case (id, s) =>
        id -> s.copy(relics = s.relics.filterNot(r => relics.contains(r.id))) }),
      temporaryHands = current.temporaryHands.updated(actor,
        Vector(conspiracy))))
  }

  private def removing(keep: DecisionOptionRef => Boolean): ContributingPower =
    new ContributingPower {
      def id: PowerId = PowerId("test.conspiracy-window")
      def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)
      def contributions: Map[PowerWindow, Vector[Contribution]] = Map(
        PowerWindow.ConspiracyTargetSelection -> Vector(Transform((_, ops) =>
          ops.flatMap {
            case decide: Decide => decide.query match {
              case one: DecisionQuery.ChooseOne =>
                val options = one.options.filter(o => keep(o.ref))
                if (options.isEmpty) Vector.empty
                else Vector(decide.copy(query = one.copy(options = options)))
              case _ => Vector(decide)
            }
            case other => Vector(other)
          })))
    }

  private def hook: CardPlayedFaceup =
    CardPlayedFaceup(conspiracy, RuleSourceRef.Adviser(actor, conspiracy))

  private def walk(power: Option[ContributingPower])
      : (WalkerPowers, WalkerOutcome) = {
    val powers = WalkerPowers(ConspiracyWhenPlayed +: power.toVector)
    (powers, ProcedureWalker.advance(staged, hook, None, powers).toOption.get)
  }

  test("the target decision carries the Conspiracy target window") {
    assertEquals(PowerWindow.ConspiracyTargetSelection.key,
      "conspiracy.target-selection")
    val (powers, parked) = walk(None)
    val decide = ProcedureWalker.parkedDecide(staged, hook,
      parked.asInstanceOf[WalkerOutcome.Parked].tree, powers).get
    assertEquals(decide.window, Some(PowerWindow.ConspiracyTargetSelection))
    assertEquals(decide.query.asInstanceOf[DecisionQuery.ChooseOne].options
      .map(_.ref), Vector[DecisionOptionRef](
      DecisionOptionRef.RelicSlot(enemy, 0),
      DecisionOptionRef.RelicSlot(enemy, 1)))
  }

  test("a power at the window removes the targets it forbids") {
    val only = DecisionOptionRef.RelicSlot(enemy, 1)
    val (powers, parked) = walk(Some(removing(_ == only)))
    val decide = ProcedureWalker.parkedDecide(staged, hook,
      parked.asInstanceOf[WalkerOutcome.Parked].tree, powers).get
    assertEquals(decide.query.asInstanceOf[DecisionQuery.ChooseOne].options
      .map(_.ref), Vector[DecisionOptionRef](only))
  }

  test("when every target is removed nothing is asked and nothing is taken, " +
      "and the card still leaves the game") {
    val (_, outcome) = walk(Some(removing(_ => false)))
    val done = outcome.asInstanceOf[WalkerOutcome.Finished].treeless
    assertEquals(player(done, enemy).relics.map(_.id), relics)
    assertEquals(player(done).relics, player(staged).relics)
    assertEquals(done.game.current.temporaryHands(actor), Vector.empty)
    assert(!CardIndex.from(done.game).toOption.get.ids.contains(conspiracy))
  }
}
