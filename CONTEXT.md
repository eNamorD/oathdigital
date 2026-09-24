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
