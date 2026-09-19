package oathdigital.gameplay

import oathdigital.model.OathState.Ready
import oathdigital.gameplay.actions.{CampaignCommand, ChallengeCommand,
  MinorActionCommand, NegotiationCommand}
import oathdigital.gameplay.powers.{PhasePowerCatalog, WalkerPowerCatalog}
import oathdigital.gameplay.powers.rest.{LeagueTreatyFixture, SilverTongue,
  SilverTongueFixture}
import oathdigital.gameplay.setup.FirstGameSetupFixture._
import oathdigital.model._

/** The rules half of the pending-walker invariant: over a parked walker,
  * neither a walker start nor any legacy `handle` overload runs.
  */
class PendingWalkerRulesSuite extends munit.FunSuite {
  private val rules = new OathRules(catalog,
    walkerPowerCatalog = WalkerPowerCatalog.default(catalog),
    phasePowerCatalog = PhasePowerCatalog.default(catalog))
  private val pending = OathViolation.InvalidEventOrder(
    "a walker procedure is already pending")

  private def leagueTreatyPark: OathState = {
    val act = LeagueTreatyFixture.act
    val ruler = act.game.current.players.map(_.player)
      .find(_ != act.game.current.turn.activePlayer).get
    val (ready, _) = LeagueTreatyFixture.arranged(Some(ruler),
      Vector(Suit.Arcane -> 2, Suit.Discord -> 2))
    val parked = rules.startWalker(Ready(ready), PhaseTransitionRef.BeginRest,
      ready.game.current.turn.activePlayer).toOption.get
    assert(parked.continue.isInstanceOf[OathContinue.AwaitingRestDecision])
    parked.state
  }

  private def silverTonguePark: OathState = {
    val (ready, actor) = SilverTongueFixture.arranged(
      Vector(Suit.Arcane, Suit.Nomad), Set(Suit.Arcane, Suit.Nomad))
    val parked = rules.startWalker(Ready(ready), ActionRef.UsePower(SilverTongue.id),
      actor, Vector.empty, Vector(DecisionOptionRef.Denizen(DenizenId("92"))))
      .toOption.get
    assert(parked.continue.isInstanceOf[OathContinue.AwaitingPowerDecision])
    parked.state
  }

  Vector("off-turn League Treaty" -> (() => leagueTreatyPark),
    "Silver Tongue choice" -> (() => silverTonguePark)).foreach {
    case (name, park) =>
      test(s"no walker start and no legacy handle runs over a parked $name") {
        val state = park()
        val ready = state.asInstanceOf[Ready].value
        val actor = ready.game.current.turn.activePlayer
        val site = ready.game.current.map.inPlay.head
        (StartableRef.all :+ ActionRef.UsePower(SilverTongue.id)).foreach { ref =>
          assertEquals(rules.startWalker(state, ref, actor).left.toOption,
            Some(pending), ref.key)
        }
        Vector(
          "challenge" -> rules.handle(state, ChallengeCommand.Begin(actor,
            DecisionId("c1"), Banner.PeoplesFavor)),
          "minor action" -> rules.handle(state,
            MinorActionCommand.PeekSiteRelics(actor)),
          "negotiation" -> rules.handle(state, NegotiationCommand.Decline(actor,
            DecisionId("n1"))),
          "campaign" -> rules.handle(state, CampaignCommand.Start(actor,
            DecisionId("cp1"), Vector(site), 1))
        ).foreach { case (family, result) =>
          assertEquals(result.left.toOption, Some(pending), family)
        }
      }
  }
}
