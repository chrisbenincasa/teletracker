import _ from 'lodash';
import { Thing } from '../types/external/themoviedb/Movie';

const tmdbMetadataPath = ['metadata', 'themoviedb'];
const tmdbMoviePath = tmdbMetadataPath.concat(['themoviedb.movie']);

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

const posterExtractors = ['movie', 'show']
  .map(t => makePath(t, 'poster_path'))
  .map(p => _.property<Thing, string>(p));

// Provides the path of the poster image
export const getPosterPath = (item: Thing) => {
  return fallbacks<Thing, string>(posterExtractors)(item);
};

const descriptionExtractors = ['movie', 'show']
  .map(t => makePath(t, 'overview'))
  .map(p => _.property<Thing, string>(p));

export const getDescription = (item: Thing) => {
  return fallbacks<Thing, string>(descriptionExtractors)(item);
};
