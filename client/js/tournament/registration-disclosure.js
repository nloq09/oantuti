import {createRegistrationDisclosure as createCore} from './registration-disclosure-core.js';
import {renderTimelineRegistration} from './timeline-registration.js';
import {registrationForm} from './registration-form.js';
export {registrationDisclosureAvailable} from './registration-disclosure-core.js';
export function createRegistrationDisclosure(options){return createCore({...options,renderTimelineRegistration,registrationForm});}
