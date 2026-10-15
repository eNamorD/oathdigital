package oathdigital.gameplay.powers.campaign

import oathdigital.model._

/** The single-shield rescoring of the defense, for the plans that ignore the
  * defense dice showing a single shield (Bag of Siegeworks, Rain Boots). Each
  * single-shield die scores 0; two shields and doublers score as usual. The
  * defense is written again through the defense scorer (`DefenseScore`), so
  * two plans that both rescore write the same score.
  */
private[campaign] object SingleShields:
  /** The keys this object owns, which no one power owns. A power that
    * writes one includes this set. */
  val notes: NoteSet = NoteSet.unowned("SingleShields")
  /** "Single shields ignored." */
  val ignored: NoteKey = notes.key("ignored",
    Vector(NotePart.Text("Single shields ignored.")))

  /** The defense dice score with every single shield scoring 0. */
  def score(faces: Vector[DefenseDieFace]): Int =
    DefenseScore.dice(faces, DefenseRules(singleShieldsIgnored = true))

  /** What a plan adds after the defense result's own step: the defense
    * scored again, then `power`'s line from `source`, written only when a
    * single shield was rolled. */
  def ignore(use: PlanUse, power: PowerId, source: PowerSourceRef)
      : Vector[Operation] = Vector(
    DefenseScore.rescore(use.actor),
    Note(power, states => Option.when(
      DefenseScore.faces(states.now).contains(DefenseDieFace.OneShield))(
      ignored(source))))
