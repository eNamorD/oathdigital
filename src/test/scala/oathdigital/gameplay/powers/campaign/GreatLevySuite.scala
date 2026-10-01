package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.{CampaignIds, CampaignPlans}
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._

/** The Great Levy: two favor placed for three attack dice, added or removed,
  * unless the enemy holds the People's Favor; an attacker also ignores every
  * skull, as with Outriders. */
class GreatLevySuite extends munit.FunSuite:
  private val card = cardWith("denizen.the-great-levy")
  private val id = DenizenId(card)
  private val ref: DecisionOptionRef = DecisionOptionRef.Denizen(id)
  private val outriders = cardWith("denizen.outriders")
  private val outridersRef: DecisionOptionRef =
    DecisionOptionRef.Denizen(DenizenId(outriders))
  private val line = NoteText.Said("ignored", "Skulls ignored.", covers = false)

  /** The first attack die two swords and a skull, the rest one sword; every
    * defense die blank. */
  private val skull: WalkerDice = (kind, count) => Right(kind match
    case DiceKind.Attack => Vector.tabulate(count)(index =>
      (if index == 0 then AttackDieFace.TwoSwordsSkull
       else AttackDieFace.OneSword): DieFace)
    case DiceKind.Defense => Vector.fill(count)(DefenseDieFace.Blank: DieFace))

  /** `who` holds The Great Levy and `favor` favor. */
  private def holder(b: Board, who: PlayerId, favor: Int = 2): Board =
    on(withAdviserFor(b, who, card, Orientation.FaceUp))(_.favor(who, favor))

  private def adviserTokens(state: OathState, who: PlayerId): Option[Tokens] =
    player(state, who).advisers.collectFirst {
      case held: DenizenState if held.id == id => held.tokens }

  private def result(run: Run): CampaignResult =
    ready(run.state).game.current.lastCampaignResult.get

  private def lines(run: Run): Vector[NoteText.Said] =
    NoteText.said(GreatLevy.id, Vector(Outriders.ignored), run.events)

  test("an attacker places two favor for three attack dice, ignores every " +
      "skull, and says so"):
    val base = board()
    val b = holder(base, base.actor)
    val run = commit(rules(skull), b, 2)
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assert(picked.since(run).contains(ModifyDicePool(CampaignIds.attackPool, 3)))
    assertEquals(adviserTokens(picked.state, b.actor), Some(Tokens(2, 0)))
    assertEquals(player(picked.state, b.actor).board.favor, 0)
    val done = picked.finish
    assertEquals(result(done).skullLosses, 0)
    assertEquals(result(done).attackScore,
      AttackDieFace.score(result(done).attackFaces))
    assertEquals(lines(done), Vector(line))

  test("it is not offered while the defender holds the People's Favor"):
    val base = againstPlayer(board())
    val funded = holder(base, base.actor)
    assert(commit(rules(winning), funded, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))
    val b = on(funded)(_.peoplesFavor(Some(base.other), 1))
    assert(!commit(rules(winning), b, 3).offers(base.actor, base.actor,
      CampaignIds.attackerPlan, ref))

  test("it is not offered to a player with one favor"):
    val base = board()
    val b = holder(base, base.actor, favor = 1)
    assert(!commit(rules(winning), b, 2).offers(b.actor, b.actor,
      CampaignIds.attackerPlan, ref))

  test("a defender removes three attack dice, and the attack's skulls " +
      "still count"):
    val base = againstPlayer(board())
    val b = holder(base, base.other)
    val run = commit(rules(skull), b, 4)
      .pick(b.other, CampaignIds.defenderPlan, ref)
    assert(run.ops.contains(ModifyDicePool(CampaignIds.attackPool, -3)))
    // One die is left, and it rolls the skull.
    val done = run.finish
    assertEquals(result(done).skullLosses, 1)
    assertEquals(lines(done), Vector.empty)

  test("with Outriders also chosen, the attack is scored the same"):
    val base = board()
    val b = withAdviser(holder(base, base.actor), outriders, Orientation.FaceUp)
    val done = commit(rules(skull), b, 2)
      .pick(b.actor, CampaignIds.attackerPlan, outridersRef)
      .pick(b.actor, CampaignIds.attackerPlan, ref).finish
    assertEquals(result(done).skullLosses, 0)
    assertEquals(result(done).attackScore,
      AttackDieFace.score(result(done).attackFaces))
    assertEquals(lines(done), Vector(line))
    assertEquals(NoteText.said(Outriders.id, Vector(Outriders.ignored),
      done.events), Vector(line))

  test("a bandit defender never applies it, since it costs"):
    val base = board()
    val b = withSiteCard(base, base.origin, card)
    assert(!commit(rules(winning), b, 2).finish.ops.contains(
      ModifyDicePool(CampaignPlans.appliedMarker(ref), 1)))
