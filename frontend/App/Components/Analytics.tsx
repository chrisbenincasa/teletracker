import { GoogleAnalyticsTracker } from 'react-native-google-analytics-bridge';
import { version } from '../../package.json';

export const tracker = new GoogleAnalyticsTracker('UA-123012032-1');
export const appVersion = `v${version}`;