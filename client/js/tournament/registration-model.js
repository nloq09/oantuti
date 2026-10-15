export function controllerChoices(policy="BOT_ONLY"){return policy==="HUMAN_ONLY"?["HUMAN"]:policy==="HUMAN_OR_BOT"?["HUMAN","BOT"]:["BOT"];}
export const selectableBot=bot=>bot.status==="READY";
export function registrationPayload(controllerType,botId,accepted){if(!accepted)throw new Error("RULES_ACCEPTANCE_REQUIRED");if(controllerType!=="HUMAN"&&controllerType!=="BOT")throw new Error("INVALID_CONTROLLER");if(controllerType==="BOT"&&!botId)throw new Error("BOT_NOT_READY");return{controllerType,botId:controllerType==="BOT"?botId:null,rulesAccepted:true};}
