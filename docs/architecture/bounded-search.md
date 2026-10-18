# Search

Search runs on the procedure walker, like every other action. This note
states the rules it follows, the tree it walks, the windows its powers hook
and how its hidden draw stays private.

Design history: [search walker design](../superpowers/specs/2026-09-16-search-walker-design.md),
[search source design](../superpowers/specs/2026-10-07-search-source-walker-design.md),
[card play slots design](../superpowers/specs/2026-10-04-card-play-slots-design.md)
and the [modifier walker design](../superpowers/specs/2026-10-09-modifier-walker-design.md).

## Rules

The authoritative rule is Combined Rulebook (CR) p. 20. A Search chooses the
world deck or the discard pile in the pawn's region, spends the Visions Drawn
track cost for the world deck or 2 Supply for the regional discard, and draws
one card at a time up to three. A Vision drawn from the world deck stops the
draw and advances the track. The player keeps exactly one drawn card, puts
the other cards onto the top of the next-region discard (Cradle to Provinces,
Provinces to Hinterland, Hinterland to Cradle), then plays or discards the
kept card. CR p. 42 gives the turn player control over simultaneous
ordering, which is why discard order is the player's choice.

CR p. 20 also establishes these placement rules:

- a denizen played to the current site gives one favor from its suit bank;
- a full site rejects a denizen, except that a matching-suit Homeland may
  discard a site card first;
- a faceup site-only denizen cannot be an adviser, and an adviser-only denizen
  cannot be played to a site;
- facedown advisers have no suit, power, or play restriction;
- the adviser limit is three, so exceeding it first discards a non-locked
  adviser; locked cards cannot be discarded (CR pp. 20, 28);
- an Exile may reveal a Vision to the Vision space, replacing an existing
  revealed Vision, or hold it facedown as an adviser (CR p. 20).

The head of `worldDeck` is its top. A regional discard vector keeps its top
last, so a draw reads from the end and a discard appends. `SearchRules` in
`gameplay/actions/Search.scala` holds the cost, the pile order and the draw.
A power that draws more or fewer cards, or from the bottom, passes `extra` or
`position` to `SearchRules.draw`.

## The tree

`SearchProcedure` (`gameplay/actions/search/SearchProcedure.scala`) builds
the tree, and `WalkerProcedureRegistry` registers it as `ActionRef.Search`.
In order, the walk:

1. asks for modifiers in a `ModifierChoice` loop at `SearchModifierSelection`.
   Each pick pays at once, and "Start Search" ends the loop;
2. parks on `search.source`, a board choose-one of the world deck and the
   actor's regional discard. `SearchOptions.priced` shows what each pile costs
   under the picked modifiers, or why it cannot be searched;
3. spends the cost (`SearchCost`), then draws into the actor's temporary hand
   and advances Visions Drawn when a world-deck draw holds a Vision
   (`SearchBeforeDraw`);
4. writes the "Drew" Game Log line;
5. parks on `search.cards` when two or more cards were drawn: a Keep/Discard
   `Partition` that keeps exactly one card (`SearchKeep`). One drawn card is
   kept without a question;
6. writes the "Kept" line, discards the other cards to the next region's
   discard pile in the order the answer lists them, and plays each kept card
   through the shared card play subtree, `CardPlayProcedure`.

The whole body after the source answer sits in `SearchActionEligibility`.
A start is refused when a temporary hand already holds cards, or when the
pawn has no site. Supply, tolls and empty piles do not gate the start: the
source decision says why a pile is unavailable, and the registry's
playable-option gate refuses a start that no pile can complete.

Card play owns placement: its slots, replacement of a full site or adviser
row, favor for a site play, and the `CardPlayedFaceup` or
`CardPlayedFacedown` hook. The facedown-adviser play
(`ActionRef.PlayFacedownAdviser`) reuses the same subtree and modifier
window. The `Locked`, `SiteOnly` and `AdviserOnly` catalog traits are
enforced as operation restrictions (`LockedCards`, `RestrictionIcons`), not
by Search.

## Powers

Search powers live in `gameplay/powers/search/`. Each hooks one or more of
these windows:

- `SearchModifierSelection`: offers a player-selected modifier;
- `SearchSourceSelection`: adds piles to the source decision, warns one, or
  makes one unavailable. Spinning Bee removes the decision, and the walk then
  searches the actor's own regional discard;
- `SearchCost`: changes the Supply spent;
- `SearchBeforeDraw`: changes what is drawn, or adds a step at the draw;
- `SearchKeep`: lets the actor keep more than one card (Land Warden);
- `SearchPlayAdviser`: changes the adviser play inside card play, such as
  the adviser limit;
- `SearchActionEligibility`: adds a step after placement (Hunting Party).

See [power resolution](rule-resolution.md) for how a window gathers its
contributions.

## Draw and hidden information

The application checks the draw where it happens, at the `search.source`
answer. `GameApplicationService` asks the `SearchDrawPort` to prepare the
draw for the named pile and refuses the command with `SearchDrawMismatch`
unless it equals `SearchRules.draw`. The walker records the `Draw`
operation in a `WalkerStepRecorded` event. Replay applies the recorded
operations and never draws again.

The source decision is the commit point. The walker's look-ahead stops at a
`Draw` (`HiddenOutcomes`), so pruning never reveals what a pile holds. Once
the source answer is accepted, the cost is paid and the cards are drawn; the
Search must then complete.

The drawn cards are in the actor's temporary hand until placed. Only the
actor's projection shows them: in `temporaryHandPreview` and in the parked
`search.cards` decision on `walkerDecision`. Every other viewer sees a
`walkerWaiting` notice and never the cards. A card in a regional discard is
shown by its kind; its face is shown only to a viewer whose card knowledge
records it. The world deck shows its count and its top card's kind.

Journal events contain the drawn card identities, because replay needs them.
Raw event history is therefore a privileged view. Ordinary routes expose only
scoped projections and generic rejection messages.
