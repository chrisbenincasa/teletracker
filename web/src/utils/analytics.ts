import ReactGA from 'react-ga';
import { GA_TRACKING_ID } from '../constants/';

export const initGA = () => {
  console.log('GA: Initialization');
  ReactGA.initialize(GA_TRACKING_ID);
};

export const logPageView = () => {
  console.log(`GA: Logging pageview for ${window.location.pathname}`);
  ReactGA.set({ page: window.location.pathname });
  ReactGA.pageview(window.location.pathname);
};

export const logEvent = (category: string = '', action: string = '') => {
  if (category && action) {
    console.log(`GA: Logging event for ${category} & ${action}`);
    ReactGA.event({ category, action });
  }
};

export const logException = (description: string = '', fatal = false) => {
  if (description) {
    console.log(`GA: Logging exception for ${description}`);
    ReactGA.exception({ description, fatal });
  }
};
