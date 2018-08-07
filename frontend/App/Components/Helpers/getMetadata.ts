import { Thing } from "../../Model/external/themoviedb";
import * as _ from 'lodash';

// Returns true if the item has metadata
const hasTmdbMetadata = (item: Thing) => {
    return item.metadata && item.metadata.themoviedb;
};

// Returns true if the item is a movie
const hasTmdbMovie = (item: Thing) => {
    return hasTmdbMetadata(item) && item.metadata.themoviedb.movie;
};

// Returns true if the item is a show
const hasTmdbShow = (item: Thing) => {
    return hasTmdbMetadata(item) && item.metadata.themoviedb.show;
};

// Returns true if the item is a person
const hasTmdbPerson = (item: Thing) => {
    return hasTmdbMetadata(item) && item.metadata.themoviedb.person;
};

// Provides the path of the poster image
const getPosterPath = (item: Thing) => {
    if (hasTmdbMovie(item)) {
        // This throws a lens error pretty consistantly, requires further investigation.  Workaround in place for now.
        // return R.view<Props, Movie>(this.tmdbMovieView, this.props).poster_path;
        return item.metadata.themoviedb.movie.poster_path;
    } else if (hasTmdbShow(item)) {
        return item.metadata.themoviedb.show.poster_path;
    } else if (hasTmdbPerson(item)) {
        return; // There are no photos for people yet
    }
};

// Provides the release year of the item
const getReleaseYear = (item: Thing) => {
    if (hasTmdbMovie(item)) {
        // This throws a lens error pretty consistantly, requires further investigation.  Workaround in place for now.
        // return R.view<Props, Movie>(this.tmdbMovieView, this.props).poster_path;
        return item.metadata.themoviedb.movie.release_date.substring(0,4);
    } else if (hasTmdbShow(item)) {
        let firstAirDate = _.property<Thing, string>('metadata.themoviedb.show.first_air_date')(item);
        if (firstAirDate) {
            return firstAirDate.substring(0, 4); //We may want to consider making this last_air_date?
        } else {
            return null;
        }
    } else {
        return null; // There are no photos for people yet
    }
};

// Provides the season count, if applicable, of the item
const getSeasonCount = (item: Thing) => {
    if (hasTmdbShow(item)) {
        let numSeasons = item.metadata.themoviedb.show.number_of_seasons;

        if (numSeasons === 1) {
            return numSeasons + ' season | ';
        } else {
            return numSeasons + ' seasons | ';
        }
    } else {
        return null;
    }
};

const getSeasons = (item: Thing) => {
    let meta = item.metadata.themoviedb;
    if (hasTmdbMovie(item)) {
        return null; // There is no seasons parameter for movies
    } else if (hasTmdbShow(item)) {
        return meta.show.seasons; 
    } else {
        return;
    }
}

// Provides the episode count, if applicable, of the item
const getEpisodeCount = (item: Thing) => {
    if (hasTmdbShow(item)) {
        let numEpisodes = item.metadata.themoviedb.show.number_of_episodes;

        if (numEpisodes === 1) {
            return numEpisodes + ' episode';
        } else {
            return numEpisodes + ' episodes';
        }
    } else {
        return null;
    }
};

// Provides the runtime of the item
const getRuntime = (item: Thing) => {
    const formatRuntime = (Runtime: number) => {
        let hours = Math.floor(Runtime / 60);
        let minutes = Runtime % 60;

        if (hours > 0 && minutes > 0)  {
            return hours + 'h ' + minutes + 'm | ';
        } else if (hours > 0 && minutes === 0){
            return hours + 'h | ';
        } else if (hours === 0 && minutes > 0){
            return minutes + 'm | ';
        } else {
            return null;
        }
    }

    if (hasTmdbMovie(item)) {
        // This throws a lens error pretty consistantly, requires further investigation.  Workaround in place for now.
        // return R.view<Props, Movie>(this.tmdbMovieView, this.props).poster_path;
        return formatRuntime(item.metadata.themoviedb.movie.runtime);
    } else if (hasTmdbShow(item)) {
        return formatRuntime(item.metadata.themoviedb.show.episode_run_time[0]); // Need to identify what to do when run time is variable between episodes.  Maybe show ~x?
    } else if (hasTmdbPerson(item)) {
        return; // There are no photos for people yet
    }
};

const getVoteCount = (item: Thing) => {
    // Format 11,453 as 11.4k
    const formatVoteCount = (VoteCount: number) => {
        return VoteCount > 9999 ? `${Math.round((VoteCount / 1000)*10)/10}k` : VoteCount;
    }

    let meta = item.metadata.themoviedb;
    if (hasTmdbMovie(item)) {
        // return formatVoteCount(R.view<Props, Movie>(this.tmdbMovieView, this.props).vote_count);
        return formatVoteCount(meta.movie.vote_count);
    } else if (hasTmdbShow(item)) {
        return formatVoteCount(meta.show.vote_count);
    } else if (hasTmdbPerson(item)) {
        return; // There is no vote count for people
    }
}

const getDescription = (item: Thing) => {
    let meta = item.metadata.themoviedb;
    if (hasTmdbMovie(item)) {
        // return R.view<Props, Movie>(this.tmdbMovieView, this.props).overview;
        return meta.movie.overview;
    } else if (hasTmdbShow(item)) {
        return meta.show.overview;
    } else if (hasTmdbPerson(item)) {
        return; // There is no description for people yet
    }
}

const getCast = (item: Thing) => {
    let meta = item.metadata.themoviedb;
    if (hasTmdbMovie(item)) {
        // return R.view<Props, Movie>(this.tmdbMovieView, this.props).credits.cast;
        return meta.movie.credits.cast;
    } else if (hasTmdbShow(item)) {
        return meta.show.credits.cast.length > 0 ? meta.show.credits.cast : null; 
    } else {
        return;
    }
}

const getGenre = (item: Thing) => {
    let meta = item.metadata.themoviedb;
    if (hasTmdbMovie(item)) {
        // return R.view<Props, Movie>(this.tmdbMovieView, this.props).genres;
        return meta.movie.genres;
    } else if (hasTmdbShow(item)) {
        return meta.show.genres; 
    } else {
        return;
    }
}

const getBackdropImagePath = (item: Thing) => {
    let meta = item.metadata.themoviedb;
    if (hasTmdbMovie(item)) {
        // return R.view<Props, Movie>(this.tmdbMovieView, this.props).backdrop_path;
        return meta.movie.backdrop_path;
    } else if (hasTmdbShow(item)) {
        return meta.show.backdrop_path; 
    } else if (hasTmdbPerson(item)) {
        return; // There are no backdrop photos for people yet
    }
}

const getRatingPath = (item: Thing) => {
    let meta = item.metadata.themoviedb;
    if (hasTmdbMovie(item)) {
        // return R.view<Props, Movie>(this.tmdbMovieView, this.props).vote_average;
        return meta.movie.vote_average;
    } else if (hasTmdbShow(item)) {
        return meta.show.vote_average;
    } else if (hasTmdbPerson(item)) {
        return; // There is no ratings for people...yet?
    }
}

const getAvailabilityInfo = (item: Thing) => {
    return item.availability;
}

// Does the item belong to any of the users lists?
const belongsToLists = (item: Thing) => {
    return item.userMetadata.belongsToLists.length > 0 ? true : false;
}

export default {
    belongsToLists,
    getPosterPath,
    getReleaseYear,
    getSeasonCount,
    getSeasons,
    getEpisodeCount,
    getRuntime,
    getVoteCount,
    getDescription,
    getCast,
    getGenre,
    getBackdropImagePath,
    getRatingPath,
    getAvailabilityInfo
};
