package com.rpsbattle.worker;


/** Handle to a running match. Capacity is retained until its terminal result is archived. */
public record MatchExecutionResult(String roomId, String historyId) {}
