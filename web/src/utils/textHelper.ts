import { Item } from '../types/v2/Item';

/*
    Parse initials from multi-word string
    Example 1: "Season 12" becomes "S12"
    Example 2: "Marc Maron" becomes "MM"
*/
export const parseInitials = (name: string, type: string) => {
  if (name.length === 0) {
    return '';
  }

  if (type === 'season') {
    var nameSplit = name.split(' ');
    nameSplit[0] = nameSplit[0].substring(0, 1);
    return nameSplit.join('');
  } else {
    var matches = name.match(/\b(\w)/g);
    return matches!.join('');
  }
};

// Format Run Time
export const formatRuntime = (runtime: number | number[], type: string) => {
  if (typeof runtime === 'number' && type === 'movie') {
    const hours = Math.floor(Number(runtime) / 60);
    const minutes = Number(runtime) % 60;

    if (hours > 0 && minutes > 0) {
      return `${hours}h ${minutes}m`;
    } else if (hours > 0 && minutes === 0) {
      return `${hours}h`;
    } else if (hours === 0 && minutes > 0) {
      return `${minutes}m`;
    } else {
      return null;
    }
  } else if (typeof runtime === 'object' && type === 'show') {
    if (runtime.length === 0) {
      return;
    }

    const max = Math.max(...runtime);
    const min = Math.min(...runtime);

    if (max === min) {
      return `Episode Length: ${max}m`;
    } else {
      return `Episode Length: ${min}-${max}m`;
    }
  } else {
    return null;
  }
};

/*
    Truncate text and append ellipsis
*/
export const truncateText = (text: string, lengthLimit: number) => {
  if (text.length > lengthLimit) {
    return `${text.substr(0, lengthLimit - 4).trim()}...`;
  }

  return text;
};

const getRating = (item: Item, provider: 'imdb' | 'tmdb') => {
  return item?.ratings?.filter(item => item.provider_shortname === provider)[0];
};

/*
    Get Vote Average
    This currently uses IMDB
*/
export const getVoteAverage = (item: Item) => {
  const imdbRating = getRating(item, 'imdb');

  return imdbRating?.weighted_average ? imdbRating?.weighted_average / 2 : 0;
};

/*
    Get Vote Count
    This currently uses IMDB
*/
export const getVoteCount = (item: Item) => {
  const imdbRating = getRating(item, 'imdb');
  return imdbRating?.vote_count || 0;
};

/*
    Get Vote Count Formatted e.g 4,000 === 4k
    This currently uses IMDB
*/
export const getVoteCountFormatted = (item: Item) => {
  const imdbRating = getRating(item, 'imdb');

  return imdbRating?.vote_count
    ? imdbRating?.vote_count > 999
      ? `${(imdbRating?.vote_count / 1000).toFixed(1)}k`
      : imdbRating?.vote_count
    : 0;
};
