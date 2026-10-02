# Test readability ledger

Date: 2026-10-02. Spec: [test readability sweep](2026-10-02-test-readability-design.md).
Status: survey in progress.

## Baseline

Measured on 2026-10-02 at commit f9abb76d with `.superpowers/readability/metrics.sh`.

- suites (root): 372
- suites (frontend): 43
- replayed setup files (execute()/initialReady): 7
- Table.start files: 157
- state patches (updateCurrent/copy(current =): 313
- @unchecked: 158
- numeric card ids: 84
- tuple destructures with _: 69
- Table.update escapes: 14
- test files over 800 lines: 4
- largest test file: 1829 src/test/scala/oathdigital/application/GameApplicationServiceSuite.scala

## Summary

| Area | R1 | R2 | R3 | R4 | R5 | R6 | R7 | R8 | R9 | R10 | R11 | Keep | Total |
|---|---|---|---|---|---|---|---|---|---|---|---|---|---|

## Calibration

## Spot check

## Shared fixtures

| ID | File | Helper | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 01-service

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 02-game-log-and-testkit

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 03-engine

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 04-powers-action

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 05-powers-play-and-economy

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 06-powers-phases-and-travel

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 07-campaign-targeting

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 08-model-catalog-serialization

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 09-setup-server-persistence

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 10-procedures

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 11-frontend-a

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|

## 12-frontend-b

| ID | File | Test | Rule | Problem | Planned outcome | Outcome | Commit |
|---|---|---|---|---|---|---|---|
