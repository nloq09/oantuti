export class ServerClock {
 update(serverTime,received=Date.now()){const stamp=Date.parse(serverTime);if(Number.isFinite(stamp))this.offset=stamp-received;}
 remaining(deadline,now=Date.now()){const target=Date.parse(deadline);return deadline&&Number.isFinite(target)?Math.max(0,Math.ceil((target-now-(this.offset||0))/1000)):null;}
}
