package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseAmountAnswer
import oathdigital.testkit.{CatalogNames, Look}

/** Salt the Earth: an attacker who wins a Conquest buries every card at one
  * targeted site, relics included. */
class SaltTheEarthSuite extends munit.FunSuite:
  private val card = CatalogNames.denizen("Salt the Earth")
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(card)
  private val baron = planlessCard("Baron")
  private val giant = planlessCard("Mountain Giant")
  /** An edifice whose powers are not built, so it changes no Campaign. It is
    * locked, which a burial ignores. */
  private val clock = CatalogNames.edifice("Great Clock")
  private val sticks = CatalogNames.relic("Dowsing Sticks")

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(SaltTheEarth.id, Vector(SaltTheEarth.buried), run.events)

  private def cardsAndRelicsAt(state: OathState, site: SiteId)
      : Vector[CardId] =
    Look(state).cardsAt(site) ++ Look(state).relicsAt(site)

  /** The actor holds Salt the Earth faceup. The origin holds Baron,
    * the Great Clock and Dowsing Sticks, facedown. */
  private def saltableOrigin: Board =
    val base = board(warbands = 5, supply = 7)
    on(withAdviser(base, card, Orientation.FaceUp))(_
      .denizen(baron, at = base.origin)
      .edifice(clock, EdificeSide.Intact, at = base.origin)
      .relicAt(sticks, at = base.origin))

  test("a victorious attacker buries every card at its one target, a " +
      "facedown relic included"):
    val b = saltableOrigin
    val done = commit(rules(winningDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(true))
    assertEquals(cardsAndRelicsAt(done.state, b.origin), Vector.empty)
    val decks = ready(done.state).game.current.commonCards
    assert(decks.worldDeck.contains(baron))
    assert(decks.edificeDeck.contains(clock))
    assert(decks.relicDeck.contains(sticks))
    assertEquals(lines(done), Vector(NoteText.Said("buried",
      s"Buried ${baron.value}, ${clock.value}, ${sticks.value} from " +
        s"${b.origin.value}.", covers = true)))

  test("with two targets the attacker chooses the site, and the other " +
      "keeps its cards"):
    val base = board(extras = 1, warbands = 5, supply = 7)
    val extra = base.extras.head
    val b = on(withAdviser(base, card, Orientation.FaceUp))(_
      .denizen(baron, at = base.origin).denizen(giant, at = extra))
    val asked = commit(rules(winningDice), b, 3,
        targets = Vector(DecisionOptionRef.Site(extra)))
      .pick(b.actor, CampaignIds.attackerPlan, ref)
      .answer(b.actor, CampaignIds.sacrifice, ChooseAmountAnswer(3))
    // Three swords and three sacrificed beat four bandits and four blank
    // dice, and no survivor is left to place.
    assertEquals(asked.winner, Some(true))
    assert(awaits(asked, b.actor, SaltTheEarth.decisionId))
    assertEquals(asked.offered(b.actor).toSet, Set[DecisionOptionRef](
      DecisionOptionRef.Site(b.origin), DecisionOptionRef.Site(extra)))
    val done = asked.pick(b.actor, SaltTheEarth.decisionId,
      DecisionOptionRef.Site(extra))
    assertEquals(cardsAndRelicsAt(done.state, extra), Vector.empty)
    assertEquals(cardsAndRelicsAt(done.state, b.origin), Vector[CardId](baron))
    assertEquals(lines(done), Vector(NoteText.Said("buried",
      s"Buried ${giant.value} from ${extra.value}.", covers = true)))

  test("it is offered in a Conquest, not in a Raid"):
    val base = withEnemyAtOrigin(board(warbands = 4, supply = 7))
    val b = withAdviser(base, card, Orientation.FaceUp)
    assert(commit(rules(winningDice), b, 2)
      .offers(b.actor, b.actor, CampaignIds.attackerPlan, ref))
    val raid = commit(rules(winningDice), b, 2, raid = true)
    assert(!awaits(raid, b.actor, CampaignIds.attackerPlan))

  test("a defeated attacker buries nothing"):
    val b = saltableOrigin
    val done = commit(rules(losingDice), b, 4)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finishPlanWindows
    assertEquals(done.winner, Some(false))
    assertEquals(cardsAndRelicsAt(done.state, b.origin),
      Vector[CardId](baron, clock, sticks))
    assertEquals(lines(done), Vector.empty)
