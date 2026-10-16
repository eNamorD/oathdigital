# Oath Digital

A digital implementation of the board game Oath. Rulebook terms (site, adviser,
denizen, relic, Chronicle, campaign, recover) are defined with citations in
`docs/rules/glossary.yaml`; this file holds only the terms the code adds on top
of the rulebook.

## Language

### Walker

**Start fact**:
Something the engine works out when a procedure starts and keeps until it
ends, because the walk itself changes it: Travel's starting site, once the
pawn has moved. The player never chooses it; a start selection is what the
player chose.
_Avoid_: start argument (that is the player's selection), pinned value

**Parked decision**:
A question the procedure walker has stopped on and is waiting for one player
to answer. At most one is parked per game at a time.
_Avoid_: pending decision, walker decision, park

**Phase menu**:
The parked decision over everything the active player may do now in Wake or
Rest: each option starts one procedure, and the phase's ending is always the
last. It is asked only when more than the ending is available; otherwise the
phase ends by itself.
_Avoid_: action list, phase controls, wake actions

**Form**:
The kind of answer a parked decision asks for: choose one, choose many, choose
an amount, partition, distribute, negotiate, or inspect. An inspect shows
cards in order and takes one answer, Done: it asks for acknowledgement, not a
choice. A parked decision has exactly one form, or none when it asks nothing (a
roll). The form determines what else the question carries: its options, its
sections, its slots, its cards, its bounds and the name of its confirm control.
_Avoid_: query type, decision type, panel type

**Surface**:
Where and how a viewer sees a parked decision: as a panel in the action pane,
or as clickable sites on the world board. A viewer sees at most one surface for
the parked decision, plus a waiting notice when the decision awaits someone
else.
_Avoid_: panel (a surface is one of the two kinds of panel), view, renderer

**Subject card**:
The card a parked decision is about, shown beside the question: the card being
placed, or the card a replacement would discard. A parked decision has zero or
more.
_Avoid_: context card, preview card, card in hand

**Roll feedback**:
The dice a parked decision shows beside itself: the faces rolled so far in one
pool, their score, and the target when the roll has one. A parked decision
declares at most one.
_Avoid_: roll outcome, difficulty, dice summary

**Answered options**:
The options already chosen at the parked decision when the same decision is
asked again in one action, such as the battle plans played so far this
Campaign.
_Avoid_: plans played (the panel heading, not the concept), history, answers

**Plan side**:
Which side of a Campaign a battle plan applies to: attack, defense, or both.
_Avoid_: badge, chip, colour, red/blue

**Board draft**:
The site a viewer has picked on the board for the parked decision and not yet
confirmed. A viewer has at most one, and only for a decision that asks to be
confirmed.
_Avoid_: pending selection, staged choice, highlighted site

### Note

**Note**:
One line of the Game Log in which a power says what its own effect did:
"Warning Signals: Blue redistributed their warbands." It is never text. It
is a source, a key and typed arguments, so a card it names still passes the
log's knowledge rule.
_Avoid_: log message, power text, log entry

**Note key**:
One line a power can write: its name and the wording that renders it. The
name is the wire spelling, journaled in `PowerNoted`, so a saved game's
notes stay readable only while their keys keep their names.
_Avoid_: note id, message key, line type

**Wording**:
The sentence a note key renders, as alternating text and argument slots. A
key carries its own; the Game Log holds no sentence a key does not.
_Avoid_: template string, note text, format

**Declared set**:
Every key a power may write. A power owns a key it defines itself, and
borrows one defined in another object, which it must declare too. A note
whose key is outside the writing power's declared set has no wording to
render.
_Avoid_: key list, note registry, declared keys list

### Consent

**Waivable power**:
A persistent power that restricts its ruler's enemies, whose ruler may
consent to a player ignoring it for one choice. A power worded "players" is
not one.
_Avoid_: optional power, consent power

**Granter**:
The player who may consent to a waivable power being ignored: the player who
rules it. Bandits, the Empire and nobody are never granters.
_Avoid_: ruler (the ruler may be bandits), owner

**Askable option**:
An option of a parked choose-one, or of a choose-many whose options a power
forbids one by one, blocked only by waivable powers whose granters are other
players, with nothing refused for it this turn, which a grant would open. An
option whose plan only a waivable operation restriction refuses (the Hall of
Ministers) is askable too. Choosing it asks the granters first. A grant
answers a choose-one with it, and only opens it in a choose-many.
_Avoid_: consent option, amber option

**Consent request**:
A player's ask, from an askable option, to the granters of the powers blocking
it. At most one waits at a time.
_Avoid_: consent prompt (that is what a granter sees)

**Consent answer**:
A granter's yes or no to a consent request, remembered for the rest of the
turn for its power, requester, decision and option.
_Avoid_: consent, permission

**Consent preference**:
A granter's private standing answer for one requester on one power: never,
always, or case by case (no entry).
_Avoid_: permission, auto-consent

**Consent ledger**:
The preferences, the turn's answers and the waiting request, kept beside the
game so that undo leaves them be.
_Avoid_: consent state (the type's name, not the concept)

**Pay-or-ask step**:
The choice a toll or penalty card puts to an enemy of its ruling player: pay,
or go on without paying. The free option is askable until that ruler
answers, even for a payer who cannot pay.
_Avoid_: toll prompt, consent step

### Action pane

**Act-action control**:
A control offered to a viewer for starting a major or minor action, listed by
the projection as legal for them. It starts an action; it never answers a
parked decision.
_Avoid_: button, action button, command

### Table session

**Draft set**:
Everything a viewer has staged at the table and not yet sent: the board
draft and the partition, distribute and selection drafts. A viewer has
exactly one, bound to one game, seat and position; it empties when any of
those change.
_Avoid_: session state, UI state, pending selections, the drafts

**Modifier decision**:
A major action's first walker decision, `{action}.modifiers`. It is asked
one modifier at a time, and each pick is paid at once. "Start" ends it.
Every major action has one; an action with nothing to pick is asked nothing.
_Avoid_: modifier stage, modifier step

**Table session**:
One viewer's live connection to one game from one seat. It knows the
position on display, holds the viewer's draft set, and says whether the
viewer can act; changing the game or the seat starts a new one.
_Avoid_: server session, connection, client state

### Game

**First-game input**:
Everything a game begins from before anyone plays: the Chronicle, the seated
participants and the recorded shuffle orders. Two games with the same input
begin identically.
_Avoid_: setup fixture, setup data, bootstrap

**Situation**:
A game reached by real play from a first-game input: every command issued and
every parked decision answered along the way, in order. Named situations are
Wake (Setup complete: the first player's Wake menu, or their Act when that
Wake had nothing to decide), Act (a player's Wake ended) and Rest (a player's Act
ended); any other is described by the steps that reach it. A situation may
also start at a Table: the rules adapter directly, the journaled adapter
through a service begun at that Table. A situation settles at a phase menu:
it stops there, unanswered, because the menu asks what the player does next.
_Avoid_: position (a journal sequence number), fixture, snapshot, initial
game, setup state

**Table**:
A game state assembled directly for a rule test, not reached by play: the real
first-game start made quiet (p1's Act, pawns at their own sites, nothing at the
sites), plus one stated fact per step. A rule test states every fact it relies
on and inherits nothing incidental from Setup.
_Avoid_: board fixture, base state, initial ready

### Undo

**Command batch**:
The events one accepted command appends, opened by `CommandAccepted` with
the player who issued it. Begin's batch has no opening.
_Avoid_: transaction, append, step (a step is one walker event)

**Void marker**:
The `StepTakenBack` event an undo appends, or the `StepRestored` event a redo
appends. It names the range of the command batch it voids or restores; nothing
is deleted from the journal.
_Avoid_: delete, rollback, revert event

**Barrier**:
A command batch that undo never takes back: it showed someone something
new, handed the turn over or ended the game. It shields every batch before
it.
_Avoid_: commit point, checkpoint

**Taken-back group**:
A contiguous run of Game Log entries whose events an undo voided, shown
struck through under one header.
_Avoid_: undone section, deleted lines
