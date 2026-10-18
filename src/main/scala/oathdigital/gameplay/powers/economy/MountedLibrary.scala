package oathdigital.gameplay.powers.economy

import oathdigital.catalog.{Denizen, ExecutableCatalog, PrintedPower, SiteOnly}
import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.actions.economy.TradeProcedure
import oathdigital.gameplay.powerresolver.{Contribution, Deferred,
  FoldPhase, OperationRewrite, Transform}
import oathdigital.gameplay.powers.{NoteSupport, PowerAnswers,
  SelectedModifier}
import oathdigital.gameplay.powers.action.SiteCards
import oathdigital.model._

object MountedLibraryCard extends Denizen(DenizenId("248"), "Mounted Library", Suit.Nomad) with SiteOnly:
  val power = PrintedPower(PowerId("denizen.mounted-library"),
    persistent = false, cost = Cost.free,
    text = "If trading on Mounted Library for [secret], gain double the " +
      "[secret], then discard Mounted Library.")
  val powers: Vector[PrintedPower] = Vector(power)

/** Mounted Library (card 248, site-only), a selected Trade modifier with no
  * cost: a Trade for secrets with Mounted Library as its source gains double
  * the secrets, and once selected, Mounted Library is discarded at the end of
  * the Trade, whatever the Trade was.
  *
  * The doubling records the Trade's own secret gain as it runs, and adds a
  * step after it for every Trade that gains as much again, run only when
  * Mounted Library was the source, as The Old Oak's is. Its line covers
  * that second gain's generic line only, so the Trade's own "Gained" line
  * stays and this one follows it.
  *
  * Both rewrites fold `Late`. The discard is appended to the Trade's root,
  * so it comes after every other modifier's root question. It is the standard
  * discard ([[SiteCards]]) from wherever the card stands: its favor returns
  * to the Nomad bank and its secrets to the trader facedown, so a Trade for
  * favor with it returns the secret paid onto it. Its line covers the
  * generic Discarded line.
  *
  * A selected modifier is held in play until its action ends, so it declares
  * `discardsItself` to be exempt from the active-modifier rule.
  */
final case class MountedLibrary private (catalog: ExecutableCatalog)
    extends SelectedModifier:
  import MountedLibrary._

  val cardId: DenizenId = MountedLibraryCard.id
  def id: PowerId = MountedLibrary.id
  def actions: Set[MajorActionType] = Set(MajorActionType.Trade)
  override def discardsItself: Boolean = true
  override def noteKeys: Vector[NoteKey] = MountedLibrary.noteKeys

  private val nomadCards = new SiteCards(catalog, Set(Suit.Nomad))

  def effects: Map[PowerWindow, Vector[Contribution]] = Map(
    PowerWindow.TradeGain -> Vector(Transform((ctx, operations) =>
      OperationRewrite.ops(ctx, operations)((_, _) => {
        // The record comes first, for the reason The Old Oak's does.
        case gain @ Gain.Secrets(player, _) if player == ctx.activePlayer =>
          Vector(Defer(deferred, Vector(gain)), gain)
      }) :+ double(ctx.activePlayer), FoldPhase.Late)),
    PowerWindow.TradeActionEligibility -> Vector(Transform((ctx, children) =>
      children ++ Vector(discard(ctx.activePlayer),
        Note(id, discardedNote, covers = true)), FoldPhase.Late)))

  /** As many secrets again as the Trade gained, then its line, which covers
    * the gain's: run only when Mounted Library was the source. */
  private def double(actor: PlayerId): Operation =
    Deferred.resolve(deferred)((_, pending, held) => {
      val secrets = held.collect { case Gain.Secrets(_, amount) => amount }.sum
      if secrets > 0 && tradedWith(pending) then Vector(
        Gain.Secrets(actor, secrets),
        Note(id, doubledNote(_, actor), covers = true))
      else Vector.empty
    })

  private def tradedWith(pending: PendingTree): Boolean =
    PowerAnswers.one(pending, TradeProcedure.decisionId)
      .contains(DecisionOptionRef.Denizen(cardId))

  /** The secrets the doubling's step gained, when it was the source. */
  private def doubledNote(states: NoteStates, actor: PlayerId)
      : Option[LogNote] = for
    step <- states.previous
    if NoteSupport.answer(states, TradeProcedure.decisionId)
      .contains(DecisionOptionRef.Denizen(cardId))
    gained = NoteSupport.secrets(step, actor)
    if gained > 0
  yield doubled(PowerSourceRef.Card(cardId), NoteArg.Number(gained))

  /** The standard discard of the card from the site it stands at. */
  private def discard(actor: PlayerId): Operation = BuildOps((ready, _) =>
    (for
      site <- SiteRulers.siteOf(ready, cardId)
      card <- ready.game.current.map.sites.get(site)
        .flatMap(_.denizens.find(_.id == cardId))
    yield nomadCards.discard(ready, site, card, Suit.Nomad, actor,
      required = true)) match
      case Some(discarding) => discarding.map(Vector(_))
      case None => Right(Vector.empty))

  /** Written when the step before took the card off its site. */
  private def discardedNote(states: NoteStates): Option[LogNote] = for
    (before, after) <- states.previous
    if SiteRulers.siteOf(before, cardId).nonEmpty &&
      SiteRulers.siteOf(after, cardId).isEmpty
  yield discarded(PowerSourceRef.Card(cardId))

object MountedLibrary:
  val id: PowerId = MountedLibraryCard.power.id
  /** The Trade's secret gain, recorded as it runs. */
  val deferred: DeferralKey = DeferralKey("mounted-library")
  private val notes = NoteSet(id)
  def noteKeys: Vector[NoteKey] = notes.declared
  /** "Gained {n} more secrets." */
  val doubled: NoteKey = notes.key("doubled", Vector(NotePart.Text("Gained "),
    NotePart.Arg(0), NotePart.Plural(0, " more secret.", " more secrets.")))
  /** "Discarded after the Trade." */
  val discarded: NoteKey = notes.key("discarded", Vector(
    NotePart.Text("Discarded after the Trade.")))

  def forCatalog(catalog: ExecutableCatalog): MountedLibrary =
    new MountedLibrary(catalog)
