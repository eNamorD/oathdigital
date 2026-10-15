package oathdigital.gameplay.walker

import oathdigital.gameplay.operations.OperationRestrictions
import oathdigital.gameplay.powerresolver.{ContributingPower, ModifierPicks,
  PowerWindowIndex}
import oathdigital.model.{Answered, OperationRestriction, PowerId,
  PowerResolution}

/** The powers available to one `advance`/`roll`/`resolve` command (Task 3).
  * `OathRules` supplies it at command entry; `applyRecorded` (replay) never
  * takes one -- replay applies recorded ops only (spec decision 5) and must
  * never re-gather or re-transform.
  *
  * `probing` is on for every command: the walker searches before a decision
  * parks and hides the options from which no path reaches a legal end
  * (global operation restrictions design, "Lazy pruning"). [[quiet]] turns
  * it off for a dry run inside a search, so that run never searches in turn.
  *
  * `restrictionSet` is the catalog's global operation restrictions and
  * `modifiers` the modifiers picked so far. Together with the
  * offered powers' own restrictions they make `operationRestrictions`, which
  * every walker step runs with (global operation restrictions design).
  * `pool` is the full catalog a selection was made from, so the modifiers a
  * walk picks can join it (`withAnswers`).
  */
final case class WalkerPowers(powers: Vector[ContributingPower],
    probing: Boolean = true,
    restrictionSet: OperationRestrictions = OperationRestrictions.none,
    modifiers: Vector[PowerId] = Vector.empty,
    pool: Vector[ContributingPower] = Vector.empty,
    /** Every note key the catalog declares, which `NoteCheck` reads to
      * refuse a note no power declared. `WalkerPowerCatalog` supplies it;
      * a `WalkerPowers` assembled from hand-made powers has no catalog
      * behind it and declares nothing, so its notes are not checked. */
    declared: Option[oathdigital.gameplay.powers.DeclaredNotes] = None):
  /** These powers indexed by the windows they hook, built once per instance
    * (see [[PowerWindowIndex]]). */
  lazy val windowIndex: PowerWindowIndex = PowerWindowIndex.of(powers)

  /** Whether any power can reject an action through a tree-level
    * `Restriction`. The search's tree check has nothing to find without one.
    */
  lazy val hasRestrictions: Boolean = windowIndex.hasRestrictions

  /** The operation restrictions every step of this command runs with. A
    * selected power that discards its own card is not held in play by the
    * active-modifier rule. */
  lazy val operationRestrictions: Vector[OperationRestriction] =
    restrictionSet.active(powers.flatMap(_.operationRestrictions),
      modifiers.filterNot(id => powers.exists(power =>
        power.id == id && power.discardsItself)))

  /** The verdicts this instance's searches have found (see [[SearchMemo]]). */
  private[walker] lazy val searchMemo: SearchMemo = new SearchMemo

  /** These powers without the search, for a dry run inside one. It is built
    * once per instance, so repeated dry runs share one copy and build its
    * restrictions once. */
  lazy val quiet: WalkerPowers =
    if probing then copy(probing = false) else this

  /** These powers with the modifiers picked in `answered` added (modifier
    * walker design, "Active powers"). Picks are only ever added: a dry run
    * that starts from no answers keeps the picks already made. Without a
    * pool there is nothing to add from. */
  def withAnswers(answered: Vector[Answered]): WalkerPowers =
    val wanted = (modifiers ++ ModifierPicks.picks(pool, answered)).distinct
    if wanted == modifiers then this
    else withPicks.computeIfAbsent(wanted,
      picked => WalkerPowers.selected(copy(powers = pool), picked))

  /** The instances `withAnswers` built, by selection, so that every call for
    * the same picks shares one instance and its search memo. */
  private lazy val withPicks =
    new java.util.concurrent.ConcurrentHashMap[Vector[PowerId], WalkerPowers]

object WalkerPowers:
  val empty: WalkerPowers = WalkerPowers(Vector.empty)

  /** Powers offered to one command out of a full catalog: an `Automatic`
    * power fires unconditionally; a `PlayerSelected` power fires only when
    * its id appears in `modifiers`, the picks. Shared by
    * `OathRules.walkerPowers` (command time) and `WalkerDecisionProjector`
    * (park-time projection) so both fold a shared window identically. The
    * modifiers are kept, so the active-modifier restriction sees them. The
    * engine's follow-up waiver is always offered; it applies only to a run
    * started from the queue.
    */
  def selected(catalog: WalkerPowers, modifiers: Vector[PowerId]): WalkerPowers =
    catalog.copy(powers = catalog.powers.filter(power =>
      power.resolution == PowerResolution.Automatic ||
        modifiers.contains(power.id)) :+ FollowUpSupplyWaiver,
      modifiers = modifiers, pool = catalog.powers)
