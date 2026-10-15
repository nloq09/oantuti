package com.rpsbattle.tournament.domain;
public enum ParticipantPolicy { BOT_ONLY,HUMAN_ONLY,HUMAN_OR_BOT;
 public boolean humans(){return this!=BOT_ONLY;}public boolean bots(){return this!=HUMAN_ONLY;}
}
