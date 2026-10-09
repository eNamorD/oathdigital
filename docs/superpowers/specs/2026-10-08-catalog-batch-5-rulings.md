# Catalog Batch 5: Per-Power Rulings

> Appendix to [the design](2026-10-08-catalog-batch-5-design.md). Each entry
> states the ruling agreed with the product owner on 2026-10-08. Card ids are
> catalog ids. Every rule that applies to the whole of
> [batch 3](2026-09-29-catalog-batch-3-rulings.md#rules-that-apply-to-the-whole-batch)
> and of [batch 4](2026-10-05-catalog-batch-4-rulings.md#rules-that-apply-to-the-whole-batch)
> applies here too.

## Rules that apply to the whole batch

- **The token-free rule.** Favor and secrets may not be placed on a card that
  already holds favor or secrets, unless a card explicitly allows it or a
  battle plan does it. The Rest cleanup returns the tokens on every card in
  play, advisers included, so a card that was used becomes usable again after
  the next Rest.
- **Edifices are denizens.** A power that names denizens or cards as Muster
  or Trade sources includes edifices.
- **"Counts as" applies everywhere.** A card that counts as more than one
  adviser of a suit counts that way in every count of a player's advisers by
  suit. It does not change a list of targets or a check for having one.
- **A suitless adviser is not an adviser of any suit.** A facedown adviser,
  Vision or denizen, has no suit.

## Slice 1: Muster and Trade sources

- **199 Golem Legions.** A selected Muster modifier: the actor may muster on
  Golem Legions, one of their own advisers. The token-free rule still
  applies, so Golem musters once between Rests unless Pressgangs is also
  selected. "It counts itself as a matching adviser" is a reminder: a faceup
  Golem is already one of the actor's Arcane advisers, so it adds nothing to
  the count.
- **76 Map Library.** A selected Trade modifier, usable while the actor's
  pawn is at Map Library's site. The actor may trade with any card, edifices
  included, at any site in the pawn's region, the pawn's own site too. The
  token-free rule still applies.
- **238 Skilled Merchants.** A selected Trade modifier from an adviser. The
  actor may trade with any denizen, edifices included, at any site in the
  pawn's region, the pawn's own site too. The token-free rule still applies.
  With Map Library, the two offer each card once.
- **229 Old Songs.** A selected Trade modifier. The actor may also trade with
  a card that holds exactly one secret and no favor. A card with one secret
  and any favor does not qualify. Token-free cards qualify as usual.
- **6 Pressgangs.** A selected Muster modifier. The actor may muster on a
  card that holds any number of favor or secrets. It does not apply to
  Trade. It composes with Golem Legions, so Golem may be mustered on twice in
  one turn.

## Slice 2: Warband placement

- **247 Vow of Wandering.** Its Muster power adds two warbands to the
  Muster's gain, best effort from the supply. While it is a faceup adviser,
  its holder cannot place warbands on a site, from anywhere: the board, the
  bank or another site. Moving warbands from a site to the board stays
  legal. After a Conquest victory, the placement question is not asked and
  the survivors stay on the board, with a line saying why.
  - The holder's warbands are those leaving the holder's board, and those
    of the holder's kind. Imperial warbands, which the Chancellor and the
    Citizens share, count only while the holder is the active player.
  - Garrison and Key to the City still gain their warbands, and place none.
    Bandit Prince replaces nothing. Hospital saves nothing.
  - Ballot Box: none of the warbands can be replaced, so it removes them
    all.
  - Messenger and Warning Signals may only bring warbands back to the
    board.
  - The minor action refuses a move to the site, naming the Vow.

## Slice 3: Adviser counting

- **148 Marriage.** Counts as two Hearth advisers in every count of the
  holder's advisers by suit, Muster and Trade matching included. Counts as
  one toward the adviser limit.
- **182 Pied Piper.** Does not count toward the adviser limit. Its ACTION
  moves Pied Piper into any other player's advisers, even when that player is
  at or over the limit, then takes two favor from them, best effort. If the
  receiver holds Family Wagon, the receiver buries Pied Piper (slice 4); the
  favor is still taken.
- **R29 Lost Tapestry.** The holder's facedown Visions do not count toward the
  holder's adviser limit.
- **At the limit,** an adviser that does not count toward the limit is shown
  unavailable for Replace, with the reason "Does not count toward the adviser
  limit".

## Slice 4: Adviser arrival

- **168 Family Wagon.** Its holder has no adviser limit. Whenever its holder
  gains an adviser, or an adviser turns faceup, while Family Wagon is faceup,
  the holder buries every adviser that is not a faceup Nomad. Family Wagon's
  own arrival counts, so it buries the advisers already held.
  - Facedown advisers are buried, Visions included, since a suitless card is
    not a Nomad adviser.
  - Locked does not stop a bury: Locked guards against a discard, and a bury
    is not a discard. Marriage is buried.
  - Advisers are buried in held order.
  - The arrival may come by any route: card play, Horned Mask, a swap, or
    Pied Piper's move.
  - A card play that would add a non-Nomad or facedown adviser stays on
    offer, with a note that the card will be buried.

## Moved out of the batch

- **Vow of Union** joins Sneak Attack in the engine-blocked phase. At the
  start of **any** Campaign, its holder may move warbands, which needs an
  off-turn actor.
