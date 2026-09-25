# Oath Digital

A digital implementation of the board game Oath. Rulebook terms (site, adviser,
denizen, relic, Chronicle, campaign, recover) are defined with citations in
`docs/rules/glossary.yaml`; this file holds only the terms the code adds on top
of the rulebook.

## Language

### Walker

**Parked decision**:
A question the procedure walker has stopped on and is waiting for one player
to answer. At most one is parked per game at a time.
_Avoid_: pending decision, walker decision, park

**Form**:
The kind of answer a parked decision asks for: choose one, choose many, choose
an amount, partition, distribute, or negotiate. A parked decision has exactly
one form, or none when it asks nothing (a roll).
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

### Action pane

**Act-action control**:
A control offered to a viewer for starting a major or minor action, listed by
the projection as legal for them. It starts an action; it never answers a
parked decision.
_Avoid_: button, action button, command
