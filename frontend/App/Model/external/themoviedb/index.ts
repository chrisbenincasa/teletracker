export * from './Tv';
export * from './Movie';
export * from './Person';
export * from './Common';
export * from './Search';

import {Movie} from './Movie';
import {TvShow} from './Tv';
import {Person} from './Person';

export const Guards = {
    isMovie: (item: Movie | TvShow | Person): item is Movie => {
        return (<Movie>item).title !== undefined;
    },
    isTvShow: (item: Movie | TvShow | Person): item is TvShow => {
        return (<TvShow>item).name !== undefined;
    }
}