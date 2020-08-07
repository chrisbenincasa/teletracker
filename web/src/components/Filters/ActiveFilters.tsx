import React, { useContext } from 'react';
import { Chip, Theme } from '@material-ui/core';
import {
  Genre,
  ItemType,
  ItemTypeEnum,
  networkToPrettyName,
  networkToColor,
  NetworkType,
  OpenRange,
  SortOptions,
  toItemTypeEnum,
} from '../../types';
import _ from 'lodash';
import {
  DEFAULT_FILTER_PARAMS,
  FilterParams,
  removeUndefinedKeys,
  SelectableNetworks,
} from '../../utils/searchFilters';
import { setsEqual } from '../../utils/sets';
import { useSelector } from 'react-redux';
import { AppState } from '../../reducers';
import { filterParamsEqual } from '../../utils/changeDetection';
import makeStyles from '@material-ui/core/styles/makeStyles';
import { useRouter } from 'next/router';
import { sortOptionToName } from './SortToggle';
import { FilterContext } from './FilterContext';
import { useGenres } from '../../hooks/useStateMetadata';
import produce from 'immer';
import { getLogoUrl } from '../../utils/image-helper';

const useStyles = makeStyles((theme: Theme) => ({
  activeFiltersContainer: {
    display: 'flex',
    flexWrap: 'wrap',
  },
  chip: {
    margin: theme.spacing(0.5, 0.5, 0, 0.5),
    backgroundColor: `${theme.palette.grey[800]}`,
  },
  networkIcon: {
    width: 48,
    maxHeight: 24,
  },
  resetChip: {
    backgroundColor: theme.custom.palette.cancel,
    margin: theme.spacing(0.5, 0, 0, 0.5),
  },
}));

interface Props {
  readonly isListDynamic?: boolean;
  readonly variant?: 'default' | 'outlined';
  readonly hideSortOptions?: boolean;
}

export const prettyItemType = (itemType: ItemType) => {
  switch (toItemTypeEnum(itemType)) {
    case ItemTypeEnum.Movie:
      return 'Movie';
    case ItemTypeEnum.Show:
      return 'Show';
  }
};

export const prettySort = (sortOption: SortOptions) => {
  return sortOptionToName[sortOption];
};

type FilterRemove = {
  sort?: SortOptions;
  network?: SelectableNetworks;
  type?: ItemType[];
  genre?: number[];
  releaseYearMin?: true;
  releaseYearMax?: true;
  people?: string[];
  imdbRatingMin?: true;
  imdbRatingMax?: true;
};

export default function ActiveFilters(props: Props) {
  const classes = useStyles();
  const router = useRouter();
  const filterState = useContext(FilterContext);
  const genres = useGenres();
  const { isListDynamic, variant } = props;
  const {
    filters,
    setFilters,
    defaultFilters,
    currentFiltersAreDefault,
  } = filterState;
  const {
    genresFilter,
    itemTypes,
    networks,
    sortOrder,
    sliders,
    people,
  } = filters;

  let personNameBySlugOrId = useSelector(
    (state: AppState) => state.people.nameByIdOrSlug,
  );

  const deleteNetworkFilter = (
    network?: SelectableNetworks,
  ): [SelectableNetworks | undefined, boolean] => {
    if (_.isUndefined(network)) {
      // Trying to remove nothing, return unchanged.
      return [networks, false];
    } else if (network === 'all') {
      // Removing all, reset to nothing.
      return [undefined, true];
    } else {
      // If the existing stuff is undefined or all, we can't remove anything,
      // this is in invalid state.
      if (_.isUndefined(networks) || networks === 'all') {
        return [networks, false];
      } else {
        // Take the difference of the current networks and the ones we're
        // attempting to remove.
        let networkDiff = _.difference(networks, network);
        return [
          networkDiff.length === 0 ? undefined : networkDiff,
          !setsEqual(networkDiff, networks),
        ];
      }
    }
  };

  const deleteTypeFilter = (
    type?: ItemType[],
  ): [ItemType[] | undefined, boolean] => {
    let typeList: ItemType[] = ['movie', 'show'];

    if (!type) {
      return [itemTypes, false];
    }

    const itemTypesInFilter = itemTypes || typeList;

    const typeDiff = _.difference(itemTypes, type);

    return [typeDiff, !setsEqual(typeDiff, itemTypesInFilter)];
  };

  const deletePersonFilter = (
    newPeople?: string[],
  ): [string[] | undefined, boolean] => {
    if (!newPeople) {
      return [people, false];
    }

    const peopleInFilter = people || [];

    const diff = _.difference(peopleInFilter, newPeople);

    return [
      diff.length === 0 ? undefined : diff,
      !setsEqual(diff, peopleInFilter),
    ];
  };

  const deleteGenreFilter = (
    genresToRemove: number[] | undefined,
  ): [number[] | undefined, boolean] => {
    // If there are set genres, remove them. Then return the new set.
    if (genresFilter && genresFilter.length > 0) {
      let diff = _.difference(genresFilter, genresToRemove || []);
      return [diff, !setsEqual(diff, genresFilter)];
    }

    // Nothing changed.
    return [undefined, false];
  };

  const deleteSort = (
    sort: SortOptions | undefined,
  ): [SortOptions | undefined, boolean] => {
    if (sort !== sortOrder) {
      return [sort, true];
    }

    return [sort, false];
  };

  const resetFilters = () => {
    setFilters(_.extend({}, defaultFilters || {}));
  };

  const removeFilters = (filters: FilterRemove) => {
    const [newSort, sortChanged] = deleteSort(filters.sort);
    const [newNetworks, networksChanged] = deleteNetworkFilter(filters.network);
    const [newType, typesChanged] = deleteTypeFilter(filters.type);
    const [newGenre, genreChanged] = deleteGenreFilter(filters.genre);
    const [newPeople, peopleChanged] = deletePersonFilter(filters.people);

    let releaseYearStateNew: OpenRange | undefined;
    if (filterState.filters.sliders?.releaseYear) {
      releaseYearStateNew = produce(
        filterState.filters.sliders.releaseYear,
        draft => {
          if (filters.releaseYearMin) {
            delete draft.min;
          }

          if (filters.releaseYearMax) {
            delete draft.max;
          }
        },
      );
    }

    let imdbRatingStateNew: OpenRange | undefined;
    if (filterState.filters.sliders?.imdbRating) {
      imdbRatingStateNew = produce(
        filterState.filters.sliders.imdbRating,
        draft => {
          if (filters.imdbRatingMin) {
            delete draft.min;
          }

          if (filters.imdbRatingMax) {
            delete draft.max;
          }
        },
      );
    }

    let filterParams: FilterParams = removeUndefinedKeys({
      sortOrder: (sortChanged
        ? newSort
        : filterState.filters.sortOrder) as SortOptions,
      networks: (networksChanged
        ? newNetworks
        : filterState.filters.networks) as NetworkType[],
      itemTypes: (typesChanged
        ? newType
        : filterState.filters.itemTypes) as ItemType[],
      genresFilter: (genreChanged
        ? newGenre
        : filterState.filters.genresFilter) as number[],
      sliders: {
        ...filterState.filters.sliders,
        releaseYear: releaseYearStateNew,
        imdbRating: imdbRatingStateNew,
      },
      people: peopleChanged ? newPeople : filterState.filters.people,
    });

    setFilters(
      _.extend({}, defaultFilters || DEFAULT_FILTER_PARAMS, filterParams),
    );
  };

  const mapGenre = (genre: number) => {
    const genreItem = genres && genres.find(obj => obj.id === genre);
    return (genreItem && genreItem.name) || '';
  };

  let releaseYearMin = sliders?.releaseYear?.min;
  let releaseYearMax = sliders?.releaseYear?.max;
  let imdbMin = sliders?.imdbRating?.min;
  let imdbMax = sliders?.imdbRating?.max;

  const showGenreFilters =
    !currentFiltersAreDefault &&
    Boolean(genresFilter && genresFilter.length > 0);
  const showNetworkFilters =
    !currentFiltersAreDefault && Boolean(networks && networks.length > 0);
  const showTypeFilters =
    !currentFiltersAreDefault && Boolean(itemTypes && itemTypes.length > 0);
  const showSort = defaultFilters?.sortOrder !== sortOrder;
  const showPersonFilters =
    !currentFiltersAreDefault && Boolean(people && people.length > 0);
  const showOfferTypes =
    !currentFiltersAreDefault &&
    Boolean(filters.offers?.types && filters.offers?.types?.length > 0);

  const showReleaseYearSlider =
    !currentFiltersAreDefault &&
    Boolean(
      !_.isUndefined(sliders?.releaseYear?.min) ||
        !_.isUndefined(sliders?.releaseYear?.max),
    );

  const showImdbSlider =
    !currentFiltersAreDefault &&
    Boolean(
      !_.isUndefined(sliders?.imdbRating?.min) ||
        !_.isUndefined(sliders?.imdbRating?.max),
    );

  const showReset = Boolean(
    showSort ||
      showGenreFilters ||
      showNetworkFilters ||
      showTypeFilters ||
      showImdbSlider ||
      showOfferTypes,
  );

  const renderSelectedNetworks = (selectedNetworks: NetworkType[] | 'all') => {
    if (selectedNetworks === 'all') {
      return (
        <Chip
          key="all"
          className={classes.chip}
          label="All Networks"
          onDelete={() => removeFilters({ network: 'all' })}
          variant={variant}
        />
      );
    } else {
      return selectedNetworks.map((network: NetworkType) => (
        <Chip
          key={network}
          icon={
            <div
              style={{
                padding: '1px 5px',
                backgroundColor: networkToColor[network],
                borderRadius: 8,
              }}
            >
              <img
                className={classes.networkIcon}
                src={getLogoUrl(network)}
                alt={network}
              />
            </div>
          }
          className={classes.chip}
          label={networkToPrettyName[network]}
          onDelete={() => removeFilters({ network: [network] })}
          variant={variant}
        />
      ));
    }
  };

  return (
    <div className={classes.activeFiltersContainer}>
      {showGenreFilters
        ? genresFilter &&
          genresFilter.map((genre: number) => (
            <Chip
              key={genre}
              className={classes.chip}
              label={mapGenre(Number(genre))}
              onDelete={() => removeFilters({ genre: [genre] })}
              variant={variant}
            />
          ))
        : null}
      {showNetworkFilters
        ? !_.isUndefined(networks) && renderSelectedNetworks(networks)
        : null}
      {showTypeFilters
        ? itemTypes &&
          itemTypes.map((type: ItemType) => (
            <Chip
              key={type}
              label={`Type: ${prettyItemType(type)}`}
              className={classes.chip}
              onDelete={() => removeFilters({ type: [type] })}
              variant={variant}
            />
          ))
        : null}
      {showSort && sortOrder ? (
        <Chip
          key={sortOrder}
          label={`Sort by: ${prettySort(sortOrder)}`}
          className={classes.chip}
          onDelete={() => removeFilters({ sort: undefined })}
          variant={variant}
        />
      ) : null}
      {showReleaseYearSlider ? (
        <React.Fragment>
          {releaseYearMin ? (
            <Chip
              key={releaseYearMin}
              label={'Released since: ' + releaseYearMin}
              className={classes.chip}
              onDelete={() => removeFilters({ releaseYearMin: true })}
              variant={variant}
            />
          ) : null}
          {releaseYearMax ? (
            <Chip
              key={releaseYearMax}
              label={'Released before: ' + (releaseYearMax + 1)}
              onDelete={() => removeFilters({ releaseYearMax: true })}
              className={classes.chip}
              variant={variant}
            />
          ) : null}
        </React.Fragment>
      ) : null}
      {showImdbSlider ? (
        <React.Fragment>
          {!_.isUndefined(imdbMin) && _.isUndefined(imdbMax) ? (
            <Chip
              key={imdbMin}
              label={'IMDb Rating higher than: ' + imdbMin}
              className={classes.chip}
              onDelete={() => removeFilters({ imdbRatingMin: true })}
              variant={variant}
            />
          ) : null}
          {!_.isUndefined(imdbMax) && _.isUndefined(imdbMin) ? (
            <Chip
              key={imdbMax}
              label={'IMDb Rating lower than ' + imdbMax}
              onDelete={() => removeFilters({ imdbRatingMax: true })}
              className={classes.chip}
              variant={variant}
            />
          ) : null}
          {!_.isUndefined(imdbMax) && !_.isUndefined(imdbMin) ? (
            <Chip
              key={`${imdbMin}_${imdbMax}`}
              label={'IMDb Rating between : ' + imdbMin + ' and ' + imdbMax}
              onDelete={() =>
                removeFilters({ imdbRatingMin: true, imdbRatingMax: true })
              }
              className={classes.chip}
              variant={variant}
            />
          ) : null}
        </React.Fragment>
      ) : null}
      {showPersonFilters
        ? people &&
          people.map((person: string) =>
            personNameBySlugOrId[person] ? (
              <Chip
                key={person}
                label={`Starring: ${personNameBySlugOrId[person]}`}
                className={classes.chip}
                clickable
                onClick={() => router.push(`/person/${person}`)}
                onDelete={() => removeFilters({ people: [person] })}
                variant={variant}
              />
            ) : null,
          )
        : null}
      {showOfferTypes
        ? filters.offers?.types &&
          filters.offers.types.map(ot => (
            <Chip
              key={ot}
              label={'Offer: ' + _.capitalize(ot)}
              className={classes.chip}
              clickable
              variant={variant}
            />
          ))
        : null}
      {showReset ? (
        <Chip
          key="Reset"
          className={classes.resetChip}
          label="Reset"
          variant={variant}
          color="default"
          onClick={resetFilters}
        />
      ) : null}
    </div>
  );
}

ActiveFilters.defaultProps = { variant: 'outlined' };
