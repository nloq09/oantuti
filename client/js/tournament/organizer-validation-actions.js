export function finalValidationActions(validation,{retryWindowOpen}={}){
 if(retryWindowOpen!==true)return [];
 if(validation.status==='INFRA_ERROR'&&validation.canRetry===true)return ['retry'];
 if(validation.status==='FAILED'&&validation.canGrantEmergency===true)return ['emergency'];
 return [];
}
