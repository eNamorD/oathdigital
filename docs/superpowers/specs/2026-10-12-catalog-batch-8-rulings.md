# Catalog Batch 8: Per-Power Rulings

> Appendix to [the design](2026-10-12-catalog-batch-8-design.md). Each entry
> states the ruling agreed with the product owner on 2026-10-10 to
> 2026-10-12. Card ids are catalog ids. Every rule that applies to the whole
> of [batch 5](2026-10-08-catalog-batch-5-rulings.md#rules-that-apply-to-the-whole-batch),
> [batch 6](2026-10-09-catalog-batch-6-rulings.md#rules-that-apply-to-the-whole-batch)
> and [batch 7](2026-10-09-catalog-batch-7-rulings.md#rules-that-apply-to-the-whole-batch)
> applies here too.

## Rules that apply to the whole batch

- **A card ships whole.** No card lands with part of its printed text left
  for later.
- **An unusable option stays listed.** It is shown unavailable with its
  reason on hover, never hidden.
- **Who burns.**
  - A burn's actor is the actor of the procedure that burns, or the payer
    when the burn is a cost.
  - In an effect that makes each player burn (Revelation, Ancient
    Binding), each owner is the actor of their own burn. A card's secrets
    are its holder's.
  - A Raid's half-favor burn is the defeated defender's own.
  - A player never counts as their own enemy.
- **Off-turn moves count.** A rule that watches a player's pawn or tokens
  applies on and off that player's turn.
- **Log lines omit the actor** and follow the earlier batches' wording
  rules. Each slice settles its lines at plan time.

## Slice 1: No new engine

- **202 Ward of Silence.** "X [secret] ACTION: Choose a player whose pawn is
  at your site. Flip X [secret] on their board facedown."
  - The target is another player whose pawn is at the user's site.
  - X runs from one to the smaller of the user's faceup secrets and the
    target's faceup secrets. The user places X secrets on the card, and X
    of the target's faceup secrets flip facedown, in one batch.
  - A player at the site with no faceup secrets is shown unavailable as a
    target, with "No faceup secrets".
  - The action is usable when its card could take a secret (the user has a
    faceup secret and the card is empty) and another player at the site has
    a faceup secret. Otherwise the Act menu shows it unavailable with
    "Nothing to do now" (plan-time ruling).
  - Line: "Ward of Silence: Flipped 2 secrets on {Blue}'s board facedown."
- **171 Hospitality.** "After traveling, gain [favor] from one favor bank
  that matches both a card at your site and one of your advisers."
  - After the pawn arrives, the matching banks are the suits shared by a
    card at the new site and one of the holder's faceup advisers.
  - With one matching bank that holds favor, it gains from it without
    asking. With several, it asks which, listing every matching bank; an
    empty one is shown unavailable with "This bank is empty". With none
    holding favor, nothing happens (plan-time ruling).
  - It applies to every Travel the holder makes, including one made off turn
    (Palanquin).
  - It writes no line: the generic gain line names the bank.
- **203 Vow of Wisdom.** "You must gain [secret] instead of [favor] when you
  play to a site." When its holder plays a card to a site, the play gains
  1 secret and no favor. There is no choice. The secret is gained whether
  or not the card's suit bank holds favor (plan-time ruling). A favor
  another power gives for the play, such as Welcoming Party's, is still
  gained: "instead of" replaces only the play's own favor (ruled
  2026-10-12). It writes no line: the generic line reads "Gained 1
  secret".

## Slice 2: Oathkeeper goal

- **209 True Oath.** "WHEN PLAYED, if you rule at least three other
  [suit-beast] cards, you may change the Oathkeeper goal."
  - The three Beast cards are counted as `RuledCards.of` counts ruled
    cards, without True Oath itself, when the power fires (plan-time
    ruling).
  - With three or more, it lists the four goals, the current one shown
    unavailable with "The current goal", and "Keep the current goal". The
    new goal must differ from the current one. The player may decline.
  - Lines: "True Oath: Changed the Oathkeeper goal to Protection.", "True
    Oath: Kept the Oathkeeper goal." and, with fewer than three, "True
    Oath: Ruled fewer than three other Beast cards." (plan-time ruling).
- **244 The Red Seer.** "[secret] [secret-burnt] [secret-burnt] ACTION: If
  you hold the Darkest Secret, change the Oathkeeper goal to anything but
  Devotion."
  - The new goal is neither the current goal nor Devotion. Both are listed
    unavailable: "The current goal", and "The Red Seer cannot choose
    Devotion" (plan-time ruling).
  - Without the Darkest Secret the action is shown unavailable, with
    "Nothing to do now" (Yew Staff satisfies it once slice 9 lands).
  - Line: "The Red Seer: Changed the Oathkeeper goal to Protection."
- **Both.** The title follows at the action's end: the Oathkeeper check
  reads the new goal, and "Oathkeeper passed to {Blue}" tells a pass
  (plan-time ruling).

## Slice 3: Warbands at a Campaign's start

- **185 Vow of Union.** "At the start of any campaign, you may move any
  number of warbands from sites you rule in your region to your board or
  vice versa." and "You may only have [suit-beast] advisers—bury other
  advisers you have or would gain."
  - It fires at the start of every Campaign, the holder's or another
    player's, and asks the holder: after the Supply is spent, before the
    kind and the targets (plan-time ruling).
  - The sites are those the holder rules in the region of their pawn when
    the Campaign starts.
  - One rearrangement in one direction: from those sites to the board, or
    from the board to those sites. Any number may move, including none. It
    asks the direction ("Move warbands to your board", "Move warbands to
    your sites", "Move none"), then the amounts (plan-time ruling).
  - Emptying a site of the holder's warbands may lose its rule. That is
    allowed. If no Campaign kind is legal afterwards, the Campaign ends with
    its Supply spent: "Nothing left to attack: the Campaign ended." (product
    owner).
  - When it moved at least one warband, the attacker may answer the kind
    question with "Skip Campaign", which ends the Campaign the same way:
    "Skipped the Campaign." (product owner).
  - Lines: "Vow of Union: Moved 2 warbands from sites to the board." on the
    holder's turn, "Vow of Union: {Blue} moved 2 warbands from sites to
    their board." off turn, and the same for the other direction (plan-time
    ruling).
  - The restriction half buries every non-Beast adviser when the card
    arrives, and any non-Beast adviser its holder would gain, as Family
    Wagon does. A play it would bury carries "Vow of Union will bury it".
    It lifts no limit.

## Slice 4: A Raid's defeat replaced

- **5 Keep.** "If defeated in a raid, after the attacker takes what they
  targeted, place your pawn at this site and end the action. Do not burn
  favor, move pawn, etc."
  - It applies when the defeated defender rules Keep's site.
  - The attacker takes what they targeted. Then the half-favor burn, the
    discard of facedown cards and the relocation of the defender's pawn are
    skipped, and the defender's pawn is placed at Keep's site.
  - "End the action" ends the Raid there. Powers that act "after the
    Campaign" still run.
  - Keep applies when it is faceup at a site the defeated defender rules,
    judged after the battle's losses. It asks nothing, and the defender
    cannot decline it (plan-time ruling).
  - The tokens that leave with a taken banner still leave: the People's
    Favor's favor returns to the banks, and the Darkest Secret's secrets
    are burnt. They are part of the take (plan-time ruling).
  - The facedown advisers' discard includes a Conspiracy's return to the
    box, and it is skipped too (plan-time ruling).
  - A pawn already at Keep's site stays there (plan-time ruling).
  - The effects at the Campaign's end still run: Book Burning still burns,
    Herald still pays, and battle plans still discard (plan-time ruling).
  - Line: "Keep: Ended the Raid with {Blue}'s pawn at {site}." The closing
    line no longer says the pawn "was sent to" Keep's site (plan-time
    ruling).

## Slice 5: Burns taken

- **74 Vow of Silence.** "You cannot challenge the Darkest Secret. Whenever
  an enemy burns [secret-burnt], you take those secrets."
  - Every burn of secrets by an enemy counts, cost or effect. The burn is
    replaced by a `Take`: the tokens go to the holder instead of the bank,
    and a facedown secret stays facedown. Secrets an enemy burns from the
    holder's own board stay there.
  - Its holder cannot start a Challenge for the Darkest Secret. The option
    is shown unavailable with the reason.
- **86 Vow of Division.** "You cannot take the Challenge action. Whenever an
  enemy burns [favor-burnt], you take that favor unless it was burned by a
  raid targeting you."
  - As Vow of Silence, for favor. A Raid's half-favor burn is the defeated
    defender's own (plan-time ruling), so an attacker or a bystander who
    holds the Vow takes it, and a defender who holds it does not.
  - Its holder cannot take the Challenge action at all.

## Slice 6: Operation triggers

- **R38 Secret Testament.** "When an enemy takes a banner from you, take
  all [favor] or [secret] on the banner."
  - A persistent Challenge modifier (ruled 2026-10-13). It works only when
    an enemy challenges for a banner its holder holds. A Raid, a
    Conspiracy or Wizard's Conclave that takes the banner does not
    trigger it.
  - The holder takes the tokens that would leave the banner (ruled
    2026-10-13): every favor on the People's Favor, in place of their
    return to the banks, and every secret on the Darkest Secret, in place
    of the retained half and the secrets placed on sites. The challenger
    then pays onto the empty banner and takes it.
  - The holder holds the relic faceup.
  - The line names the holder, whose turn it never is: "{Red} took 2
    favor from the {People's Favor}." or "{Red} took 2 secrets from the
    {Darkest Secret}." An empty banner writes no line.
- **R25 Brass Army** (restriction half). "Whenever your pawn moves (travel
  or place), bury this or flip a secret you have facedown."
  - Every move of the holder's pawn counts, on or off turn: Travel, a
    Raid's relocation, Keep's placement, Whistle, Brass Horse, Magic Carpet
    and Palanquin. A placement that leaves the pawn where it stands moves
    nothing, so it asks nothing.
  - The holder chooses: bury the relic, or flip one faceup secret facedown.
    With no faceup secret, the bury is forced and nothing is asked.
  - The question comes right after the move, before the rest of the
    action. In a Travel it comes before after-travel powers such as
    Hospitality.
  - The holder holds the relic faceup. A Raid that takes the relic before
    the defender's pawn moves leaves nothing to ask.
  - The question: "Brass Army: your pawn moved", with "Bury Brass Army" and
    "Flip a secret facedown".
  - Lines: "Buried after a pawn move." and "Flipped a secret facedown." on
    the holder's turn; "{Red} buried it after a pawn move." and "{Red}
    flipped a secret facedown." off it.
- **R25 Brass Army** (Campaign half, corrected 2026-10-13). "[secret] ±4
  [attack-die]". The component catalog prints "+4", a typo: it is a
  gradient battle plan for either side.
  - An attacker places the secret onto the relic and adds four attack
    dice, which are not force.
  - A defender places the secret onto the relic and removes four attack
    dice. The attack pool never goes below zero.

## Slice 7: Trade at another site

- **177 Small Friends.** "Act as if your pawn is at any site with a
  [suit-beast] card. (You may use Trade modifiers there.)"
  - A Trade modifier. Picked in the modifier loop, it asks which site, right
    after the pick, in the same pass. It offers the sites in play, other
    than the pawn site, that hold a card of the Beast suit. The question has
    no Skip.
  - For the rest of the Trade, the chosen site takes the place of the pawn
    site: the Trade's source cards and the modifiers usable at a site come
    from it. Ruled sites, advisers and relics stay usable.
  - The player is not in two places at once. The physical pawn site's cards
    are not usable unless the player rules that site (FAQ: Trade modifiers
    only at the new site).
  - Rules that read the pawn site follow the chosen site: Skilled Merchants'
    and Map Library's region is its region, and Map Library there is
    selectable.
  - Unavailable, with the reason:
    - once a picked modifier's card stands at the pawn site, and the player
      does not rule it: "A modifier you picked is at your pawn site";
    - once Friendly Familiar acts at another site: "Already acting as if at
      another site";
    - with no such site: "No other site has a Beast card".
  - The question: "Small Friends: act as if at which site?". The line:
    "Acted as if at {the Shrine}."
- **224 Friendly Familiar.** "[secret] Act as if your pawn is at any site.
  You may use Trade modifiers at the site." As Small Friends, costing one
  secret paid onto it when picked.
  - It offers the sites in play, other than the pawn site, that hold a card
    a Trade can draw on (a card with a suit). With none: "No other site has
    a card to trade with".
  - The question: "Friendly Familiar: act as if at which site?". The line:
    "Acted as if at {the Shrine}."
- **55 Secret Signal.** "If you gain only one [favor], gain one more
  [favor]."
  - An optional Trade modifier. It reads the Trade's whole favor gain at the
    Trade's end, after Moving Market: the Trade's own gain and Moving
    Market's. When that is exactly one, one more favor is gained from the
    bank of the Trade's own gain.
  - Two separate gains of one favor are not "only one": a Trade with Moving
    Market that gains one favor and moves it gains two, and nothing more.
  - Moving Market's favor counts at what the Nomad bank could give, so an
    empty bank counts 0.
  - The extra favor does not count again.
  - Unavailable for a Trade for secrets: "A Trade for secrets gains no
    favor". In a Trade for favor it is always available: the source is
    chosen after the modifiers. When the gain is not one, it does nothing
    and writes no line.
  - The line: "Gained 1 more favor.", covering the generic gain line.

## Slice 8: Effective suit

- **223 Pledge to Discord.** "Your advisers all have the [suit-discord] suit
  instead of their printed suit. Marriage counts as two [suit-discord]."
  - Every rule that reads the suit of the holder's faceup advisers sees
    Discord. Marriage counts as two Discord advisers. A facedown adviser
    still has no suit, and a facedown Pledge changes nothing.
  - Favor going back from the holder's adviser goes to the Discord bank:
    at Rest, in an off-turn cost settlement, and on a discard or bury.
  - A card played faceup to the holder's adviser slot reads Discord, so
    Wild Cry and Saddle Makers do not fire for it. A card played to a site
    reads its printed suit.
  - The client shows printed suits.
  - **With Family Wagon or Vow of Union.** Simultaneous effects are ordered
    by the active player. Every adviser that does not keep the rule by its
    printed suit, Pledge aside, is buried at once. When faceup advisers
    that keep it by their printed suit remain, the active player sorts
    them between "Bury before Pledge to Discord" and "Keep in advisers"
    (confirm "Bury"). Those placed first are buried, then Pledge; the rest
    revert and stay. With none, Pledge goes in the first bury.
    - The heading, when the holder is the active player: "Family Wagon:
      Depending on when you bury Pledge to Discord, you can keep all or
      none of your Nomad advisers. Choose which Nomad advisers you keep."
    - When another player answers: "Family Wagon: Depending on when Pledge
      to Discord is buried, its holder keeps all or none of their Nomad
      advisers. Choose which they keep."
    - The question posts no "Chose" line; each bury posts "Buried
      {cards}.".
  - Pledge writes no line.
- **R41 Spiteful Mirror.** "[secret] This turn, you may act as if any cards
  you use to trade or muster are [suit-discord] cards."
  - An optional Trade and Muster modifier. Picked, it turns the source card
    Discord: favor comes from the Discord bank, the Discord advisers match,
    and every reader of the traded or mustered card sees Discord.
  - Its first pick in a turn pays 1 secret onto the relic and records the
    use for the turn. Later picks that turn are free. The player may leave
    it unpicked on any action.
  - It is offered only while its player holds the relic.
  - Its line, once the source is chosen: "Spiteful Mirror: Acting as if
    {card} is a Discord card."
  - **With The Old Oak** (product owner, 2026-10-14). The Old Oak, traded
    with the Mirror picked, is Discord, so its bonus secret comes when the
    player has a Discord adviser, not a Beast one.

## Slice 9: Darkest Secret lens

- **R40 Yew Staff.** "When using card powers, you may act as if you hold
  the Darkest Secret."
  - Every condition inside a card power its holder uses that asks whether
    they hold the Darkest Secret is met: Disciples, Terror Spells, The Red
    Seer, Fae Battalion, Rusting Ray, Fire Talkers and Unstable Summon.
    Giant Python (slice 10) reads it too.
  - It works only while its holder is the one using the power, with Yew
    Staff faceup on their board. An enemy's Armed Mob does not make them a
    target, and Banner Breakers, the attacker's power, is unaffected.
  - It applies automatically and writes no line; the power's own line
    shows its effect.
  - It does not reach Challenge, scoring, the Oathkeeper goal or the
    Darkest Secret banner's own powers, which are not card powers.
  - **Unstable Summon** burns X secrets from the Darkest Secret wherever
    the banner is: held by another player or in the bank. The line is
    unchanged (plan-time ruling).
  - **Armed Mob used by the holder** (plan-time ruling). The holder may be
    a target of their own Armed Mob unless they also hold the People's
    Favor.
    - With two targets it first asks "Armed Mob: discard a faceup adviser
      of which player?". A target with no faceup adviser is shown
      unavailable, with "No faceup adviser to discard". With neither
      having one, nothing is asked, and the line is "Neither target had a
      faceup adviser to discard."
    - With one target nothing is asked about players. The adviser question
      reads "Armed Mob: discard one of your faceup advisers" for the
      user's own advisers.
  - **Cards not built yet** (plan-time ruling). Ancient Pact and High
    Priest read the check when they land. Tome Guardians' protection reads
    the real holder, because it guards the actual banner.

## Slice 10: Power-use restrictions

- **162 Spell Breaker.** "Enemies of Spell Breaker's ruler cannot use
  powers that cost any [secret] or [secret-burnt]." Every power whose cost
  includes a secret, ACTION or modifier, is shown unavailable to the
  ruler's enemies.
- **194 Forest Council.** "Enemies of Forest Council's ruler cannot place
  [favor] / [secret] on [suit-beast] cards or use their non-persistent
  powers." Both bans cover Beast cards anywhere.
- **252 City Wall.** "While at this site, enemies of City Wall's ruler
  cannot place [favor] / [secret] on cards or use non-persistent powers."
  While an enemy's pawn is at City Wall's site, the enemy cannot place favor
  or secrets on any card or use any non-persistent power, wherever the card
  is.
- **186 Giant Python.** "[secret] ACTION: If you hold the Darkest Secret,
  move Giant Python to another site, and you may discard a card there
  first." and "Other cards at this site cannot be used for non-persistent
  powers or placing [favor] / [secret]."
  - The move goes to any other site. At a full site, the discard is
    required.
  - Its restriction moves with it, and its ruler becomes the ruler of its
    new site.
  - The restriction holds for every player, its ruler included.
- **Automatic powers** (ruled 2026-10-15). An automatic power is used when
  it makes something happen: a gain, a kill, a bury, a card's effect.
  Restrictions, limits, requirements on an action and cost changes are not
  uses.
  - Used, so refused like any other non-persistent power: every WHEN
    PLAYED power (Conspiracy included), Tyrant, Hospitality, Hunger, Keep,
    Vow of Beastkin's extra warband and Vow of Wandering's extra Muster
    warbands.
  - Not used, so never refused: Initiation Rite, Vow of Beastkin's source
    limit, Pied Piper's "ignores the adviser limit", the adviser limits of
    Insomnia, Silver Tongue and Assassin, Vow of Obedience, Vow of Poverty,
    and the edifice setup rules during Setup.
  - A refusal is judged when the power's effect would run. Tyrant arriving
    at City Wall's site is refused.
  - Slice 10 refuses an automatic power silently. Roadmap item 10b lets the
    refusing card's ruler decide through consent
    ([design](2026-10-15-automatic-power-consent-design.md)).

## Slice 11: Actor-aware lock

- **165 Ancient Bloodline.** "Your enemies act as if denizens and relics at
  sites you rule are locked."
  - For an enemy of the holder, the denizens and relics at sites the holder
    rules are locked: the enemy cannot move or flip them. Bury still goes
    through, as for every lock.
  - Site relics are never Raid targets, so the effect that matters is that
    an enemy cannot Recover a relic at those sites. Recover shows the option
    unavailable with the reason.
  - Discards by an enemy at those sites (Baron, Giant Python) are refused.

## Slice 12: Card play at another site

- **250 Baron.** "You may play to any site you rule, and you may discard a
  card from that site first." The card play's slots widen to every site the
  player rules. The discard uses the Replace path.
- **188 New Growth.** "You may play [suit-beast] and [suit-hearth] cards to
  any site (that has space)." Beast and Hearth cards may be played to any
  site with space. Full sites are shown unavailable with the reason.
- **218 Forest Warden.** "If you play a [suit-beast] card to a site, you
  may bury a card at the site first." When a Beast card is played to a
  site, the Replace path offers a bury, labelled "Bury and replace".

## Slice 13: Campaign from another site

- **198 Wild Allies.** "[secret] ACTION: Campaign at any site with a
  [suit-beast] card. Act as if your pawn is there. Spend no Supply and add
  your warbands there to your force."
  - The chosen site is the Campaign's origin for its targets and its force,
    and the user's warbands there join the force. No Supply is spent.
  - The physical pawn does not move. In a Raid from the origin, the
    defender at the target is relocated as usual.
- **115 Captains.** "[favor] ACTION: Campaign at any site you rule. Act as
  if your pawn is there. Spend no Supply and add your warbands there to
  your force." As Wild Allies, at a site the user rules when the action is
  used.

## Slice 14: Off-turn actor

- **98 Sneak Attack.** "After another player's campaign, you may campaign,
  spending no Supply, if you declare them as the defender."
  - It is offered after every effect and follow-up of the other player's
    Campaign has resolved.
  - With several holders, each is offered in turn order, starting after the
    attacker.
  - The Campaign is the holder's, off turn: they choose modifiers and plans
    as the attacker, and the earlier attacker is the defender.
- **16 Second Wind** (defender side). "[secret] [favor-burnt] After you're
  victorious, you may travel and then may campaign, spending no Supply for
  either."
  - A victorious defender is offered it too. Their Travel and Campaign run
    off turn, after the attacker's follow-ups.

## Slice 15: Palanquin

- **107 Palanquin.** "[favor] ACTION: Choose a player whose pawn is at your
  site. Put your pawn on a site that they can travel to. Make them travel to
  that site, spending no Supply."
  - The destinations are the sites the target could legally travel to.
  - The user's pawn is placed there; then the target travels there, off
    turn.
  - The target owns every decision in that Travel. Their restrictions,
    consent and after-travel powers apply (Hospitality, Tyrant, Brass Army),
    and only the Supply cost is waived.
  - Toll Roads: the target must consent to pay its ruler. If they refuse,
    that destination is invalid.
