package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._

/** Rain Boots: a free attacker's plan that scores every single-shield defense
  * die as nothing, as Bag of Siegeworks does, and the card is discarded after
  * the Campaign. */
class RainBootsSuite extends munit.FunSuite:
  private val card = cardWith("denizen.rain-boots")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val bag = relicWith("relic.bag-of-siegeworks")
  private val bagRef: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(bag))
  private val ignored = NoteText.Said("ignored", "Single shields ignored.",
    covers = false)
  private val discardedLine = NoteText.Said("discarded",
    "Discarded after the Campaign.", covers = false)

  /** Every attack die a sword. The first defense die a single shield, the
    * rest two shields: the origin rolls one of each. */
  private val shields: WalkerDice = (kind, count) => Right(kind match
    case DiceKind.Attack => Vector.fill(count)(AttackDieFace.OneSword: DieFace)
    case DiceKind.Defense => Vector.tabulate(count)(index =>
      (if index == 0 then DefenseDieFace.OneShield
       else DefenseDieFace.TwoShields): DieFace))

  private def result(run: Run): CampaignResult =
    ready(run.state).game.current.lastCampaignResult.get

  private def discarded(state: OathState): Boolean = ready(state).game.current
    .commonCards.regionalDiscards.values.exists(_.contains(id))

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    RainBoots.id, Vector(SingleShields.ignored, PlanDiscard.discarded),
    run.events)

  test("the defense is scored without its single shields, the card is " +
      "discarded, and both are said"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val run = commit(rules(shields), b, 3)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(!picked.since(run).exists(_.isInstanceOf[PayCost]))
    val done = picked.finish
    // A single shield and two shields, plus the two bandits: the single
    // shield scores nothing.
    assertEquals(result(done).defenseScore,
      SingleShields.score(result(done).defenseFaces) + 2)
    assertEquals(result(done).defenseScore, 4)
    assert(discarded(done.state))
    assertEquals(lines(done), Vector(ignored, discardedLine))

  test("without a single shield rolled only the discard is said"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(winning), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(lines(done), Vector(discardedLine))

  test("it is offered in a Raid"):
    val b = withAdviser(withEnemyAtOrigin(board()), card, Orientation.FaceUp)
    assert(commit(rules(winning), b, 3, raid = true).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("with Bag of Siegeworks also chosen, the defense is scored the same"):
    val b = withSecrets(withRelic(withAdviser(board(), card,
      Orientation.FaceUp), bag), 1)
    val done = commit(rules(shields), b, 3)
      .pick(b.actor, CampaignIds.attackerPlan, bagRef)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(result(done).defenseScore, 4)
    assertEquals(lines(done), Vector(ignored, discardedLine))
    assertEquals(NoteText.said(BagOfSiegeworks.id,
      Vector(BagOfSiegeworks.ignored), done.events), Vector(ignored))

  test("a Campaign that does not choose it scores the single shields and " +
      "keeps the card"):
    val b = withAdviser(board(), card, Orientation.FaceUp)
    val done = commit(rules(shields), b, 3).finish
    assertEquals(result(done).defenseScore, 5)
    assert(!discarded(done.state))
    assertEquals(lines(done), Vector.empty)
