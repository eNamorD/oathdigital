package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.CampaignFixture._
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.NoteText
import oathdigital.gameplay.powers.campaign.PlanDriver._
import oathdigital.gameplay.walker.WalkerDice
import oathdigital.model._

/** Bag of Siegeworks: a secret placed, in a Conquest, so that every
  * single-shield defense die scores nothing. */
class BagOfSiegeworksSuite extends munit.FunSuite:
  private val relic = relicWith("relic.bag-of-siegeworks")
  private val ref: DecisionOptionRef = DecisionOptionRef.Relic(RelicId(relic))
  private val line = NoteText.Said("ignored", "Single shields ignored.",
    covers = false)

  private val pattern = Vector(DefenseDieFace.OneShield,
    DefenseDieFace.TwoShields, DefenseDieFace.Doubler, DefenseDieFace.Blank)

  /** Every attack die a sword; the defense dice cycle through `pattern`. */
  private val shields: WalkerDice = (kind, count) => Right(kind match
    case DiceKind.Attack => Vector.fill(count)(AttackDieFace.OneSword: DieFace)
    case DiceKind.Defense =>
      Vector.tabulate(count)(index => pattern(index % pattern.size): DieFace))

  /** The attacker holds the Bag and a secret; two further bandit sites can be
    * targeted with the origin, so the defense rolls at least three dice. */
  private def holder: Board = withSecrets(withRelic(board(extras = 2), relic), 1)

  private def conquest(b: Board, dice: WalkerDice): Run =
    commit(rules(dice), b, 5, targets = b.extras.map(DecisionOptionRef.Site(_)))

  private def lines(run: Run): Vector[NoteText.Said] = NoteText.said(
    BagOfSiegeworks.id, Vector(BagOfSiegeworks.ignored), run.events)

  test("a single shield scores nothing; two shields and doublers score as usual"):
    import DefenseDieFace._
    assertEquals(BagOfSiegeworks.score(Vector(Blank, OneShield, Doubler)), 0)
    assertEquals(BagOfSiegeworks.score(Vector(TwoShields, OneShield, Doubler)), 4)
    assertEquals(BagOfSiegeworks.score(Vector(OneShield, OneShield)), 0)

  test("an attacker places a secret on the relic"):
    val b = holder
    val run = conquest(b, shields)
    assert(awaits(run, b.actor, CampaignIds.attackerPlan))
    val picked = run.pick(b.actor, CampaignIds.attackerPlan, ref)
    assertEquals(player(picked.state, b.actor).board.faceUpSecrets, 0)

  test("the defense is scored without its single shields, and it says so"):
    val b = holder
    val done = conquest(b, shields).pick(b.actor, CampaignIds.attackerPlan, ref)
      .finish
    val result = ready(done.state).game.current.lastCampaignResult.get
    assert(result.defenseFaces.contains(DefenseDieFace.OneShield),
      result.defenseFaces)
    val force = 2 * (1 + b.extras.size)
    assertEquals(result.defenseScore,
      BagOfSiegeworks.score(result.defenseFaces) + force)
    assert(result.defenseScore < DefenseDieFace.score(result.defenseFaces) + force)
    assertEquals(lines(done), Vector(line))

  test("without a single shield rolled nothing is said"):
    val b = holder
    val done = conquest(b, winning).pick(b.actor, CampaignIds.attackerPlan, ref)
      .finish
    assertEquals(lines(done), Vector.empty)

  test("a Campaign that does not choose it scores the single shields"):
    val b = holder
    val done = conquest(b, shields).finish
    val result = ready(done.state).game.current.lastCampaignResult.get
    assertEquals(result.defenseScore,
      DefenseDieFace.score(result.defenseFaces) + 2 * (1 + b.extras.size))
    assertEquals(lines(done), Vector.empty)

  test("a Raid targets no site, so it is not offered"):
    val (raid, _) = raidBoard()
    val b = withSecrets(withRelic(raid, relic), 1)
    val run = commit(rules(winning), b, 2, raid = true)
    assert(!awaits(run, b.actor, CampaignIds.attackerPlan))
