// Transport clocks advance even when the read model has not changed.
export const readSnapshotKey=value=>JSON.stringify(value,(key,item)=>key==='serverNow'||key==='serverTime'?undefined:item);
