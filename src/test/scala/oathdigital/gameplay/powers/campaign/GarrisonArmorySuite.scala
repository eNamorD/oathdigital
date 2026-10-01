package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

/** Garrison Armory: a defender's plan, a favor placed, in a Conquest, so that
  * the warbands at the targets add their defense twice. */
class GarrisonArmorySuite extends munit.FunSuite:
  private val card = cardWith("denizen.garrison-armory")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val bag = relicWith("relic.bag-of-siegeworks")
  private val bagRef: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(bag))
  private val provisions = cardWith("denizen.extra-provisions")
  private val twoAdded = NoteText.Said("added",
    "Warbands on the targets added 2 more defense.", covers = false)

  /** Every attack die a sword; the defense dice are `faces`, in turn. */
  private def defense(faces: DefenseDieFace*): WalkerDice = (kind, count) =>
    Right(kind match
      case DiceKind.Attack => Vector.fill(count)(AttackDieFace.OneSword: DieFace)
      case DiceKind.Defense =>
        Vector.tabulate(count)(index => faces(index % faces.size): DieFace))

  /** The other player rules the origin with two warbands and holds Garrison
    * Armory and a favor. */
  private def defender: Board =
    val base = againstPlayer(board())
    on(withAdviserFor(base, base.other, card, Orientation.FaceUp))(
      _.favor(base.other, 1))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  private def result(run: Run): CampaignResult =
    ready(run.state).game.current.lastCampaignResult.get

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    GarrisonArmory.id, Vector(GarrisonArmory.added), run.events)

  test("a defender places a favor, off turn, and the targets' warbands add " +
      "their defense twice, and it says so"):
    val b = defender
    val run = commit(rules(defense(DefenseDieFace.TwoShields)), b, 5)
    val picked = run.pick(b.other, CampaignIds.defenderPlan, ref)
    // Paid off turn, the favor goes to the bank at once, not onto the card.
    assertEquals(adviserTokens(picked.state, b.other), Some(Tokens.empty))
    assertEquals(player(picked.state, b.other).board.favor, 0)
    val done = picked.finish
    // Two dice of two shields, then the two warbands at the origin, twice.
    assertEquals(result(done).defenseScore, 4 + 2 + 2)
    assertEquals(lines(done), Vector(twoAdded))

  test("the doubler multiplies the dice only"):
    val done = commit(rules(defense(DefenseDieFace.TwoShields,
      DefenseDieFace.Doubler)), defender, 5)
      .pick(defender.other, CampaignIds.defenderPlan, ref).finish
    // Two shields doubled is 4; the warbands add 2, then 2 more, undoubled.
    assertEquals(result(done).defenseScore, 8)

  test("single shields are ignored before the warbands are added"):
    val b = withSecrets(withRelic(defender, bag), 1)
    val bagged = commit(rules(defense(DefenseDieFace.OneShield,
      DefenseDieFace.TwoShields)), b, 5)
      .pick(b.actor, CampaignIds.attackerPlan, bagRef)
    val attackDone =
      if awaits(bagged, b.actor, CampaignIds.attackerPlan) then
        bagged.answer(b.actor, CampaignIds.attackerPlan,
          ChooseOneAnswer(CampaignIds.finish))
      else bagged
    val done = attackDone.pick(b.other, CampaignIds.defenderPlan, ref).finish
    // The single shield scores nothing, so the dice score 2; the warbands add
    // 2, twice.
    assertEquals(result(done).defenseScore, 6)
    assertEquals(lines(done), Vector(twoAdded))

  test("it is not offered in a Raid"):
    val base = withEnemyAtOrigin(board())
    // Extra Provisions keeps the defender's window open.
    val b = on(withAdviserFor(withAdviserFor(base, base.other, card,
      Orientation.FaceUp), base.other, provisions, Orientation.FaceUp))(
      _.favor(base.other, 2))
    val run = commit(rules(winning), b, 3, raid = true)
    assert(awaits(run, b.other, CampaignIds.defenderPlan))
    assert(!run.offered(b.actor).contains(ref))

  test("a bandit defender never applies it, since it costs"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winning), b, 2).finish.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
