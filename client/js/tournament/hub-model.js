export const hubTabs=[["live","Đang diễn ra"],["open","Đang mở đăng ký"],["upcoming","Sắp diễn ra"],["joined","Đã tham gia"],["finished","Đã kết thúc"]];
export function tournamentCta(t){return t.registered?"Xem đăng ký":t.status==="REGISTRATION"?"Đăng ký":t.status==="CHECK_IN"?"Check-in":["FINISHED","CANCELLED"].includes(t.status)?"Xem kết quả":"Vào giải";}
