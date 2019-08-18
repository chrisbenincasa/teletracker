import _ from 'lodash';
import { Thing } from '../types';

const tmdbMetadataPath = ['metadata', 'themoviedb'];

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
    .map(p => _.property<Thing, T>(p));

// Provides the path of metadata
export const getMetadataPath = (item: Thing, metadata: string) => {
  return fallbacks<Thing, string>(extractor(metadata))(item);
};

export const getPosterPath = (item: Thing) => {
  return fallbacks<Thing, string>(extractor('poster_path'))(item);
};

export const getBackdropPath = (item: Thing) => {
  return fallbacks<Thing, string>(extractor('backdrop_path'))(item);
};

export const getTitlePath = (item: Thing) => {
  return fallbacks<Thing, string>(extractor('title'))(item);
};

export const getOverviewPath = (item: Thing) => {
  return fallbacks<Thing, string>(extractor('overview'))(item);
};

export const getVoteAveragePath = (item: Thing) => {
  return fallbacks<Thing, string>(extractor('vote_average'))(item);
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

export const getDescription = (item: Thing) => {
  return fallbacks<Thing, string>(extractor('overview'))(item);
};
