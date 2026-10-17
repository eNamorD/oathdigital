# Consent system

**Status:** designed 2026-10-08. It covers the roadmap's **Phase - Consent
system**. All four slices are implemented ([slice 1 plan](../plans/2026-10-09-consent-slice1.md), [slice 2 plan](../plans/2026-10-09-consent-slice2.md), [slice 3 plan](../plans/2026-10-09-consent-slice3.md), [slice 4 plan](../plans/2026-10-09-consent-slice4.md)), and their plans' decisions are folded in below.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **parked decision**.
The [undo design](2026-10-06-undo-design.md) defines **command batch**,
**issuer**, **barrier** and **void marker**. This design adds **waivable
power**, **granter**, **askable option**, **consent request**, **consent
answer**, **consent preference** and **consent ledger**.

## Goal

A player blocked by an enemy's persistent power can ask that power's ruler
for consent to ignore it, for one specific choice. The ruler answers Yes or
No, or sets a standing preference that answers that player's future requests
automatically. Answers and preferences never get in the way of undo.
Preferences are private to the player who sets them.

## User story

Red rules the Narrow Pass. Blue wants to Travel to the Deep Woods, which the
Pass blocks.

1. In the Travel destination choice, Deep Woods is shown in amber, not grey.
   Its hover reason reads "Narrow Pass: Cannot travel to other sites in the
   region without ruler's consent."
2. Blue clicks Deep Woods. A confirmation asks "Ask Red for consent to ignore
   Narrow Pass?" **No** returns Blue to the destination choice. **Yes** logs
   "Asked Red for consent to ignore Narrow Pass and select Deep Woods."
3. Red is prompted: "Give Blue consent to ignore Narrow Pass and select Deep
   Woods?" The options are **Never Allow**, **No**, **Yes** and **Always
   Allow**, with the notice "You can adjust your consent preferences by
   clicking on the Narrow Pass power."
4. **Never Allow** and **No** log "Red gave no consent." Blue is back at the
   destination choice, with Deep Woods greyed out and showing the ordinary
   reason ("Narrow Pass: Cannot travel to other sites in the region."). Blue
   may pick another site or undo the Travel.
5. **Yes** and **Always Allow** log "Red gave consent." Blue's Travel
   continues as if Blue had selected Deep Woods.
6. **Never Allow** and **Always Allow** also set Red's standing preference
   for Blue on the Narrow Pass. From then on Blue's requests to ignore it are
   answered automatically. Blue sees the same log lines either way and cannot
   tell an automatic answer from a manual one.
7. While Red rules the Narrow Pass, clicking the power shows its text and a
   grid with one row per other player: **Never Allow**, **Case-by-case**
   (the default) and **Always Allow**. Red can change it at any time, even
   off turn. Each change writes a log line only Red sees.

## Rulings

### Scope

1. **Waivable powers.** A persistent power is waivable when its text
   restricts the ruler's "enemies" (CR p.29, NF p.16). This includes
   "cannot" powers (the Pass, Secret Police, Hall of Ministers), "cannot
   … unless they give favor" powers (Toll Roads, Forced Labor, Curfew),
   where consent skips the payment, and "must" powers (Grasping Vines),
   where consent skips the penalty. Powers worded "players" or "other
   players" (Lost Tongue, Sacred Ground, Rotting Fortress, Diplomat) are
   not waivable.
2. **Campaign actions.** No consent inside a Campaign action (CR p.29),
   except the Pass's own campaign-targeting clause, whose text grants it.
   Campaign-only cards (Gleaming Armor, Insect Swarm, True Names, Beast
   Tamer) are never waivable. Forgotten Vault is never waivable either:
   "target" means a Campaign's target selection only (catalog batch 3
   rulings), so all of its effect is inside a Campaign.
3. **Enforcement.** The game always enforces. There is no "forgot to
   enforce" rule (CR p.43, ambiguities decision 3). Enforcement is
   all-or-nothing: a waiver applies to every operation that happens at the
   same time. A waived Hall of Ministers lets all of that moment's discards
   through (official ruling on the Hall of Ministers, recorded 2026-10-09).
4. **Bandits** never consent. An option blocked by a bandit-ruled power is
   not askable.
5. **Own powers.** A ruler is never asked about a power they rule; ruling it
   already ignores it.
6. **Ignore effects** such as Forest Paths still drop the Pass without anyone
   being asked, because ignore hooks run before restrictions are checked.

### Requests

7. **Two request points, one mechanism.** A request is always made from an
   askable option. For a hard block it is the blocked option itself. For a
   toll or penalty power, the step becomes a choice such as "Pay 1 favor" or
   "Travel without paying", and the free option is the askable one. A
   refusal leaves only the paying option.
8. **Askability.** An option is askable only when every block on it comes
   from a waivable power, no granter is the bandits or the requester, and no
   answer for it has been refused this turn. Otherwise it is unavailable, as
   today.
9. **Several granters.** One prompt per granter covers all of that granter's
   powers on the option. All granters are asked at once; the first refusal
   closes the other prompts. Grants given before that are remembered. This
   needs several pending asks, which land in the first slice where two hard
   blocks from different rulers can fall on one option.
10. **Automatic answers.** For each granter: if any of its powers on the
    option is set to Never for the requester, the answer is No. If all are
    set to Always, the answer is Yes. Otherwise the granter is prompted, and
    the prompt covers all of that granter's powers on the option.
11. **Speed leak.** An automatic answer arrives at once, so the requester
    can infer it. This is accepted for now.

### Memory and undo

12. **Answers last the turn.** An answer is remembered for the rest of the
    turn for its exact key: power, requester, decision and target. A refused
    target stays unavailable. A granted target can be selected again without
    asking. A preference set later in the same turn does not change a
    remembered answer; it applies only to targets nobody has asked about yet.
    An answer binds its granter: it counts only while the player who gave it
    may still answer that key. A ruler who loses the site, to another player
    or to bandits, no longer lifts or blocks anything with it, and the new
    ruler may be asked (catalog batch 8, slice 10). A refusal, which settles
    the whole request, is the refuser's for every key it settles.
13. **Answers never block undo.** The requester may undo past a consent
    answer, as far as the ordinary barriers allow. The answer stays
    remembered and its log lines are never struck through.
14. **Withdrawing.** The requester may undo while a request is unanswered.
    The request is withdrawn, its log line is struck through, and nothing is
    remembered.

### Preferences

15. **Key and default.** A preference is stored per granter, power and
    requester. Missing means Case-by-case.
16. **Who sees it.** The grid is shown only to the player who currently
    rules the power's card, and only that player's own preferences.
17. **Reset.** When the granter stops ruling the card, that card's
    preferences are cleared and the granter sees a private log line. A card
    lost and regained within a single command keeps its preferences; this is
    accepted.
18. **Changing.** A change is accepted at any time, including during another
    player's parked decision. It is never undoable and never becomes a
    command batch.
19. **Changing while prompted.** An open prompt stays open after the granter
    changes a preference. The change applies only to requests made later.

## Architecture

Consent lives beside the walker, not inside its procedure tree. Requests,
answers and preferences are their own commands and events, kept off the undo
stack. The walker gains one new thing: options in an askable state.

### State

`ReadyGame` gains `consent: ConsentState`, next to `knowledge`:

- `preferences: Map[(PlayerId, PowerId, PlayerId), ConsentPreference]`,
  keyed by granter, power and requester, where `ConsentPreference` is
  `Never | Always`.
- `answers: Map[ConsentKey, ConsentAnswer]`, where `ConsentKey` is
  `(power, requester, decisionId, target: DecisionOptionRef)` and
  `ConsentAnswer` is `Granted | Refused`. The decision id keeps Deep Woods as
  a travel destination apart from Deep Woods in a toll step. An operation
  restriction's grant is keyed by what it refuses instead: the Hall of
  Ministers' key is the requester, `operation.discard` and the card, so it
  covers that card's discard on any path for the turn, and, by ruling 3,
  every other discard of the same batch.
- `pending: Option[ConsentRequest]`, where `ConsentRequest` holds the
  requester, decision id, target, `asks: Vector[ConsentAsk]` and `covers`,
  the keys it settles when they are not the asked option's. A
  `ConsentAsk` is a granter, its powers and an optional answer. Slice 1 has
  one ask at most.
- `turn`: the turn `answers` belongs to.

The state sits outside `CurrentGameState` because undo rewinds to a saved
`CommandBatch.before`. Consent state has to be carried across that rewind.

### Events

New `OathEvent` cases under a sealed `ConsentEvent`. None opens a
`CommandAccepted` batch, and the journal fold closes any open batch when it
meets one, so a whole-journal replay batches exactly as live appends did and
consent stays off the undo stack.

- `ConsentRequested(request: ConsentRequest)`
- `ConsentAnswered(granter, granted: Boolean)`
- `ConsentRequestWithdrawn(requester)`
- `ConsentPreferenceSet(granter, power, requester, setting)`, where
  `setting` is `Never | CaseByCase | Always`. Case-by-case removes the entry.
- `ConsentPreferencesCleared(granter, power)`

A manual Never Allow or Always Allow emits `ConsentAnswered`, anything it
settles, and then `ConsentPreferenceSet` in the same append, so the
requester's log shows no gap between the question and the answer. An
automatic answer is `ConsentAnswered` alone, emitted in the requester's
command.

`answers` belong to `turn`: they read as empty once the turn has changed,
and the next settlement replaces them. That expiry writes no event and no log
line.

### Undo

On `StepTakenBack`, the fold takes the batch's `before` state and puts the
current `ConsentState` back onto it, minus any pending request. Grants,
refusals and preferences therefore survive undo. `StepRestored` does the
same.

When a consent request is pending, the requester's `Undo` does not take back
a batch. It emits `ConsentRequestWithdrawn`.

A stale-preference clearing is not undone. Undoing the batch that took a card
from its ruler gives the card back but not the preferences, since the ledger
outlives undo (ruling 17).

### Commands

The pending-walker gate admits three new `GameCommand`s.

**`RequestConsent(requester, decisionId, target)`**

1. Validate four conditions: the walker is parked at `decisionId`, owned by
   `requester`; the look-ahead marks `target` askable; no answer for it is
   remembered this turn; no request is pending.
2. Group the waivable breaches by granter into asks and emit
   `ConsentRequested`.
3. Emit automatic answers per ruling 10. Asks without one stay open and
   their granters are prompted.
4. Settle the request if it is complete (see below).

**`AnswerConsent(granter, choice)`**, where `choice` is
`Never | No | Yes | Always`.

1. Validate that `granter` has an open ask on the pending request.
2. For Never or Always, emit `ConsentPreferenceSet` for each power in the
   ask, then `ConsentAnswered`.
3. Settle the request if it is complete.

**Settling.** A request is complete when every ask has an answer, or when
any ask is refused.

- On any refusal, record Refused for every key and clear the request. The
  parked decision re-renders with the target unavailable.
- When all asks are granted at a choose-one, record Granted, clear the
  request, and answer the parked decision with `target` for the requester.
  That answer opens a `CommandAccepted(requester)` batch even though the
  granter's command produced it, so the requester can undo the move itself.
  This is the one case where a batch's issuer is not the command's actor.
- At a choose-many, a grant answers nothing. It records Granted and clears
  the request, and the decision re-renders with the target choosable. The
  requester goes on choosing, so no batch opens.

**`SetConsentPreference(granter, power, requester, setting)`**

Validate that `granter` rules the power's card now, the power is waivable,
`requester` is another player and the game is not over. Emit
`ConsentPreferenceSet`. Accepted at any time.

**While a request is pending,** `ResolveWalker` and `RollWalker` are
rejected with a new `OathViolation.ConsentPending`. Accepted: `AnswerConsent`
from an asked granter, `Undo` from the requester, and
`SetConsentPreference` from anyone.

**After every command,** the application compares each stored preference
with the current ruler of its card. Where they differ it appends
`ConsentPreferencesCleared`.

### Walker and powers

**Powers declare waivability; they never read the ledger.**
`Restriction` and `OptionRestriction` each have a field
`waiver: Option[Waiver]`. A `Waiver` holds `granter: ReadyGame =>
Option[PlayerId]` (the ruling player; none for bandits, the Empire or
nobody), the `source` that names the power, and the consent-worded `note`.
Campaign-only contributions never set it, which enforces ruling 2 card by
card.

The Pass sets it on its Travel `Restriction` in slice 1 and on its campaign
`OptionRestriction` in slice 2. Its "the actor rules the Pass" check stays
(ruling 5). Only the consent half of its old approximation goes.

**One ledger check in `WalkerPowerGather`.** A breach of a waivable
contribution is dropped when `answers` holds Granted for its power, the
requester, the decision and the reference:

- For an `OptionRestriction`, the reference is the option being judged.
  Its key names the decision and the option, so the grant is tied to its
  decision.
- For a tree-level `Restriction`, it is the walk's `Answered` value for the
  decision.

**The look-ahead decides askability.** `lookAheadNoted` keeps only the first
new breach today. It will collect all new breaches. The option becomes
`DecisionOption.Unavailable(option, OptionReason.Askable(power, note, asks))`
when ruling 8 holds, the decision is a choose-one, and a search with the grant
remembered reaches a legal end from it; otherwise it keeps its ordinary
reason. On the `restrictOptions` path that `OptionRestriction` uses, an
option that only waivable restrictions forbid becomes askable at a
choose-many with no search: a grant there answers nothing, so it cannot
leave a request waiting on a grant that fails. At a choose-one hooked by
its own window, the fold keeps the option's ordinary reason, and the
walker makes it askable where it narrows the parked decision, when a
search with the grant remembered reaches a legal end from it
(`optionAsks`). A search that finds no answer at such a decision still
stops there when an option may be asked about, and the walk keeps the
decision, so a player whose only way on needs consent may ask. The
tree-level check that refuses a required decision with every option
forbidden does not count such a choose-one option as forbidden; the
search at the decision judges the grant instead. An optional choose-many left
with only askable options is still asked, and `wellFormed` accepts it with
nothing to pick yet, so its player may ask or answer without the target.

**Reasons.** Each waivable contribution declares a consent-worded `NoteKey`,
used only while the option is askable. The Pass gets `no-travel-consent`:
"Cannot travel to other sites in the region without ruler's consent." Its
campaign clause gets `no-target-consent`: "Cannot target other sites in the
region without ruler's consent." Secret Police gets `no-faceup-consent`:
"Cannot play a Vision faceup without ruler's consent." The Hall of
Ministers gets `no-discard-consent`: "Cannot discard cards from its ruler's
sites without ruler's consent.", registered as a game-rule note.
Each toll step's free option has an ordinary and a consent reason:
Toll Roads `no-free-travel` ("Cannot travel to its ruler's sites without
giving favor.") and `no-free-travel-consent` ("… without giving favor or
ruler's consent."), Forced Labor `no-free-search` and
`no-free-search-consent` ("Cannot search at its ruler's sites …"),
Curfew `no-free-trade` and `no-free-trade-consent` ("Cannot trade with
its ruler's cards …"), and Grasping Vines `must-kill` ("Must kill a
warband when traveling from its ruler's sites.") and `must-kill-consent`
("…, unless ruler consents."). A favor toll's paying option, when its
payer is short, reads "Needs 1 favor; you have 0", counting the favor the
rest of the cost takes first. A refused option uses
the ordinary reason.

**Toll and penalty steps.** Toll Roads, Forced Labor, Curfew and Grasping
Vines each ask a choose-one, "Give 1 favor to Red" (or "Kill 1 warband")
or "Travel without paying", in the window `PowerWindow.PayOrAsk`
(`PayOrAsk`). The free option is barred by that power's waivable
`OptionRestriction` until its ruler consents. Its key names the occasion
(the destination, the pawn's site, the traded card, the site left), so a
grant covers that occasion for the turn. The step appears only when a
player other than the actor rules the card; under bandits the toll or
the kill is taken unasked. Grasping Vines asks only a traveller with a
warband on their board.

Every question of a cost is asked before any of it is paid: each step
goes first in the cost node, and every payment, Way Station's included,
runs after the rest of the cost. In a Search's or a Trade's cost node
that is last. A Travel's cost node also holds the pawn's move, so its
payments run just before the move (`TravelRoute.beforeMove`). A resume
folds the node again against the state it parked in, and Way Station
decides whether to ask from the favor held. Nothing after the move is a
decision today; a power that puts one there would bring this hazard back.

A payer who cannot pay sees the paying option unavailable with what it
needs, and may ask to go on free. After a refusal the step has nothing to
choose; Undo takes back the pick that led there, before anything hidden
is shown, and the pick is then shut for the turn. Paying keeps its lines;
going free writes "Gave nothing, with Red's consent." or its card's
parallel line. A preview follows an answer through a pay-or-ask step, and
reads a short payer's free option as if granted, so a destination's
badge and a shortfall reason still show the toll.

**Operation restrictions.** The Hall of Ministers is an operation
restriction, which refuses a discard wherever it runs. Its refusal names
an `OperationWaiver`: the power, the granter and the key a grant covers.
`ConsentedRestriction` wraps each printed restriction in
`OperationRestrictions.active` and drops a refusal whose grant is
remembered, the operation path's one ledger check. A batch's operations
happen at the same time, so `OperationConsent.together` lifts a power for
the whole batch once any of its operations has a grant (ruling 3): a grant
for a Replace slot's card frees the follow-up's other site cards. Card play's Replace
site slots and their follow-up, and the site-card questions of Roving
Terror, Taming Charm and Great Feast, judge each option's plan:
`OperationConsent.ask` makes it askable when only waivable refusals stop
it, with no search. A site-card question is asked only when some card
may be chosen. Discards with no option behind them (Dark Enforcer,
region discards, Mounted Library, setup, a Campaign plan) stay enforced
without consent.

**Settling a grant** runs the ordinary walker resume. The ledger check drops
the breach, so there is no special path.

### Projection

- `DecisionOptionProjection.state` gains `"askable"`, and the projection
  gains `consent: Option[ConsentAskProjection]` with the granters and powers,
  which the client uses to word the confirmation.
- While a request is pending, the requester gets `consentWaiting` ("Waiting
  for Red's consent…") with Undo offered as withdraw. Each asked granter gets
  `consentPrompt` with the requester, powers and target label. A button's
  label is read from the parked decision. Other players
  see the usual waiting banner. `awaitedPlayers` is unchanged, since it hands the parked decision to everyone it names; the prompt is its own field. The requester's `walkerDecision` is withheld while their request waits, and nobody is offered Redo.
- `consentPreferences` goes to the viewer only: one entry per waivable power
  the viewer rules, with a row per other player. A power a card prints
  carries the card's id, so the card opens the grid. A power is waivable
  when any of its contributions declares a `waiver`.
- No other player's preferences, and nothing that marks an answer as
  automatic, leave the server.

### Frontend

- An askable option gets `OptionState.Askable` and the classes
  `board-option-askable` and `walker-choice-askable`. It uses a new amber
  colour token from DESIGN.md, is clickable and keeps the reason as its
  `title`.
- Clicking an askable option opens a client-side confirmation. Yes sends
  `RequestConsent`.
- The granter's prompt panel shows the question, the four buttons and the
  preferences notice.
- Site power labels become clickable and open `CardInspectionOverlay` with
  the power text. For a power the viewer rules, the overlay also shows the
  grid. Each change sends `SetConsentPreference` at once. A site card that
  prints a power the viewer rules opens with the grid too.
- The confirmation and the grid open in the card overlay, the client's one
  document-level dialog. Site powers carry their power id, which the grid is
  matched by.

### Game Log

- Request: "Asked Red for consent to ignore Narrow Pass and select Deep
  Woods." The actor is omitted, as in other actor lines.
- Answer: "Red gave consent." or "Red gave no consent." The granter is named
  because they are off turn, and several granters may answer one request.
- Withdrawal marks the request line `takenBack`, with no "took back" group
  header.
- The formatter writes a private line only when it formats for that line's
  granter. Private lines:
  - Preference set: "Red now always allows Blue to ignore Narrow Pass."
    Also "never allows" and "decides case by case whether Blue may ignore".
  - Preferences cleared: "Red's consent preferences for Narrow Pass were
    reset."

### Alternatives (catalog batch 8, slice 15)

- **An alternative ask** (`ConsentAsk.alternative`) is one of several, any
  of whose grant will do. Palanquin asks Toll Roads this way: the target
  agreeing to pay, or the ruler waiving the toll.
- **Settling.** The first alternative to grant settles the alternatives,
  and the other prompts close. An alternative's refusal is remembered and
  the others stay asked; the request is refused once every alternative has
  refused. A required ask beside the alternatives must still grant, and
  its refusal settles the whole request. A request asks each granter once.
  A grant is remembered only once its request settles granted; a refusal,
  a refused settle or a withdrawal leaves only refusals behind.
- **`RequestConsent`'s `granters`** names the alternatives to ask; empty
  asks them all. A narrowed request keeps the required asks and the named
  alternatives, and covers only their keys.
- **Client.** An askable option with alternatives opens a choice: "Ask
  Blue", "Ask Red", then "Ask both" (or "Ask all" past two), and Cancel.
  The requester waits with "Waiting for Blue's or Red's consent…" while
  only alternatives are open.
- **A payer ask** is a player's agreement to pay rather than a ruler's
  waiver (`ConsentPowers.paying`). Its prompt reads "Green wants to send
  you to Coast. Consent to pay Toll Roads and travel to Coast?", and its
  log line "Asked Blue for consent to pay Toll Roads and travel to
  Coast." It belongs to no card's ruler, so no preference applies to it
  and it is always answered by hand.

## Testing

- **Fold:** every consent event; undo putting `ConsentState` back on the
  rewound state; redo; turn expiry; replaying a journal with consent events
  reproduces the same state.
- **Walker:** askable versus unavailable for one waivable breach, a bandit
  granter, a requester who rules the power, a waivable breach mixed with one
  that is not, a remembered refusal, and Forest Paths dropping the Pass. A
  Granted entry drops the breach and the resume proceeds.
- **Application scenarios:**
  - Request flow: manual Yes, No, Always and Never; automatic answers from
    preferences.
  - Undo: after a grant, after a refusal, withdrawing a pending request.
    Reselecting a granted target; a refused target staying unavailable after
    undo.
  - Gate rejections: `ResolveWalker` while a request is pending, an answer
    from a player who was not asked, a preference for a card the granter
    does not rule.
  - `ConsentPreferencesCleared` when the ruler changes.
- **Hidden information:** Blue's projection and log are identical after an
  automatic Yes and a manual Yes. No other viewer receives the preference
  rows or the private lines.
- **Frontend:** askable rendering, the confirmation, the prompt panel and
  the grid. One browser acceptance run of the user story, once a game with a player-ruled Pass can be seeded (roadmap play-test item).
- The coverage gate applies as usual.

## Slices

1. **Machinery and the Pass on Travel.** State, events, commands, the gate,
   undo carry-over, private log lines, askable options, the confirmation,
   the prompt, the grid and the cleared event. Docs: add CR p.29 and NF p.16
   to `docs/rules/rules-reference.md`, update the "Enemies cannot" consent
   row in `docs/rules/implementation-traceability.md`, and update the
   roadmap phase.
2. **The Pass on campaign targeting.** A waiver on its `OptionRestriction`,
   through the `restrictOptions` path.
3. **Hard-block cards.** Secret Police on the tree path, and the Hall of
   Ministers through its operation refusals. Forgotten Vault is never
   waivable (ruling 2).
4. **Toll and penalty cards.** Toll Roads, Forced Labor, Curfew and Grasping
   Vines, each converted to a pay-or-ask step, with the walker asking at a
   choose-one on the option path.

Several pending asks (ruling 9) land in the first slice where two hard
blocks from different rulers can fall on one option. Among cards still in
holding, Forbidden Forest with the Pass is the likely first case.

## Deferred

- A ruler offering consent without being asked.
- Hidden Passages ("if its ruler consents"), a positive consent on an
  unimplemented card.
- Imperial-law consent and Empire rulers. The game is all-Exile.
- Timeouts for unanswered requests, covered by the roadmap's unanswered
  off-turn decisions item.
- Hiding the speed leak of automatic answers (ruling 11).
- Hiding the sequence numbers a preference change or a clearing uses. The
  log shows no line for them, but the next line's sequence moves past them.
- Tying a grant to the decision that caused the breach on the tree path.
  The ledger check drops a waivable `Restriction`'s breach for any granted
  choose-one answer of the walk. The option path's key names the decision,
  so it is exact. The key does not name the granter. The Pass's Travel
  destination and Secret Police's placement are each the only such
  decision in their walk; the first change that puts two in one walk must tie them.
- Hiding the decision's phase from the requester while their request waits.
  The decision itself is withheld.
- Askable options on the client's shared board homes and variant toggles,
  and on a choose-many drawn in the panel. Those paths treat an askable
  option as unavailable or submit it directly; no option yet takes them.
- The Hall of Ministers on discards with no option to ask from: Dark
  Enforcer and the region discards (Dazzle, Threatening Roar, Riots)
  skip a protected card, and Mounted Library's self-discard has no
  consent point. A pay-or-ask step could reach them, once they offer an option. By ruling 3,
  one grant would waive the Hall for a whole region discard.
- Asking from a site-card question whose every candidate the Hall
  protects. A choose-one has no empty answer, so after a refusal it
  would wait with nothing to choose; the question is not asked.
- Keeping a campaigner's picks across a consent request. The client drops
  a board draft when the sequence moves, so after a grant the other
  targets are picked again.
