import _ from 'lodash';
import Thing, { HasThingMetadata } from '../types/Thing';
// import { Thing } from '../types';

const makePath = (typ: string, field: string) => {
  return ['metadata', 'themoviedb', typ, field];
};

const fallbacks = function<T, U>(x: ((x: T) => U | undefined)[]) {
  return function(p: T) {
    for (let i = 0; i < x.length; i++) {
      let v0 = x[i](p);
      if (v0) {
        return v0;
      }
    }

    return;
  };
};

const extractor = <T = string>(field: string) =>
  ['movie', 'show']
    .map(t => makePath(t, field))
    .map(p => _.property<HasThingMetadata, T>(p));

// Provides the path of metadata
export const getMetadataPath = <T = any>(
  item: HasThingMetadata,
  metadata: string,
) => {
  return fallbacks<HasThingMetadata, T>(extractor(metadata))(item);
};

export const getPosterPath = (item: HasThingMetadata) => {
  return fallbacks<HasThingMetadata, string>(extractor('poster_path'))(item);
};

export const getBackdropPath = (item: HasThingMetadata) => {
  return fallbacks<HasThingMetadata, string>(extractor('backdrop_path'))(item);
};

export const getProfilePath = (item: HasThingMetadata) => {
  return fallbacks<HasThingMetadata, string>(extractor('profile_path'))(item);
};

export const getTitlePath = (item: HasThingMetadata) => {
  return fallbacks<HasThingMetadata, string>(extractor('title'))(item);
};

export const getOverviewPath = (item: HasThingMetadata) => {
  return fallbacks<HasThingMetadata, string>(extractor('overview'))(item);
};

export const getVoteAveragePath = (item: HasThingMetadata) => {
  return fallbacks<HasThingMetadata, string>(extractor('vote_average'))(item);
};

type BackdropUrlSize = '300' | '780' | '1280' | 'original';

type PosterUrlSize = '92' | '154' | '185' | '342' | '500' | '780' | 'original';

const makeUrl = (path: string, size: string) => {
  let sizeStr: string;
  if (size === 'original') {
    sizeStr = 'original';
  } else {
    sizeStr = 'w' + size;
  }

  return TmdbPosterUrl(sizeStr, path);
};

const TmdbPosterUrl = (size: string, path: string) =>
  `https://image.tmdb.org/t/p/${size}${path}`;

export const getPosterUrl = (item: Thing, size: PosterUrlSize) => {
  let path = getPosterPath(item);

  if (path) {
    return makeUrl(path, size);
  }
};

export const getBackdropUrl = (item: Thing, size: BackdropUrlSize) => {
  let path = getBackdropPath(item);

  if (path) return makeUrl(path, size);
};

export const getDescription = (item: HasThingMetadata) => {
  return fallbacks<HasThingMetadata, string>(extractor('overview'))(item);
};
