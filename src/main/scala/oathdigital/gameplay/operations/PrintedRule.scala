package oathdigital.gameplay.operations

import oathdigital.model._

/** A card's printed power that an operation restriction enforces, with no
  * walker or phase power: the Hall of Ministers, Ancient Bloodline and the
  * Grand Scepter's restriction. `OperationRestrictions.printedRules` lists
  * each once, and every registry that keys a power by id reads that list:
  * the implemented status, the consent grid and the Game Log's notes.
  */
trait PrintedRule:
  def id: PowerId
  def source: PowerSourceRef
  /** The keys its refusals write. An operation restriction is not a power,
    * so `RuleNotes` declares them for the formatter. None by default. */
  def noteKeys: Vector[NoteKey] = Vector.empty

/** A printed rule a player may lift by consent (consent design). */
trait WaivableRule extends PrintedRule:
  /** The player who may consent, if anyone may. */
  def granter(ready: ReadyGame): Option[PlayerId]
  /** The reason of an option the granter may be asked about. */
  def consentNote: NoteKey
