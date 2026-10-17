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
  powers that cost any [secret] or [secret-burnt]."
  - Reached: every power whose printed cost has a secret or a burnt
    secret, of any kind (ACTION, modifier, battle plan, Jinx's reroll);
    Ward of Silence and Arcane Armor, which pay X secrets; and every
    battle plan Gleaming Armor taxes, the title's plan included.
  - Not reached: Initiation Rite, Unstable Summon's burn from the Darkest
    Secret.
  - The ruler may consent outside a Campaign.
- **194 Forest Council.** "Enemies of Forest Council's ruler cannot place
  [favor] / [secret] on [suit-beast] cards or use their non-persistent
  powers." Both bans cover Beast cards anywhere: a faceup adviser by its
  effective suit, a site card by its printed suit. The ruler may consent
  outside a Campaign.
- **252 City Wall.** "While at this site, enemies of City Wall's ruler
  cannot place [favor] / [secret] on cards or use non-persistent powers."
  While an enemy's pawn is at City Wall's site, the enemy cannot place favor
  or secrets on any card or use any non-persistent power, wherever the card
  is. The ruler may consent outside a Campaign. Where the enemy is, is
  where they act from: a queued Campaign's origin counts (slice 13). Small
  Friends' as-if Trade site is ROADMAP item 13c.
- **186 Giant Python.** "[secret] ACTION: If you hold the Darkest Secret,
  move Giant Python to another site, and you may discard a card there
  first." and "Other cards at this site cannot be used for non-persistent
  powers or placing [favor] / [secret]."
  - The holding is read through Yew Staff's lens.
  - The move goes to any other site, through one slot question. At a full
    site the discard is required.
  - Its restriction moves with it, and its ruler becomes the ruler of its
    new site.
  - The restriction holds for every player, its ruler included, and is
    not waivable.
- **For every card:** a non-persistent power is any power printed
  `persistent: false` that a player chooses, or an automatic power the
  "Automatic powers" entry below lists as used. A placement is a cost paid
  onto a card or a `Give` onto it; an off-turn payment is not one.
  Enemies are every player but a player ruler, every player at a bandit
  site, and nobody at an unruled or Empire site.
- **Placements in practice** (part 2). A use whose cost places on its card
  counts as a placement. One grant covers both a card's power and placing
  on it, for the turn. A placement is askable where a choose-one offers it.
  Elsewhere it is refused without a question.
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
  - Ancient Bloodline must be faceup. Enemies are every player but its
    holder. A site counts when the holder rules it as a player. An Imperial
    holder waits for the Imperial rules.
  - For an enemy, every card at those sites is locked: denizens, edifices
    and relics. The enemy cannot move it, so cannot take, give, swap or
    discard it. Bury still goes through, as for every lock.
  - A card played to those sites, or a relic arriving there, is not
    refused. It is locked once it is there.
  - Site relics are never Raid targets. In practice, the lock stops an
    enemy from Recovering a relic at those sites, or taking it with Skeleton
    Key or Barbed Net. It also stops an enemy from taking a denizen there
    (Horned Mask), moving one away (Giant Python, Roving Terror, Moving
    Market, Great Herd) or discarding one (card play's Replace, Dazzle,
    Threatening Roar, a battle plan's discard).
  - The holder may consent outside a Campaign (consent design, ruling 1).
    Inside a Campaign the lock holds.
  - Hover reasons: "Ancient Bloodline: Locked." and "Ancient Bloodline:
    Locked and needs the holder's consent."
  - The mover is named on the operation itself (ruled 2026-10-16). Each
    move names its player (`Move.by`), and a card moved from a site always
    does. A restriction reads the mover off the operation tree.

## Slice 12: Card play at another site

Ruled 2026-10-16. Each card is a free Search modifier the player selects,
and the selection reaches the Play-Facedown-Adviser action too.

- **250 Baron.** "You may play to any site you rule, and you may discard a
  card from that site first."
  - It opens every site the player rules.
  - Every play to a ruled site carries Baron's discard permission, the pawn
    site included when the player rules it.
- **188 New Growth.** "You may play [suit-beast] and [suit-hearth] cards to
  any site (that has space)."
  - It opens every site for a card whose effective suit is Beast or
    Hearth.
  - "(that has space)" is a reminder only, so every site follows the pawn
    site's rules. A site with room offers its empty slot. A full site
    offers Replace options only under a discard or bury permission, and
    nothing otherwise.
  - This replaces the earlier ruling that full sites are shown unavailable
    with the reason: a full site with no permission has no option.
- **218 Forest Warden.** "If you play a [suit-beast] card to a site, you
  may bury a card at the site first."
  - It gives one bury at the site, when the card's effective suit is
    Beast.
  - Bury ignores locks, so any card there may go: any card in the site's
    row (denizens, intact edifices, and cards the Hall of Ministers or
    Ancient Bloodline protect) and any relic there.

The shared rules:

- **Permissions follow the site played to.** The Homeland rule applies
  where the site is the Homeland of the card's effective suit. Crop
  Rotation, People's Favor Mob and Forest Warden apply at any site. Baron
  applies at ruled sites.
- **Bury or discard.**
  - A Replace covered only by Forest Warden is a bury: a bury and
    replace. The board badges it "Bury" and labels it "Bury {card} at
    {site}".
  - A Replace covered only by a discard permission is a discard, shown
    "Replace".
  - When both cover it, the option reads "Replace". A follow-up choose-one
    titled "Bury or discard the replaced card" then offers the buttons
    "Bury" and "Discard".
  - Forest Warden's bury is spent by the replaced card or by the bury
    follow-up, never twice.
- **Relics.**
  - At a site with room, each relic there is offered as a bury and play,
    and the card goes to the first empty slot. The board badges it "Bury"
    and sums it up as "Bury {card}; play {card} here".
  - After a Replace that discarded, the follow-ups run in order. "Discard
    more cards from the site" offers row cards up to the discard
    permissions left. Then, if Forest Warden is still unused, "Bury a card
    at the site" is a choose-many from 0 to 1 over the remaining row cards
    and every relic there.
  - Relics are offered by their slot (`SiteRelicSlot`), never by their
    identity.
- **Discard piles.** A site card discarded in a play goes to the pile of
  the region after the target site's region. A replaced adviser, and the
  discard-pile slot, keep the pile after the pawn's region.
- **Order.** The slot question's options run in this order: the discard
  pile; the pawn site; each other site in map order, with its empty slot,
  then its Replace options, then its relics; the faceup plays; the facedown
  plays.
- **Lines.**
  - Every discard permission writes "May discard a card at {site} first.":
    the Homeland rule, Crop Rotation, the Mob and Baron. This drops the old
    "{Red} … at their site".
  - Forest Warden writes "May bury a card at {site} first."
  - Baron and New Growth write nothing else. "Played {card} to {site}" now
    names the target site.

## Slice 13: Campaign from another site

Ruled 2026-10-16. Each card is an ACTION that pays its cost, asks for a
site, and queues a Campaign `Run` at the front of the queue. The site is
the Campaign's origin and the card its source, so the Campaign spends no
Supply. The Campaign asks its modifier decision as any Campaign does. It
takes a site start argument only from an entry with a source, so a menu
Campaign cannot fake an origin.

- **198 Wild Allies.** "[secret] ACTION: Campaign at any site with a
  [suit-beast] card. Act as if your pawn is there. Spend no Supply and add
  your warbands there to your force."
  - Its sites hold a faceup denizen, or an edifice, whose effective suit is
    Beast. Relics do not count. The pawn site counts.
  - With no site where a Campaign could start, the ACTION is unavailable:
    "No site with a Beast card where a Campaign could start".
- **115 Captains.** "[favor] ACTION: Campaign at any site you rule. Act as
  if your pawn is there. Spend no Supply and add your warbands there to
  your force."
  - Its sites are the ones its user rules as a player. Empire sites do not
    count, as with every other power. The pawn site counts, and its
    warbands then join the force.
  - With no site where a Campaign could start, the ACTION is unavailable:
    "No site you rule where a Campaign could start".

The shared rules:

- **The site question** lists the qualifying sites in map order. A site
  where the queued Campaign's start would be refused is unavailable, with
  the refusal's reason, by the same start check the menu runs.
- **"Act as if your pawn is there."**
  - Every read of the attacker's own pawn site during the Campaign walk
    reads the origin: the targets, the Raid's defenders, Fortress notes,
    the Narrow Pass, Peace Envoy, Signal Trees, Vow of Union, a plan's
    discard pile and City Wall. Reads of other players' pawns do not
    change.
  - The physical pawn does not move. Later walks read the real pawn site,
    including Second Wind's Travel and Campaign and a Knights Errant
    Campaign.
  - City Wall walls in an attacker campaigning from its site, and not one
    who stands there but campaigns from elsewhere.
  - Using Wild Allies or Captains is itself a non-persistent power, judged
    where the pawn stands. An enemy whose pawn is at City Wall's site
    cannot use either without the ruler's consent.
- **The force.** The force question asks 0 to B+S, where B is the
  attacker's warbands on their board and S their warbands at the origin.
  It is headed "Commit warbands to the Campaign: 0 to N (B on your board,
  S at {site}), each adds one attack die", and suggests the whole N. With
  no warbands at the origin, the heading is the usual one.
- **Deaths.** The joined warbands stay at the site. Of D deaths, the
  site's share runs from max(0, D−B) to min(D, S).
  - When that range holds more than one value, the attacker answers
    "Choose how many of the D dead warbands come from {site}", a choice of
    amount that suggests the least from the site. Otherwise the one value
    applies without a question.
  - The question is asked once, at the kill step. When ROADMAP item 13b
    moves each kill to its moment, this question moves with it.
- **Loss powers.**
  - Billowing Fog and Traveling Doctor, chosen by the attacker, spare a
    defeated force's half: only the skulls and the sacrifices die. Peace
    Envoy kills none. Each records the spare when chosen, so the split
    asks only over the dead that really die.
  - Bear Traps kills a board warband only when one stands outside the
    force: board plus origin warbands exceed the force, and the board holds
    one.
  - Hospital and Cursed Cauldron count both kills.
  - Sticky Fire, used by a defender who wins, kills the attacker's whole
    board and every warband of theirs at the origin, since those joined
    the force (ruled 2026-10-16). Its line counts both.
- **Lines.** The ACTION writes "Campaign at {site}." The Campaign's start
  line is unchanged ("Raid against … · Wild Allies"). The losses line
  counts the attacker's dead at the origin.

## Slice 14: Off-turn actor

Ruled 2026-10-16. A walk started from a queue entry belongs to that
entry's actor, who may be off turn. Every other walk belongs to the turn's
player.

- **98 Sneak Attack.** "After another player's campaign, you may campaign,
  spending no Supply, if you declare them as the defender."
  - It is offered first, at the end of the other player's Campaign: its
    `Offer` goes to the front of the queue. The Campaign's other
    follow-ups, Second Wind's on either side, go to the back, and run after
    it whether it is taken or skipped. Taking it puts the Campaign `Run` at
    the front, as every taken offer does.
  - A Campaign that recorded a result triggers it. A Campaign skipped, or
    ended with nothing to attack (Vow of Union), does not. The holder is
    not that Campaign's attacker: they may be its defender or uninvolved.
    An off-turn Campaign counts, so a defender's Second Wind Campaign is
    another player's Campaign.
  - The entry's one start argument names the earlier attacker as the
    declared defender. Only kinds where that player defends are legal: a
    Conquest of a site they rule, or a Raid of their pawn there. The
    defender question offers only them, and a Conquest's extra targets are
    sites they rule. The argument is accepted only from a queue entry with
    a source, as slice 13's origin is.
  - The offer is queued only when the holder has a legal kind against the
    earlier attacker. A start that a restriction refuses (Vow of Peace)
    still shows Take unavailable with its reason.
  - The Campaign is the holder's, off turn: they choose their own
    modifiers and plans as the attacker.
  - Sneak Attack is a single card, so the earlier "several holders, in
    turn order" cannot arise, and is dropped.
- **16 Second Wind** (defender side). "[secret] [favor-burnt] After you're
  victorious, you may travel and then may campaign, spending no Supply for
  either."
  - The plan is offered on both sides. A winning player-defender gets
    Travel, then Campaign, as `Offer` entries at the back, spending no
    Supply. A Bandit defender gets nothing.
  - The defender pays off turn: the secret flips facedown and the favor
    burns. A chain still ends only when the cost cannot be paid.

The shared rules:

- **Starts.** A queued start accepts its entry's actor as the requester. A
  menu start still requires the turn's player.
- **Off-turn costs** settle off turn, since it is another player's turn:
  favor to the bank, secrets flip facedown. Placements and Vow of
  Wandering's Imperial warbands stay on the turn's player.
- **Special Envoy off turn** waives the Supply, and does not end the Act
  Phase: it is not the traveller's Act Phase.
- **Modifiers off turn.** The off-turn actor picks their own Campaign and
  Travel modifiers and battle plans. Spiteful Mirror's per-turn uses are
  not reachable, since only Campaign and Travel run off turn.
- **Lines.** A run's actor is the actor of its dequeued entry, and the
  lines inside omit that actor as on-turn lines do. When the run's actor
  is not the turn's player, the tag starts with their name: "Blue ·
  Campaign · Sneak Attack". A skipped offer whose actor is not the turn's
  player reads "Sneak Attack: Campaign skipped by Blue."
- **Client and undo.** No change. The client already shows a decision's
  controls to its owner, the HUD stays on the turn's player, and issuer
  gating already stops the turn's player undoing an off-turn actor's
  answers.

## Slice 15: Palanquin

- **107 Palanquin.** "[favor] ACTION: Choose a player whose pawn is at your
  site. Put your pawn on a site that they can travel to. Make them travel to
  that site, spending no Supply." Ruled by the product owner on 2026-10-16.
  - **The target** is another player whose pawn is at the user's site. The
    user cannot choose themselves.
  - **Destinations** are every site but the user's, in map order. Each is
    judged by previewing the target's queued Travel. A site that Travel
    refuses is unavailable with its reason: "Cannot travel to other sites
    in the region" for a Narrow Pass, the start's own text when it has
    one, else "Cannot travel there now".
  - **The fixed destination.** The target's Travel goes to the chosen site
    and asks no destination question. Its modifiers, costs and
    after-travel powers are the target's (Hospitality, Tyrant, Brass Army,
    Grasping Vines). Only the Supply is waived.
  - **Order.** The target, the destination, the toll question when the
    user rules Toll Roads, then the user's pawn is placed at the
    destination (a placement, so Brass Army asks the user at once), and
    the target's Travel is queued as a `Run` at the front.
  - **When it is usable.** A target who can reach no site, directly or by
    consent, is unavailable with "Cannot travel anywhere now". With no such
    target, the Act menu shows "No valid enemy pawn". Ward of Silence shows
    the same reason when nobody at the site has a faceup secret, and keeps
    "Nothing to do now" when its card cannot take a secret.
  - **The sender.** A queued walk names the player whose power sent it. A
    restriction that would block it, and that the sender could waive, does
    not block it: a Narrow Pass the user rules lets the target through. A
    pay-or-ask step inside the walk is still the traveller's to answer.
  - **Waivable restrictions** on the target's Travel (Narrow Pass, and any
    later tree-level waivable restriction on a Travel) make the destination
    askable. The asks go to their granters, on the user's behalf. The grant
    is remembered under the key the target's own Travel checks: the
    target's answer of that site to the destination decision.
  - **Toll Roads ruled by another player.** This ruling began as "the
    target must consent to pay its ruler"; the product owner widened it on
    2026-10-16 so the ruler may waive the toll instead. A destination where
    Toll Roads would charge the target is askable two ways, the target
    agreeing to pay or the ruler waiving it, and the user may ask either
    player or both. A target with no favor cannot be asked. A refusal
    stands for the turn.
  - **Toll Roads ruled by bandits.** Only the target is asked, to burn 1
    favor. With no favor the destination is unavailable: "{Blue} has no
    favor for it."
  - **The user rules Toll Roads.** The destination is available, and a
    question of the user's follows the pick: "Take 1 favor", which needs
    the target's consent, or "Waive the toll". Option labels cannot name a
    player, so the ask's hover names the target.
  - **Inside the sent Travel** no toll question is asked. If the target
    agreed, they pay, unless the user, ruling Toll Roads, waived it; the
    queued entry then carries the toll's free option after the site.
    Otherwise the toll was waived, and the line reads "{Blue} paid
    nothing, with {Red}'s consent."
  - **One granter, two grants.** A player asked for a restriction's waiver
    who also rules the Toll Roads that charges the target answers for both,
    and the target is not asked.
  - **Settling an either-or request.** The first alternative to grant
    settles it, and the other prompts close. An alternative's refusal
    leaves the others open, and the request is refused once every
    alternative refuses. A required ask must still grant, and its refusal
    settles the whole request.
  - **Wording.** Hovers carry the card's name: "Toll Roads: {Blue} must
    agree to give 1 favor to {Red}, or {Red} must waive it."; "Toll Roads:
    {Red} must waive it." with no favor; "Toll Roads: {Blue} must agree to
    give 1 favor to {Red}." after the ruler refused; "Toll Roads: {Blue}
    must agree to burn 1 favor." under bandits. Refusals read "{Blue}
    refused.", "{Red} refused to waive it." or "{Blue} refused, and {Red}
    refused to waive it." The target's prompt reads "{Green} wants to send
    you to {Coast}. Consent to pay Toll Roads and travel to {Coast}?"; the
    ruler's keeps "Give {Green} consent to ignore Toll Roads and select
    {Coast}?". The log writes "Asked {Blue} for consent to pay Toll Roads
    and travel to {Coast}." for the target and keeps the ignore line for
    every other ask.
  - **Preferences.** The ruler's Toll Roads preference applies to their
    ask. The target's agreement belongs to no card's ruler, so no
    preference applies, and the target is always asked by hand.
  - **The Game Log.** Palanquin's run reads "Moved to {Coast}; {Blue}
    travels there." The target's run is tagged "{Blue} · Travel ·
    Palanquin".
  - **Client.** An askable option with alternatives opens a choice: one
    button per alternative, then "Ask both" (or "Ask all" past two), and
    Cancel. The requester waits with "Waiting for {Blue}'s or {Red}'s
    consent…" while only alternatives are open.
