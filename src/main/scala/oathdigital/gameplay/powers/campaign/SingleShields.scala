package oathdigital.gameplay.powers.campaign

import oathdigital.gameplay.actions.campaign.CampaignIds
import oathdigital.model._

/** The single-shield rescoring of the defense, for the plans that ignore the
  * defense dice showing a single shield (Bag of Siegeworks, Rain Boots). Once
  * the defense is rolled, each single-shield die scores 0; two shields and
  * doublers score as usual, so Blank, OneShield and Doubler score 0. The score
  * is written again before the defender's force is added to it
  * (`CampaignDefenseResult`), so the recorded defense carries the change. It
  * is computed from the faces, not from the current score, so two plans that
  * both rescore write the same score.
  */
private[campaign] object SingleShields:
  /** "Single shields ignored." */
  val ignored: NoteKey = NoteKey("ignored",
    Vector(NotePart.Text("Single shields ignored.")))

  /** The defense dice score with every single shield scoring 0. */
  def score(faces: Vector[DefenseDieFace]): Int =
    DefenseDieFace.score(faces.filterNot(_ == DefenseDieFace.OneShield))

  /** What a plan adds before the defense result's own children: the
    * rescoring, then `power`'s line from `source`, written only when a single
    * shield was rolled. */
  def ignore(power: PowerId, source: PowerSourceRef): Vector[Operation] =
    Vector(
      BuildOps((ready, _) => Right(rescored(ready))),
      Note(power, states => Option.when(
        faces(states.now).contains(DefenseDieFace.OneShield))(ignored(source))))

  /** Nothing is written when no single shield was rolled. */
  private def rescored(ready: ReadyGame): Vector[CoreOperation] =
    val rolled = faces(ready)
    if !rolled.contains(DefenseDieFace.OneShield) then Vector.empty
    else Vector(ModifyRollOutcome(CampaignIds.defensePool, None,
      Some(score(rolled))))

  private def faces(ready: ReadyGame): Vector[DefenseDieFace] =
    ready.game.current.rollOutcomes.get(CampaignIds.defensePool).toVector
      .flatMap(_.faces.collect { case face: DefenseDieFace => face })
