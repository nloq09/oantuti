import test from "node:test";import assert from "node:assert/strict";import{contextLabel}from "../js/tournament/match-context.js";
test("match context describes the actual BO1 round without inventing a series score",()=>{assert.equal(contextLabel({format:"SINGLE_ELIMINATION",round:2,totalRounds:3,bestOf:1,gameNumber:1}),"Loại trực tiếp · Bán kết · BO1 · Ván 1/1");});
