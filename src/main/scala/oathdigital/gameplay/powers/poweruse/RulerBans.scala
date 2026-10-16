package oathdigital.gameplay.powers.poweruse

import oathdigital.gameplay.SiteRulers
import oathdigital.gameplay.powerresolver.{PowerUseRefusal, Waiver}
import oathdigital.model._

/** The ban a site card holds over its ruler's enemies (Spell Breaker,
  * Forest Council, City Wall; catalog batch 8, slice 10). The ruler is the
  * ruler of the card's site, when that is a player or bandits: nobody is
  * restricted at an unruled site or one the Empire rules. Every player but
  * a player ruler is an enemy, and every player is an enemy of bandits.
  * Only a player ruler may consent (consent design, ruling 4). */
private[powers] object RulerBans:
  def ruler(ready: ReadyGame, card: DenizenId): Option[SiteRuler] =
    SiteRulers.rulerOfCard(ready, card)

  def enemy(ruler: SiteRuler, player: PlayerId): Boolean =
    ruler != SiteRuler.Player(player)

  /** The ban's waiver, for the consent grid: its granter is the player who
    * rules `card`'s site, and `asked` is its reason when asked. */
  def waiver(card: DenizenId, asked: NoteKey): Waiver =
    val source = PowerSourceRef.Card(card)
    Waiver(ready => ruler(ready, card).collect {
      case SiteRuler.Player(player) => player }, source,
      (_, _) => asked(source))

  /** `power`'s refusal of `requester`, waivable by a player ruler under
    * `covers`. */
  def refusal(power: PowerId, card: DenizenId, ruler: SiteRuler,
      requester: PlayerId, covers: ConsentKey, plain: NoteKey,
      asked: NoteKey, code: String): PowerUseRefusal =
    PowerUseRefusal(power, plain(PowerSourceRef.Card(card)),
      reason(power, card, ruler, requester, covers, asked, code))

  /** `power`'s refusal of `requester` on the operation path, waivable by a
    * player ruler under `covers`. */
  def reason(power: PowerId, card: DenizenId, ruler: SiteRuler,
      requester: PlayerId, covers: ConsentKey, asked: NoteKey, code: String)
      : OperationReason =
    val granter = ruler match
      case SiteRuler.Player(player) => Some(player)
      case _ => None
    OperationReason(code, s"${power.value} refuses ${requester.value}",
      OperationReasonKind.Impossible, granter.map(player =>
        OperationWaiver(power, Some(player), covers,
          asked(PowerSourceRef.Card(card)))))
