# Undo

**Status:** designed 2026-10-06. It replaces the roadmap's **Undo system**
item under Later. The roadmap item allowed undo only before an action
started; this design allows it inside a running walker and across an
action's end.

Vocabulary: [CONTEXT.md](../../../CONTEXT.md) defines **parked decision**,
**draft set** and **situation**. This design adds **command batch**,
**barrier**, **void marker** and **taken-back group**.

## Goal

A player who misclicks can take the step back, as long as the step showed no
one anything new and no other player has answered since. A player who takes
back a step by mistake can restore it, as long as nothing else has happened.
Other players see what was taken back.

## Terms

- **Command batch.** The events one accepted command appends, in one atomic
  `repository.append`, opened by a `CommandAccepted` event. Every
  parked-decision answer is its own batch. A batch can span procedures and a
  turn handover (`FinishRest` runs the round end and the next Wake in its own
  batch).
- **Issuer.** The player whose command produced a batch, recorded as
  `CommandAccepted.by`: the command's actor. For `ResolveWalker` that is the
  answering player, who may be off turn.
- **Live batch.** A batch that no void marker currently voids.
- **Barrier.** A batch that undo never takes back. Because undo only ever
  takes back the latest live batch, a barrier also shields every batch before
  it.
- **Void marker.** A journaled event that voids a range of earlier events
  (`StepTakenBack`) or restores one (`StepRestored`). A marker is not a
  command batch for undo purposes.
- **Taken-back group.** A contiguous run of voided Game Log lines, shown
  struck through and collapsed.

## Rulings

### Who may undo, and how far

1. Undo takes back exactly one command batch: the journal's latest live
   batch. Each press takes back one more.
2. Only that batch's issuer may undo it. This makes another player's answer
   a barrier for everyone else with no extra rule: once the defender answers,
   the latest live batch is the defender's, so the actor cannot reach past
   it.
3. The off-turn issuer of the latest live batch may undo it. A defender who
   misclicks a battle plan takes it back while nobody has acted since.
4. The actor may undo while the walker is parked on another player's
   decision, as long as that player has not answered. Their question
   disappears and their draft set empties, because the position changed.
5. Undo crosses action ends, End Wake and Begin Rest. After undoing every
   step of action B, the next press continues into action A.
6. No consent is asked. The barriers already protect what other players
   committed and what was revealed.
7. Undo always takes back the whole batch, including any automatic effects
   other players' powers produced inside it (Herald's favor, Sealing Ward's
   dice).

### Barriers

A batch is a barrier when any of these holds:

- It holds an operation that `WalkerSearch.hides` reports: `Roll`,
  `Shuffle`, `Draw`, `Peek`, `Reveal`, or a `Flip` to faceup. Undo shares
  that one predicate with the legality search, so the two cannot drift.
- It holds a legacy minor-action reveal: `SiteRelicsPeeked` or
  `OwnedRelicRevealed`.
- It hands the turn over. That is every `FinishRest` batch, along with its
  round end, War Exhaustion's random fallback and the Wake victory checks,
  and a `BeginRest` batch that finishes Rest on its own because no REST power
  is usable.
- It is the Begin batch, which starts the game and runs Setup.
- It sets the game result.

Consequences the playtest should expect:

- A started Search cannot be undone: its draw is in the start batch.
- A started Recover cannot be undone, and neither can a "continue" answer:
  each rolls in its own batch.
- The last answer before a Campaign roll cannot be undone, because the roll
  runs in the same batch.

### Redo

1. Redo restores the most recently voided batch. Each press restores one
   more, in the reverse order of the undos.
2. Only that batch's issuer may redo it.
3. The redo stack is the run of batches voided by the void markers since the
   last command batch. Any command batch clears it, whoever issues it.
4. Redo leaves no trace in the Game Log. The restored lines return to normal
   and leave their taken-back group. The outcome is the same as if the undo
   had never happened.

### Game Log

1. Voided lines stay where they were, struck through.
2. Each contiguous run of voided lines is one taken-back group, however many
   undo presses made it. A single voided line is still a group.
3. A group is collapsed by default. Its header names the players who took the
   moves back: "p1 took back the following moves:", or "p1 and p2 took back
   the following moves:" when a run mixes issuers.
4. Inside a group, lines keep the action-section styling they had.
5. A viewer sees a voided line exactly as they saw it before the undo. The
   formatter already filters each line by viewer, and voiding does not change
   that.

### Controls

1. The action pane shows **Undo** to the player who may undo now, and
   **Redo** to the player who may redo now. Nobody else sees either control.
2. The label is always "Undo", including when the press would take back an
   action's start.
3. A hover note names what the press would change, taken from the first Game
   Log line of the affected batch, for example "Undo: p1 chose battle plan
   Battle Cry". A batch with no line of its own falls back to "Undo your last
   step" ("Redo your last step").
4. There is no keyboard shortcut.
5. The client-side modifier flow's Back and Cancel stay as they are. They
   discard drafts that were never sent and are not undo.

## Architecture

### Storage: batch boundaries and void markers

The journal stays the single append-only history. Undo and redo append
events. They never delete or rewrite one.

The journal does not record today where one command's batch ends and the
next begins. One batch can hold several walker closers (an action's end plus
the triggered Oathkeeper procedure, or Finish Rest plus the round end plus
the next Wake), and a legacy minor action appends events with no closer at
all, so boundaries cannot be inferred. Three new top-level `OathEvent` cases
make them explicit:

- `CommandAccepted(by)` opens every command batch. The application layer
  prepends it to the events a command produced, before encoding them. The
  rules engine never builds it, its evolve is the identity, and the Game Log
  posts nothing for it.
- `StepTakenBack(from, until, by)` voids the events with sequences in
  `[from, until)`: the latest live batch.
- `StepRestored(from, until, by)` un-voids that range again.

The markers carry their range explicitly, and replay checks it: a marker
whose range is not the latest live batch (`StepTakenBack`) or the top of the
redo stack (`StepRestored`), or whose `by` is not that batch's issuer, fails
replay like any other rejected event fact.

A journal written before this design has no `CommandAccepted`. Everything
before its first `CommandAccepted` counts as one barrier region, so such a
game loads unchanged and undo begins with its next command.

The `EventStreamRepository` contract, the HSQLDB schema, `expectedNextSequence`
and `StaleClientPosition` are unchanged. Sequence numbers are never reused.

### Replay: one forward fold

A new application-layer `JournalFold` replaces the service's use of
`EventReplayEngine` for loading and for `history`. `EventReplayEngine` stays
generic and unchanged; a plain `evolve(state, event)` cannot rewind, so the
fold lives beside it rather than inside it.

The fold replays every event in journal order, voided or not, and keeps:

- the current state;
- a **live stack**: the batches since the latest barrier, each with its
  range, issuer, the state before it and the state after it;
- a **redo stack**: the batches voided since the last command batch.

Per event:

- `CommandAccepted` closes the batch in progress, if any, and opens a new
  one. It clears the redo stack.
- `StepTakenBack` closes the batch in progress, checks the marker against
  the live stack's top, sets the state back to that batch's state before,
  and moves the batch onto the redo stack.
- `StepRestored` checks the marker against the redo stack's top, sets the
  state to that batch's state after, and moves the batch back onto the live
  stack.
- Any other event evolves the state through `OathRules.evolve`, as today.

When a batch closes, the fold decides whether it is a barrier (below). A
barrier empties the live stack and is not pushed.

Voided events are replayed once, when the fold first reaches them, from the
state they were made from. A marker then rewinds to a state the fold already
holds, so no snapshot is stored and no event is evolved twice. `WalkerReplay`
needs no change.

The fold yields three things, so loading, the log and the offers cannot
drift:

- the current state, which `load` returns;
- one `ReplayStep` per event, markers included, which `history` returns. A
  marker's step has the rewound state as its state after;
- the live stack's top and the redo stack's top, from which the projection
  builds the undo and redo offers.

### Commands and authorization

- `GameIntent.Undo` and `GameIntent.Redo`, with no arguments, sent with the
  client's `expectedNextSequence` like every intent. `GameCommand` gains
  matching cases, each carrying its actor.
- Both are accepted while a walker is pending. The service handles them
  before the walker-pending gate, which admits only `ResolveWalker` and
  `RollWalker`.
- `Authorization` admits any seated player. The service rejects a press from
  anyone but the issuer of the affected batch, and a press with nothing to
  affect, with new `OathViolation` cases.
- Handling an undo runs nothing in the rules engine. The service folds the
  journal, appends the marker for the live stack's top, and returns that
  batch's state before as the new state. A redo returns the state after.
- Every other accepted command appends `CommandAccepted(actor)` followed by
  the events it produced, in one batch.

### Barrier detection

One function decides whether a closed batch is a barrier, from the batch's
events and the states before and after it. A batch is a barrier when any of
these holds:

- it is the Begin batch: its state before is `NoGame`;
- an operation of one of its `WalkerStepRecorded` steps satisfies
  `WalkerSearch.hides`, or one of its steps carries a `RollPayload`;
- it holds `SiteRelicsPeeked` or `OwnedRelicRevealed`;
- the active player differs between its states before and after. This
  catches `FinishRest`, a `BeginRest` that finishes Rest on its own, and any
  other batch that hands the turn over, without naming commands;
- its state after has a game result.

The function lives beside `JournalFold` in the application layer, so the
rules engine stays unaware of undo.

### Projection

Each viewer's projection gains `undo: Option[StepOffer]` and
`redo: Option[StepOffer]`. A `StepOffer` holds the hover note. Each is
present only for the player who may press it. It also gains `logRevision`,
the sequence of the latest void marker (or none).

### Game Log

- `GameLogFormatter` formats live and voided events alike, from the fold's
  steps. Each voided event's step holds the states it was made from, so its
  lines read as they did before the undo. A marker posts no line of its own.
- A marker closes a segment, so a line before it never reads ahead past it.
- The formatter's in-progress `Run` rewinds on a marker to the run it had
  before the voided range, as the state does.
- After formatting, the formatter marks each entry posted by a voided event
  with its taken-back group.
- `LogEntry` gains the group it belongs to, if any. The group's issuers come
  from the markers that voided its batches.
- The client log grows by appending entries newer than the last
  `(sequence, ordinal)`. A void or restore changes existing lines, so when
  `logRevision` changes the client calls `resetLog()` and refetches the whole
  log. The seen marker in localStorage stays valid, because sequence numbers
  are never reused.

### Client sync

Polling accepts a snapshot only when its `nextSequence` is larger. Markers
grow the stream, so every client sees an undo on its next poll. A viewer's
draft set is bound to the position, so a parked question that disappears
takes its drafts with it, as it does today when the position moves.

### The facedown-adviser pick

The "Play facedown adviser" pick moves into the `play-facedown-adviser`
walker as its first parked decision: an ordinary board decision over the
player's facedown advisers. `StartWalker` then carries no pick. Today's
preselection carries over: with exactly one facedown adviser, its site is the
board draft. The pre-walker local choice, `FacedownAdviserDraft` and its
"Cancel action" button retire. Undo takes back the start instead.

## Slices

Each slice lands green and ships on its own.

1. **Engine, storage and server log.** `CommandAccepted` and
   `StepTakenBack` with their codec, `JournalFold` with its live stack,
   barrier detection, the Undo intent, command and authorization, the
   projection's `undo` and `logRevision`, and the formatter's taken-back
   groups.
2. **Frontend.** The Undo control and its hover note, the collapsible
   taken-back groups, and the client log reset on a changed `logRevision`.
3. **Redo.** `StepRestored`, the redo stack, the Redo intent and the
   projection's `redo`, and the Redo control.
4. **The facedown-adviser pick in the walker.** The board decision, the
   retirement of the pre-walker choice, and the removal of its Cancel.

## Testing

- Situation-based suites, one per barrier kind: a roll, a draw, a shuffle, a
  peek, a reveal, a faceup flip, a legacy reveal, `FinishRest`, setup and a
  result. Each checks that undo stops at the barrier.
- Chained undo: through every step of an action, across its end, into the
  action before.
- The off-turn issuer undoing their own answer, and the actor then undoing
  theirs.
- The actor undoing while the walker is parked on another player's decision.
- Rejections: a press by a non-issuer, a press with nothing to undo, a press
  on a barrier.
- Replay of journals with voided and restored ranges, including tampered
  markers, which must fail replay.
- A journal with no `CommandAccepted`, as written before this design, loads
  unchanged and offers no undo until its next command.
- Redo: restoring in order, the stack clearing on any command batch, and a
  press by a non-issuer.
- `WalkerReplayDriftSuite` stays green.
- The formatter groups contiguous voided runs, names mixed issuers, and
  restores lines on redo.
- jsdom: the Undo and Redo controls appear only for the right player, the
  hover note, the group toggle and the log reset.

## Left out

- A keyboard shortcut.
- Consent for undo. It belongs to **Phase - Consent system** if a table ever
  wants it.
- Undo past a barrier, with or without keeping the recorded outcome.
