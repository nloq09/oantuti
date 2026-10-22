import test from 'node:test';
import assert from 'node:assert/strict';
import {installDocument,text} from './helpers/organizer-dom.js';
import {applicationQuery,queueFilters,bulkReviewPayload,localDateTime} from '../js/tournament/application-queue-model.js';
import {renderApplicationCard} from '../js/tournament/application-review-card.js';
import {openApplicationDetails} from '../js/tournament/application-details.js';
import {readRegistrationDashboard} from '../js/tournament/registration-dashboard-model.js';

const entry={id:'entry/a',displayName:'<img onerror=alert(1)>',intendedLanguage:'CPP',affiliation:'UET',status:'NEEDS_REVIEW',registeredAt:'2026-10-22T00:00:00Z',reasonCodes:['AFFILIATION_NOT_VERIFIED']};
test('search filters serialize only bounded API fields and retain explicit page sizes and UTC instants',()=>{
 const filters=queueFilters('?size=100&search=a%26b&language=CPP&affiliation=UET&reason=MANUAL_REVIEW_REQUIRED&from=2026-10-22T00%3A00%3A00Z&private=secret');
 const params=new URLSearchParams(applicationQuery(3,'NEEDS_REVIEW',filters));assert.equal(params.get('search'),'a&b');assert.equal(params.get('size'),'100');assert.equal(params.get('page'),'3');assert.equal(params.get('from'),'2026-10-22T00:00:00Z');assert.equal(params.has('private'),false);
 assert.equal(queueFilters('?size=4000').size,25);assert.equal(localDateTime('bad'),'');assert.match(localDateTime(entry.registeredAt),/^2026-10-22T\d\d:\d\d$/);
});
test('bulk payload snapshots only selected ids, statuses and registration timestamps with a mandatory reason',()=>{
 const payload=bulkReviewPayload('REJECTED',' Confirmed ',8,[{...entry,private:'hidden'}]);
 assert.deepEqual(payload,{decision:'REJECTED',reason:'Confirmed',revision:8,items:[{id:'entry/a',status:'NEEDS_REVIEW',registeredAt:entry.registeredAt}]});
 for(const entries of [[],Array(101).fill(entry),[entry,entry]])assert.throws(()=>bulkReviewPayload('APPROVED','reason',8,entries),/INVALID_BULK_REVIEW/);
 assert.throws(()=>bulkReviewPayload('REJECTED',' ',8,[entry]),/REASON_REQUIRED/);
});
test('filtered paging relies on server count and clamped page rather than unfiltered aggregate',async()=>{
 const calls=[],summary={revision:8,counts:{total:3842,needsReview:24}},t={revision:8};
 const result=await readRegistrationDashboard('/cup',{status:'',page:9,filters:{search:'one player',size:100},request:async url=>{calls.push(url);return url.endsWith('registration-summary')?summary:url.includes('participant-applications')?{revision:8,total:1,page:0,size:100,items:[entry]}:t;}});
 assert.equal(result.page,0);assert.equal(result.data.total,1);assert.match(calls.at(-1),/page=9&size=100&search=one\+player/);
});
test('review cards expose reasons immediately, support selection and keep untrusted display names as text',()=>{
 installDocument('<body></body>');let selected,decision,opened;
 const card=renderApplicationCard(entry,{reviewOpen:true,onSelect:(entry,checked)=>selected={entry,checked},onDecision:(...args)=>decision=args,onDetails:value=>opened=value});
 assert.match(text(card),/Đơn vị tự khai cần được xác minh/);assert.equal(card.querySelector('img'),null);
 const checkbox=card.querySelector('input');checkbox.checked=true;checkbox.onchange();assert.equal(selected.entry,entry);assert.equal(selected.checked,true);
 card.querySelector('textarea').value='Checked';card.querySelector('button').click();assert.deepEqual(decision,[entry,'APPROVED','Checked','']);card.querySelectorAll('button').at(-1).click();assert.equal(opened,entry);
 const readOnly=renderApplicationCard(entry,{reviewOpen:false,onDetails(){}});assert.equal(readOnly.querySelectorAll('textarea,input').length,0);assert.equal(readOnly.querySelectorAll('button').length,1);
});
test('details drawer reads manager-only detail without navigation and renders checks and audit history safely',async()=>{
 installDocument('<body></body>');let url,signal,closed=0;
 const {dialog,loaded}=openApplicationDetails('/cup',entry,{onClose:()=>closed++,request:async(path,_b,_m,options)=>{url=path;signal=options.signal;return{details:{application:entry,username:'username',rulesAcceptedAt:entry.registeredAt,accountActive:true},policy:{approvalPolicy:'HYBRID',requireAffiliationVerification:true},languageAllowed:true,canReview:false,history:[{at:entry.registeredAt,beforeValue:'PENDING',afterValue:'REJECTED',reason:'<script>bad</script>',actor:'organizer'}]};}});
 await loaded;assert.equal(url,'/cup/participant-applications/entry%2Fa');assert.match(text(dialog),/Cần ban tổ chức xác minh/);assert.match(text(dialog),/organizer/);assert.match(text(dialog),/Xét duyệt đã khóa/);assert.equal(dialog.querySelector('script'),null);dialog.querySelector('button').click();assert.equal(closed,1);assert.equal(signal.aborted,true);
});
test('closing a details drawer aborts late responses and prevents resurrecting private data',async()=>{
 installDocument('<body></body>');let resolve;const {dialog,loaded}=openApplicationDetails('/cup',entry,{request:()=>new Promise(done=>resolve=done)});dialog.close();resolve({details:{application:entry}});await loaded;assert.equal(document.querySelector('dialog'),null);
});

test('manager history distinguishes public-message changes from status transitions and renders audit text safely',async()=>{
 installDocument('<body></body>');const {dialog,loaded}=openApplicationDetails('/cup',entry,{request:async()=>({details:{application:entry,username:'player',rulesAcceptedAt:entry.registeredAt,accountActive:true},policy:{approvalPolicy:'HYBRID'},languageAllowed:true,canReview:false,history:[{type:'PARTICIPANT_APPLICATION_MESSAGE_UPDATED',at:entry.registeredAt,beforeValue:'<script>old</script>',afterValue:'New public explanation',actor:'organizer'}]})});
 await loaded;assert.match(text(dialog),/Đổi lời nhắn cho người đăng ký/);assert.match(text(dialog),/Trước: <script>old/);assert.match(text(dialog),/Sau: New public explanation/);assert.equal(dialog.querySelector('script'),null);dialog.close();
});
