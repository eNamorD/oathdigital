package oathdigital.gameplay.powers.targeting

import oathdigital.gameplay.CampaignFixture.raidBoard
import oathdigital.gameplay.actions.VisionRules
import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.gameplay.powers.{CardStaging, NoteText, PowerFixture, WalkerPowerCatalog}
import oathdigital.gameplay.setup.FirstGameSetupFixture.catalog
import oathdigital.gameplay.walker.{ProcedureWalker, WalkerOutcome, WalkerPowers}
import oathdigital.model._
import oathdigital.model.DecisionAnswer.ChooseOneAnswer

class ForgottenVaultSuite extends munit.FunSuite:
  import TargetingFixture._

  private val vault = DenizenId("75")
  private val raid = ChooseOneAnswer(DecisionOptionRef.Button("raid"))
  private val power = ForgottenVault.forCatalog(catalog).get

  private def notes(events: Vector[OathEvent]): Vector[NoteText.Said] =
    NoteText.said(power.id, power.noteKeys, events)

  private def line(ruler: PlayerId): NoteText.Said = NoteText.Said("shielded",
    s"${ruler.value}'s relics cannot be targeted.", covers = false)

  /** The Vault at a site other than `avoid`, ruled by `ruler` (`None` leaves
    * the site's bandits).
    */
  private def vaultAt(state: ReadyGame, avoid: SiteId,
      ruler: Option[PlayerId]): ReadyGame =
    val site = state.game.current.map.inPlay.find(_ != avoid).get
    val placed = PowerFixture.atSite(CardStaging.without(state, vault), vault,
      site)
    ruler.fold(placed)(ruledBy(placed, site, _))

  test("the Vault is a registered persistent rule, so it is automatic"):
    assertEquals(power.cardId, vault)
    assertEquals(power.resolution, PowerResolution.Automatic)

  // ---- Raid ----

  enum Ruler:
    case Defender, Attacker, Bandits

  /** Starts a Raid on the defender with the Vault's site ruled by `ruler`, and
    * answers the kind. Returns the defender, their faceup relic, the Raid's
    * target options and the journal of the answer.
    */
  private def raidTargets(ruler: Ruler)
      : (PlayerId, RelicId, Vector[DecisionOptionRef], Vector[OathEvent]) =
    val (b, relic) = raidBoard()
    val held = vaultAt(b.ready, b.origin, ruler match
      case Ruler.Defender => Some(b.other)
      case Ruler.Attacker => Some(b.actor)
      case Ruler.Bandits => None)
    val started = start(held, ActionRef.Campaign, b.actor).toOption.get
    val kind = rules.resolveWalker(started.state, b.actor, CampaignIds.kind, raid)
      .toOption.get
    (b.other, relic, optionsAt(kind, ActionRef.Campaign), kind.events)

  test("a Raid may not target the ruler's relics, but may target their banners"):
    val (defender, relic, options, events) = raidTargets(Ruler.Defender)
    assert(!options.contains(DecisionOptionRef.Relic(relic)), options.toString)
    assertEquals(options.toSet, Set[DecisionOptionRef](
      DecisionOptionRef.Banner(Banner.PeoplesFavor),
      DecisionOptionRef.Banner(Banner.DarkestSecret)))
    assertEquals(notes(events), Vector(line(defender)))

  test("the Vault's ruler may target an enemy's relics"):
    val (_, relic, options, events) = raidTargets(Ruler.Attacker)
    assert(options.contains(DecisionOptionRef.Relic(relic)))
    assertEquals(notes(events), Vector.empty)

  test("a Vault ruled by bandits protects nothing"):
    val (_, relic, options, _) = raidTargets(Ruler.Bandits)
    assert(options.contains(DecisionOptionRef.Relic(relic)))

  // ---- Conspiracy ----

  private val conspiracy = VisionRules.Conspiracy
  private val other = RelicId("R10")

  /** The actor plays Conspiracy at a site the enemy shares. The enemy holds
    * relic R10, and the People's Favor when `banner`. The Vault stands at
    * another site, ruled by the enemy. Returns the enemy, the target options
    * (empty when no decision was asked) and the journal.
    */
  private def conspiracyTargets(banner: Boolean)
      : (PlayerId, Vector[DecisionOptionRef], Vector[OathEvent]) =
    val base = PowerFixture.base
    val actor = PowerFixture.actor
    val enemy = base.game.current.players.map(_.player).find(_ != actor).get
    val site = PowerFixture.player(base).pawnSite
    val staged = CardStaging.without(CardStaging.without(base, conspiracy), other)
      .updateCurrent(c => c.copy(
        players = c.players.map(p => if p.player == enemy then
          p.copy(pawnSite = site) else p),
        banners = c.banners.copy(
          peoplesFavor = c.banners.peoplesFavor.copy(
            holder = Option.when(banner)(enemy)),
          darkestSecret = c.banners.darkestSecret.copy(holder = None)),
        temporaryHands = c.temporaryHands.updated(actor, Vector(conspiracy))))
    val ready = vaultAt(holds(staged, enemy, other), site.get, Some(enemy))
    val hook = CardPlayedFaceup(conspiracy, RuleSourceRef.Adviser(actor, conspiracy))
    val powers = WalkerPowers.selected(WalkerPowerCatalog.default(catalog),
      Vector.empty)
    ProcedureWalker.advance(ready, hook, None, powers).toOption.get match
      case WalkerOutcome.Parked(pending, events) =>
        (enemy, ProcedureWalker.parkedDecide(ready, hook, pending, powers).get
          .query.asInstanceOf[DecisionQuery.ChooseOne].options.map(_.ref), events)
      case WalkerOutcome.Finished(_, events) => (enemy, Vector.empty, events)

  test("Conspiracy may take the ruler's banner, but not their relic"):
    val (enemy, options, events) = conspiracyTargets(banner = true)
    assertEquals(options, Vector[DecisionOptionRef](
      DecisionOptionRef.Banner(Banner.PeoplesFavor)))
    assertEquals(notes(events), Vector(line(enemy)))

  test("a Conspiracy left with no target asks nothing and still says why"):
    val (enemy, options, events) = conspiracyTargets(banner = false)
    assertEquals(options, Vector.empty)
    assertEquals(notes(events), Vector(line(enemy)))
