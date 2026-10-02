package oathdigital.gameplay.powers.setup

import oathdigital.catalog.{Edifice, EdificeFace, ExecutableCatalog, Locked,
  PrintedPower}
import oathdigital.gameplay.powerresolver.{Contribution, ContributingPower, PowerCtx, Transform}
import oathdigital.model._

object GreatMarketCard extends Edifice(EdificeId("E02"), Suit.Discord):
  object intact extends EdificeFace("Great Market") with Locked:
    val power = PrintedPower(PowerId("edifice.e02.intact"),
      persistent = false, cost = Cost.free,
      text = "**SETUP (END) / WHEN EXPLORED:** Place [favor] on this site " +
        "for each denizen _(including this)_ in this region. " +
        "_Players may take it in Wake._")
    val powers: Vector[PrintedPower] = Vector(power)
  object ruined extends EdificeFace("Bandit Market"):
    val power = PrintedPower(PowerId("edifice.e02.ruined"),
      persistent = false, cost = Cost.free,
      text = "**SETUP (END) / WHEN EXPLORED:** Place [favor] on each site " +
        "ruled by the bandits. _(Players may take it in Wake.)_ Burn " +
        "[favor-burnt] from each favor bank.")
    val powers: Vector[PrintedPower] = Vector(power)

/** E02, both faces (2026-09-21 Chronicle design, "Setup powers"). Each names
  * `PowerWindow.WhenExplored` alongside `SetupEnd` so the same contribution
  * will serve WHEN EXPLORED once an explore procedure exists to fire it;
  * nothing folds that window yet, so only the SETUP path runs or is tested.
  */
sealed abstract class MarketRule extends ContributingPower:
  def catalog: ExecutableCatalog
  def edifice: EdificeId
  protected def side: EdificeSide

  final def source: RuleSourceRef = RuleSourceRef.GameRule(id.value)

  private def at(ready: ReadyGame): Option[SiteId] =
    EdificeSetupSupport.siteOf(ready, edifice, side)

  override def applicable(ctx: PowerCtx): Boolean = at(ctx.state).isDefined

  protected def build(ready: ReadyGame, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]]

  /** The line this face writes after its effect. */
  protected def note(at: SiteId): Note

  final def contributions: Map[PowerWindow, Vector[Contribution]] =
    val effect = Vector(Transform((ctx, ops) => at(ctx.state) match {
      case Some(site) =>
        ops :+ BuildOps((ready, _) => build(ready, site)) :+ note(site)
      case None => ops
    }))
    Map(PowerWindow.SetupEnd -> effect, PowerWindow.WhenExplored -> effect)
object MarketRule:
  /** The favor on `site` in `ready`. */
  def siteFavor(ready: ReadyGame, site: SiteId): Int =
    ready.game.current.map.sites.get(site).fold(0)(_.tokens.favor)

  def favor(amount: Int): NoteArg = NoteArg.Amount(amount, NoteUnit.Favor)

final case class GreatMarket private (catalog: ExecutableCatalog)
    extends MarketRule:
  val edifice: EdificeId = GreatMarketCard.id
  def id: PowerId = GreatMarket.id
  protected def side: EdificeSide = EdificeSide.Intact

  override def noteKeys: Vector[NoteKey] = Vector(GreatMarket.placed)

  protected def note(at: SiteId): Note = Note(id, states => for
    step <- states.previous
    placed = MarketRule.siteFavor(step._2, at) - MarketRule.siteFavor(step._1, at)
    if placed > 0
  yield GreatMarket.placed(PowerSourceRef.Card(edifice), MarketRule.favor(placed),
    NoteArg.Site(at)))

  protected def build(ready: ReadyGame, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] = for
    suit <- catalog.suitOf(edifice).toRight(OathViolation.UnknownEdifice(edifice))
    region <- ready.game.current.map.regionOf(at).toRight(
      OathViolation.InvalidEventOrder(s"${at.value} is not in play"))
  yield
    val current = ready.game.current
    val count = current.map.inPlay.filter(s => current.map.regionOf(s).contains(region))
      .flatMap(s => current.map.sites(s).denizens).size
    if count == 0 then Vector.empty
    else Vector(Move(Piece.Favor(count),
      PositionedLocation(Location.FavorBank(suit)), PositionedLocation(Location.Site(at))))
object GreatMarket:
  val id: PowerId = GreatMarketCard.intact.power.id
  /** "Placed {3 favor} on {site}." */
  val placed: NoteKey = NoteKey("placed", Vector(NotePart.Text("Placed "),
    NotePart.Arg(0), NotePart.Text(" on "), NotePart.Arg(1), NotePart.Text(".")))
  def forCatalog(catalog: ExecutableCatalog): GreatMarket =
    new GreatMarket(catalog)

final case class BanditMarket private (catalog: ExecutableCatalog)
    extends MarketRule:
  val edifice: EdificeId = GreatMarketCard.id
  def id: PowerId = BanditMarket.id
  protected def side: EdificeSide = EdificeSide.Ruined

  override def noteKeys: Vector[NoteKey] =
    Vector(BanditMarket.placedAndBurned, BanditMarket.placed, BanditMarket.burned)

  /** Each bandit site took one favor; the banks lost that and the burn. */
  protected def note(at: SiteId): Note = Note(id, states =>
    states.previous.flatMap { case (before, after) =>
      val placed = after.game.current.map.sites.keysIterator.map(site =>
        MarketRule.siteFavor(after, site) - MarketRule.siteFavor(before, site))
        .filter(_ > 0).sum
      val drained = Suit.all.map(suit => before.banks.favor.getOrElse(suit, 0) -
        after.banks.favor.getOrElse(suit, 0)).sum
      val burned = drained - placed
      val card = PowerSourceRef.Card(edifice)
      if placed > 0 && burned > 0 then Some(BanditMarket.placedAndBurned(card,
        MarketRule.favor(placed), MarketRule.favor(burned)))
      else if placed > 0 then Some(BanditMarket.placed(card, MarketRule.favor(placed)))
      else if burned > 0 then Some(BanditMarket.burned(card, MarketRule.favor(burned)))
      else None
    })

  protected def build(ready: ReadyGame, at: SiteId)
      : Either[OathViolation, Vector[CoreOperation]] =
    catalog.suitOf(edifice).toRight(OathViolation.UnknownEdifice(edifice)).map { suit =>
      val current = ready.game.current
      val bandited = current.map.inPlay.filter(s => SiteRule.ruler(
        current.map.sites(s).forces, current.players).contains(SiteRuler.Bandits))
      val placed: Vector[CoreOperation] = bandited.map(s => Move(Piece.Favor(1),
        PositionedLocation(Location.FavorBank(suit)), PositionedLocation(Location.Site(s))))
      val burned: Vector[CoreOperation] = Suit.all.map(bank =>
        Burn.favor(1, PositionedLocation(Location.FavorBank(bank))))
      placed ++ burned
    }
object BanditMarket:
  val id: PowerId = GreatMarketCard.ruined.power.id
  /** "Placed {2 favor} on the bandit sites and burned {6 favor} from the
    * banks." */
  val placedAndBurned: NoteKey = NoteKey("placed-and-burned", Vector(
    NotePart.Text("Placed "), NotePart.Arg(0),
    NotePart.Plural(0, " on the bandit site", " on the bandit sites"),
    NotePart.Text(" and burned "), NotePart.Arg(1),
    NotePart.Text(" from the banks.")))
  /** "Placed {2 favor} on the bandit sites." */
  val placed: NoteKey = NoteKey("placed", Vector(NotePart.Text("Placed "),
    NotePart.Arg(0),
    NotePart.Plural(0, " on the bandit site.", " on the bandit sites.")))
  /** "Burned {6 favor} from the banks." */
  val burned: NoteKey = NoteKey("burned", Vector(NotePart.Text("Burned "),
    NotePart.Arg(0), NotePart.Text(" from the banks.")))
  def forCatalog(catalog: ExecutableCatalog): BanditMarket =
    new BanditMarket(catalog)
