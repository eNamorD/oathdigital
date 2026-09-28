package oathdigital.testkit

import oathdigital.catalog.ExecutableCatalog
import oathdigital.gameplay.setup.{FirstGameSetupFixture, GameStartRules}
import oathdigital.model._
import oathdigital.model.OathState.Ready

/** Names as the catalog prints them, resolved to ids. An unknown name fails
  * the test and lists the five closest names, so a typo reads as one. */
object CatalogNames:
  private def catalog: ExecutableCatalog = FirstGameSetupFixture.catalog

  def site(site: String | SiteId)(using munit.Location): SiteId = site match
    case id: SiteId => id
    case name: String => catalog.sites.find(_.name == name).map(_.id)
      .getOrElse(unknown("site", name, catalog.sites.map(_.name)))

  def denizen(card: String | DenizenId)(using munit.Location): DenizenId =
    card match
      case id: DenizenId => id
      case name: String => catalog.denizens.find(_.name == name)
        .map(d => DenizenId(d.id.value))
        .getOrElse(unknown("denizen", name, catalog.denizens.map(_.name)))

  def worldCard(card: String | WorldCardId)(using munit.Location): WorldCardId =
    card match
      case id: WorldCardId => id
      case name: String => denizen(name)

  def relic(card: String | RelicId)(using munit.Location): RelicId = card match
    case id: RelicId => id
    case name: String => catalog.relics.find(_.name == name)
      .map(r => RelicId(r.id.value))
      .getOrElse(unknown("relic", name, catalog.relics.map(_.name)))

  /** An edifice by either face's name. */
  def edifice(card: String | EdificeId)(using munit.Location): EdificeId =
    card match
      case id: EdificeId => id
      case name: String => catalog.edifices
        .find(e => e.intact.name == name || e.ruined.name == name)
        .map(e => EdificeId(e.id.value))
        .getOrElse(unknown("edifice", name, catalog.edifices.flatMap(e =>
          Vector(e.intact.name, e.ruined.name))))

  /** A denizen, relic or edifice by name. */
  def card(card: String | CardId)(using munit.Location): CardId = card match
    case id: CardId => id
    case name: String =>
      catalog.denizens.find(_.name == name).map(d => DenizenId(d.id.value): CardId)
        .orElse(catalog.relics.find(_.name == name).map(r => RelicId(r.id.value)))
        .orElse(catalog.edifices.find(e => e.intact.name == name ||
          e.ruined.name == name).map(e => EdificeId(e.id.value)))
        .getOrElse(unknown("card", name, catalog.denizens.map(_.name) ++
          catalog.relics.map(_.name) ++ catalog.edifices.flatMap(e =>
            Vector(e.intact.name, e.ruined.name))))

  /** The printed name of `card`, for failure messages. */
  def nameOf(card: CardId): String = card match
    case DenizenId(id) => catalog.denizens.find(_.id.value == id).fold(id)(_.name)
    case RelicId(id) => catalog.relics.find(_.id.value == id).fold(id)(_.name)
    case EdificeId(id) => catalog.edifices.find(_.id.value == id)
      .fold(id)(_.intact.name)
    case other => other.toString

  private def unknown(kind: String, name: String, names: Vector[String])(
      using munit.Location): Nothing =
    val closest = names.sortBy(distance(name.toLowerCase, _)).take(5)
    munit.Assertions.fail(
      s"no $kind named \"$name\"; closest: ${closest.mkString(", ")}")

  private def distance(a: String, b0: String): Int =
    val b = b0.toLowerCase
    val row = Array.tabulate(b.length + 1)(identity)
    for i <- 1 to a.length do
      var diagonal = row(0)
      row(0) = i
      for j <- 1 to b.length do
        val above = row(j)
        row(j) = math.min(math.min(row(j) + 1, row(j - 1) + 1),
          diagonal + (if a(i - 1) == b(j - 1) then 0 else 1))
        diagonal = above
    row(b.length)

/** A game state assembled directly for a rule test, not reached by play
  * (`CONTEXT.md`, "Table").
  *
  * [[Table.start]] is the quiet table: the fixture's real first-game start
  * from `GameStartRules`, with no setup walk. p1, p2 and p3 sit in that turn
  * order, and it is p1's Act. Their pawns stand at Ancient City, Broken
  * Peaks and Buried Giant. No site holds a denizen, edifice, relic, bandit
  * or wealth, nobody holds an adviser, and every board keeps its printed
  * start: 1 favor, 1 faceup secret, 3 warbands and 7 Supply. Nobody holds
  * the Oathkeeper title or a banner, and the regional discards are empty.
  * Ancient City carries the River site power, a Wake option: a test that
  * puts p1 in Wake there has something to decide.
  *
  * Each step states one fact. A step that places a card first takes it out
  * of every zone that held it, so the inventory stays whole. A card the
  * first game did not deal joins the table. `ready` checks the card index
  * and the warband inventory and fails the test naming what is wrong. A
  * card added through `update` without a step is not checked, since only
  * lost and doubled cards are visible to the index.
  */
final case class Table private (private val game: ReadyGame,
    private val joined: Set[CardId]):
  import Table.*

  def turn(player: PlayerId, phase: Phase): Table =
    update(_.updateCurrent(_.copy(turn = TurnState(player, phase, Set.empty))))

  def pawn(player: PlayerId, at: String | SiteId)(using munit.Location): Table =
    val site = CatalogNames.site(at)
    onPlayer(player)(_.copy(pawnSite = Some(site)))

  /** `card` as `player`'s adviser: a denizen, or a Vision by its id. */
  def adviser(player: PlayerId, card: String | WorldCardId,
      facedown: Boolean = false)(using munit.Location): Table =
    val id = CatalogNames.worldCard(card)
    val state: AdviserState = id match
      case denizen: DenizenId => DenizenState(denizen, orientation(facedown),
        Tokens.empty)
      case vision: VisionId => VisionState(vision, orientation(facedown))
    moving(id).onPlayer(player)(p => p.copy(advisers = p.advisers :+ state))

  def relic(player: PlayerId, card: String | RelicId,
      facedown: Boolean = false)(using munit.Location): Table =
    val id = CatalogNames.relic(card)
    moving(id).onPlayer(player)(p => p.copy(relics = p.relics :+
      RelicState(id, orientation(facedown), Tokens.empty)))

  def denizen(card: String | DenizenId, at: String | SiteId,
      facedown: Boolean = false)(using munit.Location): Table =
    val id = CatalogNames.denizen(card)
    moving(id).onSite(CatalogNames.site(at))(s => s.copy(denizens =
      s.denizens :+ DenizenState(id, orientation(facedown), Tokens.empty)))

  /** A relic at a site. Relics at sites lie facedown in play. */
  def relicAt(card: String | RelicId, at: String | SiteId,
      facedown: Boolean = true)(using munit.Location): Table =
    val id = CatalogNames.relic(card)
    moving(id).onSite(CatalogNames.site(at))(s => s.copy(relics = s.relics :+
      RelicState(id, orientation(facedown), Tokens.empty)))

  /** An edifice at a site on `side`. Named by a face, it must be the face
    * `side` shows. */
  def edifice(card: String | EdificeId, side: EdificeSide,
      at: String | SiteId)(using munit.Location): Table =
    val id = CatalogNames.edifice(card)
    card match
      case name: String =>
        val definition = FirstGameSetupFixture.catalog.edifice(id).get
        val shown = side match
          case EdificeSide.Intact => definition.intact.name
          case EdificeSide.Ruined => definition.ruined.name
        if shown != name then munit.Assertions.fail(
          s"\"$name\" is not the $side face; that face is \"$shown\"")
      case _ => ()
    moving(id).onSite(CatalogNames.site(at))(s => s.copy(denizens =
      s.denizens :+ EdificeState(id, side, Tokens.empty)))

  /** Sets the tokens on `card`, wherever it sits. */
  def tokens(card: String | CardId, favor: Int = 0, secrets: Int = 0)(
      using munit.Location): Table =
    val id = CatalogNames.card(card)
    val set = Tokens(favor, secrets)
    update(_.updateCurrent(c => c.copy(
      map = c.map.copy(sites = c.map.sites.view.mapValues(s => s.copy(
        denizens = s.denizens.map {
          case d: DenizenState if d.id == id => d.copy(tokens = set)
          case e: EdificeState if e.id == id => e.copy(tokens = set)
          case other => other },
        relics = s.relics.map(r => if r.id == id then r.copy(tokens = set)
          else r))).toMap),
      players = c.players.map(p => p.copy(
        advisers = p.advisers.map {
          case d: DenizenState if d.id == id => d.copy(tokens = set)
          case other => other },
        relics = p.relics.map(r => if r.id == id then r.copy(tokens = set)
          else r))))))

  /** `cards` in `player`'s hand, after any already there: the hand a
    * Search draws into and a card play takes from. */
  def hand(player: PlayerId, cards: (String | WorldCardId)*)(
      using munit.Location): Table =
    val ids = cards.toVector.map(CatalogNames.worldCard)
    ids.foldLeft(this)(_.moving(_)).update(_.updateCurrent(c => c.copy(
      temporaryHands = c.temporaryHands.updated(player,
        c.temporaryHands.getOrElse(player, Vector.empty) ++ ids))))

  /** `cards` in `region`'s discard pile, in order: the last named is its
    * top. */
  def discarded(region: Region, cards: (String | WorldCardId)*)(
      using munit.Location): Table =
    val ids = cards.toVector.map(CatalogNames.worldCard)
    ids.foldLeft(this)(_.moving(_)).update(_.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(regionalDiscards =
        c.commonCards.regionalDiscards.updated(region,
          c.commonCards.discard(region) ++ ids)))))

  /** These cards on top of the world deck, first named on top. */
  def worldDeckTop(cards: (String | WorldCardId)*)(using munit.Location): Table =
    val ids = cards.toVector.map(CatalogNames.worldCard)
    ids.foldLeft(this)(_.moving(_)).update(_.updateCurrent(c => c.copy(
      commonCards = c.commonCards.copy(worldDeck = ids ++
        c.commonCards.worldDeck))))

  def favor(player: PlayerId, n: Int): Table =
    onBoard(player)(_.copy(favor = n))
  def secrets(player: PlayerId, faceUp: Int, faceDown: Int = 0): Table =
    onBoard(player)(_.copy(faceUpSecrets = faceUp, faceDownSecrets = faceDown))
  def supply(player: PlayerId, n: Int): Table =
    onBoard(player)(_.copy(supply = SupplyTrack(n)))
  def warbands(player: PlayerId, n: Int): Table =
    onBoard(player)(_.copy(warbands = n))

  def bandits(at: String | SiteId, n: Int)(using munit.Location): Table =
    forces(at, n, ForceKind.Bandit)

  /** `n` of `owner`'s warbands at a site, which `owner` then rules. */
  def warbandsAt(at: String | SiteId, owner: PlayerId, n: Int)(
      using munit.Location): Table =
    forces(at, n, PlayerForceKind.of(game, playerOf(owner)).getOrElse(
      munit.Assertions.fail(s"$owner has no warbands of their own")))

  def siteTokens(at: String | SiteId, favor: Int = 0, secrets: Int = 0)(
      using munit.Location): Table =
    onSite(CatalogNames.site(at))(_.copy(tokens = Tokens(favor, secrets)))

  def peoplesFavor(holder: Option[PlayerId], favor: Int): Table =
    update(_.updateCurrent(c => c.copy(banners = c.banners.copy(peoplesFavor =
      c.banners.peoplesFavor.copy(holder = holder, favor = favor)))))

  def darkestSecret(holder: Option[PlayerId], secrets: Int): Table =
    update(_.updateCurrent(c => c.copy(banners = c.banners.copy(darkestSecret =
      c.banners.darkestSecret.copy(holder = holder, secrets = secrets)))))

  /** `viewer` knows `card`, a relic lying at `at`, as a peek records it. */
  def knowsRelicAt(viewer: PlayerId, card: String | RelicId,
      at: String | SiteId)(using munit.Location): Table =
    val id = CatalogNames.relic(card)
    val site = CatalogNames.site(at)
    if !game.game.current.map.sites(site).relics.exists(_.id == id) then
      munit.Assertions.fail(s"${CatalogNames.nameOf(id)} is not at $site")
    update(r => r.copy(knowledge = r.knowledge.copy(siteRelics =
      r.knowledge.siteRelics.updated(viewer, r.knowledge.siteRelics
        .getOrElse(viewer, Map.empty).updatedWith(site)(known =>
          Some(known.getOrElse(Vector.empty) :+ id))))))

  def oathkeeper(holder: Option[PlayerId],
      side: TitleSide = TitleSide.Oathkeeper): Table =
    update(_.updateCurrent(_.copy(title = OathkeeperState(holder, side))))

  def bankFavor(suit: Suit, n: Int): Table =
    update(r => r.copy(banks = r.banks.copy(favor = r.banks.favor.updated(suit, n))))

  /** Last resort, for a fact no step states. Say why at the call site; a
    * fact three suites need becomes a step. */
  def update(f: ReadyGame => ReadyGame): Table = copy(game = f(game))

  /** The state, after checking that no card is lost or in two places and
    * that no force outnumbers its printed supply. */
  def ready(using munit.Location): ReadyGame =
    CardIndex.from(game.game, inventory ++ joined).left.foreach { problems =>
      munit.Assertions.fail("the table's cards are not whole: " +
        problems.map(describe).mkString("; "))
    }
    val placed = game.game.current.map.sites.values.map(_.forces).collect {
      case SiteForces.Occupied(kind, _) => kind }.toSet
    (placed -- game.banks.warbandSupply.keySet).foreach(kind =>
      munit.Assertions.fail(s"$kind has warbands at a site but no printed supply"))
    game.banks.warbandSupply.foreach { case (kind, printed) =>
      val inPlay = game.game.current.players.filter(p =>
        PlayerForceKind.of(game, p).contains(kind)).map(_.board.warbands).sum +
        game.game.current.map.sites.values.map(_.forces).collect {
          case SiteForces.Occupied(`kind`, count) => count }.sum
      if inPlay > printed then munit.Assertions.fail(
        s"$kind: $inPlay warbands in play, more than the $printed printed")
    }
    game

  def state(using munit.Location): OathState = Ready(ready)

  /** The state with nothing checked, for a fixture helper that wraps a step
    * inside a state its caller is still assembling by hand (a card taken out
    * to be put back later, say). A test that builds a table itself reads it
    * with `ready`. */
  def unchecked: ReadyGame = game

  /** A situation at this table, driven by `driver`, which must be a rules
    * adapter: a journal's stream begins with `GameStarted`, so a journaled
    * situation cannot start here. */
  def situation(driver: SituationDriver)(using munit.Location): Situation =
    driver match
      case _: SituationDriver.Journaled => munit.Assertions.fail(
        "a Table has no journal; start a journaled situation with " +
          "Situation.wake")
      case rules => Situation(state, Vector.empty, 0L, rules)

  private def moving(card: CardId): Table =
    Table(without(game, card), if inventory(card) then joined else joined + card)

  private def onPlayer(player: PlayerId)(f: PlayerState => PlayerState): Table =
    update(_.updateCurrent(c => c.copy(players = c.players.map(p =>
      if p.player == player then f(p) else p))))

  private def onBoard(player: PlayerId)(
      f: PlayerBoardState => PlayerBoardState): Table =
    onPlayer(player)(p => p.copy(board = f(p.board)))

  private def onSite(site: SiteId)(f: SiteState => SiteState): Table =
    update(_.updateCurrent(c => c.copy(map = c.map.copy(sites =
      c.map.sites.updated(site, f(c.map.sites(site)))))))

  private def forces(at: String | SiteId, n: Int, kind: ForceKind)(
      using munit.Location): Table =
    onSite(CatalogNames.site(at))(_.copy(forces =
      if n == 0 then SiteForces.Empty else SiteForces.Occupied(kind, n)))

  private def playerOf(player: PlayerId)(using munit.Location): PlayerState =
    game.game.current.players.find(_.player == player).getOrElse(
      munit.Assertions.fail(s"no player $player at the table"))

object Table:
  val p1: PlayerId = PlayerId("p1")
  val p2: PlayerId = PlayerId("p2")
  val p3: PlayerId = PlayerId("p3")

  private val homes: Map[PlayerId, String] = Map(
    p1 -> "Ancient City", p2 -> "Broken Peaks", p3 -> "Buried Giant")

  /** Where `player`'s pawn stands on the quiet table. */
  def homeOf(player: PlayerId)(using munit.Location): SiteId =
    CatalogNames.site(homes.getOrElse(player, munit.Assertions.fail(
      s"no player $player at the table")))

  /** Steps continued from `ready`, a state a test or a fixture already
    * holds: its cards are checked against the first game's inventory as the
    * quiet table's are. For the helpers of fixtures built on the table. */
  def from(ready: ReadyGame): Table = Table(ready, Set.empty)

  /** The quiet table (see [[Table]]). */
  lazy val start: Table =
    val orders = FirstGameSetupFixture.orders.copy(firstPlayer = p1)
    val fresh = GameStartRules.evolve(FirstGameSetupFixture.catalog,
      FirstGameSetupFixture.chronicle, orders).fold(violation =>
        throw AssertionError(s"the fixture's first game does not start: $violation"),
        identity)
    Table(quiet(fresh), Set.empty)

  /** Every card the fixture's first game deals, wherever it lies. */
  private lazy val inventory: Set[CardId] =
    CardIndex.from(start.game.game).toOption.get.ids

  private def orientation(facedown: Boolean): Orientation =
    if facedown then Orientation.FaceDown else Orientation.FaceUp

  /** The start with every hand, discard, site card, relic, force and site
    * wealth put back: dealt hands, the seeded regional discards and site
    * denizens to the bottom of the world deck, edifices to the edifice deck, relics to the bottom of the relic
    * deck. `banks.warbandSupply` is the printed inventory and the bank holds
    * whatever is not in play, so clearing a site's forces returns them to
    * the bank with no change there. Pawns go to [[homes]], and the turn is
    * p1's Act. */
  private def quiet(fresh: ReadyGame): ReadyGame = fresh.updateCurrent { c =>
    val sites = c.map.inPlay.map(c.map.sites)
    val hands = c.players.flatMap(p => c.temporaryHands.getOrElse(p.player,
      Vector.empty))
    val denizens = sites.flatMap(_.denizens).collect { case d: DenizenState => d.id }
    val edifices = sites.flatMap(_.denizens).collect { case e: EdificeState => e.id }
    val relics = sites.flatMap(_.relics).map(_.id)
    val discards = Region.all.flatMap(c.commonCards.discard)
    c.copy(
      temporaryHands = Map.empty,
      commonCards = c.commonCards.copy(
        worldDeck = c.commonCards.worldDeck ++ hands ++ discards ++ denizens,
        regionalDiscards = Region.all.map(_ -> Vector.empty[WorldCardId]).toMap,
        edificeDeck = c.commonCards.edificeDeck ++ edifices,
        relicDeck = c.commonCards.relicDeck ++ relics),
      map = c.map.copy(sites = c.map.sites.view.mapValues(_ =>
        SiteState(SiteForces.Empty, Vector.empty, Vector.empty, Tokens.empty))
        .toMap),
      players = c.players.map(p => p.copy(pawnSite = Some(
        c.map.inPlay.find(id => FirstGameSetupFixture.catalog.sites
          .exists(s => s.id == id && s.name == homes(p.player)))
          .getOrElse(throw AssertionError(
            s"${homes(p.player)} is not in the fixture's first game"))))),
      turn = TurnState(p1, Phase.Act, Set.empty))
  }

  /** `ready` with `card` taken out of every zone and every viewer's memory
    * of it. */
  private def without(ready: ReadyGame, card: CardId): ReadyGame =
    def keep(id: CardId): Boolean = id != card
    val c = ready.game.current
    val zones = c.commonCards
    val current = c.copy(
      commonCards = zones.copy(
        worldDeck = zones.worldDeck.filter(keep),
        relicDeck = zones.relicDeck.filter(keep),
        edificeDeck = zones.edificeDeck.filter(keep),
        legacyDeck = zones.legacyDeck.filter(keep),
        regionalDiscards = zones.regionalDiscards.view.mapValues(
          _.filter(keep)).toMap),
      map = c.map.copy(sites = c.map.sites.view.mapValues(s => s.copy(
        denizens = s.denizens.filter(d => keep(d.id)),
        relics = s.relics.filter(r => keep(r.id)))).toMap),
      players = c.players.map(p => p.copy(
        advisers = p.advisers.filter(a => keep(a.id)),
        relics = p.relics.filter(r => keep(r.id)),
        revealedVision = p.revealedVision.filter(v => keep(v.id)))),
      temporaryHands = c.temporaryHands.view.mapValues(_.filter(keep)).toMap,
      setAsideRelics = c.setAsideRelics.filter(keep))
    val campaign = ready.game.campaign
    ready.copy(
      game = ready.game.copy(current = current, campaign = campaign.copy(
        reliquary = campaign.reliquary.filter(keep),
        dispossessed = campaign.dispossessed.filter(keep),
        suitedReserves = campaign.suitedReserves.view.mapValues(
          _.filter(keep)).toMap,
        lineages = campaign.lineages.view.mapValues(l => l.copy(
          legacies = l.legacies.filter(x => keep(x.id)),
          startingAdvisers = l.startingAdvisers.filter(a => keep(a.id))))
          .toMap,
        atlas = AtlasState(campaign.atlas.entries.map {
          case s: AtlasEntry.StoredSite => s.copy(
            denizens = s.denizens.filter(d => keep(d.id)),
            relics = s.relics.filter(r => keep(r.id)),
            edifice = s.edifice.filter(keep))
          case other => other
        }))),
      knowledge = ready.knowledge.copy(
        siteRelics = ready.knowledge.siteRelics.view.mapValues(
          _.view.mapValues(_.filter(keep)).toMap).toMap,
        advisers = ready.knowledge.advisers.view.mapValues(_.filter(keep)).toMap,
        heldRelics = ready.knowledge.heldRelics.view.mapValues(
          _.filter(keep)).toMap))

  private def describe(problem: CardIndexProblem): String = problem match
    case CardIndexProblem.DuplicateCard(id, locations) =>
      s"${CatalogNames.nameOf(id)} is in ${locations.size} places: " +
        locations.mkString(", ")
    case CardIndexProblem.MissingCard(id) =>
      s"${CatalogNames.nameOf(id)} is missing"
