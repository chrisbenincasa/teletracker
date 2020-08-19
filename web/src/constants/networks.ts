import { NetworkType } from '../types';

export const Netflix = 'netflix';
export const Hulu = 'hulu';
export const AmazonVideo = 'amazon-video';
export const PrimeVideo = 'amazon-prime-video';
export const DisneyPlus = 'disney-plus';
export const HboMax = 'hbo-max';
export const Hbo = 'hbo-go';
export const AppleTv = 'apple-tv';

export const NetworkTranslation: { [k: string]: NetworkType } = {
  'apple-itunes': AppleTv,
};

export const AllNetworks = [
  Netflix,
  Hulu,
  AmazonVideo,
  PrimeVideo,
  DisneyPlus,
  HboMax,
  AppleTv,
];
