package oathdigital.frontend

import scala.collection.mutable.ArrayBuffer

/** Builders for the sites a render suite draws. A site starts quiet: three
  * denizen slots, no relic slots, nothing on it, no defense. A test adds
  * exactly the facts its outcome depends on, one step each.
  */
private[frontend] object TestSites:
  def quietSite(id: String, label: String): GameSite =
    GameSite(id, label, looseFavor = 0, looseSecrets = 0, denizenCapacity = 3,
      relicCapacity = 0, denizens = Vector.empty, relics = GameSiteRelics(0),
      defense = 0)

  extension (site: GameSite)
    def withDenizens(cards: GameSiteCard*): GameSite =
      site.copy(denizens = cards.toVector)
    def withDenizenCapacity(slots: Int): GameSite =
      site.copy(denizenCapacity = slots)
    def withRelicCapacity(slots: Int): GameSite =
      site.copy(relicCapacity = slots)

    /** `facedown` relics lie on the site; `known` of them are readable by
      * the viewer.
      */
    def withRelics(facedown: Int, known: CardDetails*): GameSite =
      site.copy(relics = GameSiteRelics(facedown, known.toVector))
    def withLooseTokens(favor: Int, secrets: Int): GameSite =
      site.copy(looseFavor = favor, looseSecrets = secrets)
    def withDefense(defense: Int): GameSite = site.copy(defense = defense)

/** Builders for the player boards a render suite draws. A board starts quiet:
  * no warbands, favor, secrets or Supply, no cards, standing nowhere. A test
  * adds exactly the facts its outcome depends on, one step each.
  */
private[frontend] object TestBoards:
  def quietBoard(playerId: String): PlayerBoard =
    PlayerBoard(playerId, warbands = 0, favor = 0, faceUpSecrets = 0,
      faceDownSecrets = 0, committedSecrets = 0, totalSecrets = 0, supply = 0,
      pawnSiteId = None, advisers = Vector.empty, relics = Vector.empty,
      revealedVision = None)

  extension (board: PlayerBoard)
    def withWarbands(count: Int): PlayerBoard = board.copy(warbands = count)
    def withFavor(count: Int): PlayerBoard = board.copy(favor = count)
    def withSupply(count: Int): PlayerBoard = board.copy(supply = count)
    def standingAt(siteId: String): PlayerBoard =
      board.copy(pawnSiteId = Some(siteId))

    /** The secrets the player owns; the total is their sum. */
    def withSecrets(faceUp: Int, faceDown: Int, committed: Int = 0)
        : PlayerBoard =
      board.copy(faceUpSecrets = faceUp, faceDownSecrets = faceDown,
        committedSecrets = committed,
        totalSecrets = faceUp + faceDown + committed)
    def withAdvisers(cards: CardDetails*): PlayerBoard =
      board.copy(advisers = cards.toVector)
    def withRelics(cards: CardDetails*): PlayerBoard =
      board.copy(relics = cards.toVector)

/** A set of board options, and the picks a click on the table records in it. */
private[frontend] final case class RecordingOptions(options: BoardOptions,
    recorded: ArrayBuffer[DecisionOptionState]):
  def picks: Vector[DecisionOptionState] = recorded.toVector

private[frontend] object TestBoardOptions:
  /** An option of `kind` whose label is its id. */
  def option(kind: String, id: String): DecisionOptionState =
    DecisionOptionState(kind, id, id)

  def available(option: DecisionOptionState): OptionView =
    OptionView(option, OptionState.Available, None)

  /** Board options for `views`, each at its option's home on the table, and
    * the picks they record.
    */
  def recording(views: OptionView*): RecordingOptions =
    val recorded = ArrayBuffer.empty[DecisionOptionState]
    RecordingOptions(new BoardOptions(views.map(view =>
      BoardOptionViews.home(view.option) -> view).toMap,
      chosen => { recorded += chosen; () }), recorded)
