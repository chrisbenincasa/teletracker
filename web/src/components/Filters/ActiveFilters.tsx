import React from 'react';
import { Chip, createStyles, withStyles, WithStyles } from '@material-ui/core';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import {
  Genre,
  ItemType,
  ItemTypeEnum,
  networkToPrettyName,
  NetworkType,
  SortOptions,
  toItemTypeEnum,
} from '../../types';
import { updateMultipleUrlParams } from '../../utils/urlHelper';
import _ from 'lodash';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../../utils/searchFilters';
import { setsEqual } from '../../utils/sets';
import { useSelector } from 'react-redux';
import { AppState } from '../../reducers';
import { filterParamsEqual } from '../../utils/changeDetection';

const styles = () =>
  createStyles({
    activeFiltersContainer: {
      display: 'flex',
      flexWrap: 'wrap',
    },
    networkChip: {
      margin: '2px',
    },
    networkIcon: {
      width: 20,
      borderRadius: '50%',
    },
  });

interface OwnProps {
  updateFilters: (FilterParams) => void;
  genres?: Genre[];
  isListDynamic?: boolean;
  filters: FilterParams;
  initialState?: FilterParams;
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

export const prettyItemType = (itemType: ItemType) => {
  switch (toItemTypeEnum(itemType)) {
    case ItemTypeEnum.Movie:
      return 'Movie';
    case ItemTypeEnum.Show:
      return 'Show';
  }
};

export const prettySort = (sortOption: SortOptions) => {
  switch (sortOption) {
    case 'added_time':
      return 'Added Time';
    case 'popularity':
      return 'Popularity';
    case 'default':
      return 'Default';
    case 'recent':
      return 'Release Date';
  }
};

function ActiveFilters(props: Props) {
  let personNameBySlugOrId = useSelector(
    (state: AppState) => state.people.nameByIdOrSlug,
  );

  const deleteNetworkFilter = (
    network?: NetworkType[],
  ): [NetworkType[] | undefined, boolean] => {
    let {
      filters: { networks },
    } = props;
    if (!network) {
      return [networks, false];
    }

    // TODO: Put somewhere constant/common
    let networkList: NetworkType[] = [
      'hbo-go',
      'hbo-now',
      'netflix',
      'netflix-kids',
      'hulu',
    ];

    if (!networks) {
      networks = networkList;
    }

    let networkDiff = _.difference(networks, network);

    return [
      networkDiff.length === 0 ? undefined : networkDiff,
      !setsEqual(networkDiff, networks),
    ];
  };

  const deleteTypeFilter = (
    type?: ItemType[],
  ): [ItemType[] | undefined, boolean] => {
    let {
      filters: { itemTypes },
    } = props;
    let typeList: ItemType[] = ['movie', 'show'];

    if (!type) {
      return [itemTypes, false];
    }

    if (!itemTypes) {
      itemTypes = typeList;
    }

    const typeDiff = _.difference(itemTypes, type);

    return [
      typeDiff.length === 0 ? undefined : typeDiff,
      !setsEqual(typeDiff, itemTypes),
    ];
  };

  const deletePersonFilter = (
    newPeople?: string[],
  ): [string[] | undefined, boolean] => {
    let {
      filters: { people },
    } = props;

    if (!newPeople) {
      return [people, false];
    }

    if (!people) {
      people = [];
    }

    const diff = _.difference(people, newPeople);

    return [diff.length === 0 ? undefined : diff, !setsEqual(diff, people)];
  };

  const deleteGenreFilter = (
    genresToRemove: number[],
  ): [number[] | undefined, boolean] => {
    const {
      filters: { genresFilter },
    } = props;

    // If there are set genres, remove them. Then return the new set.
    if (genresFilter && genresFilter.length > 0) {
      let diff = _.difference(genresFilter, genresToRemove);
      return [diff, !setsEqual(diff, genresFilter)];
    }

    // Nothing changed.
    return [undefined, false];
  };

  const deleteSort = (
    sort: SortOptions,
  ): [SortOptions | undefined, boolean] => {
    const {
      filters: { sortOrder },
    } = props;
    const cleanSort = sort === 'default' ? undefined : sort;

    if (sort !== sortOrder) {
      return [cleanSort, true];
    }

    return [sort, false];
  };

  const applyDiffer = <T extends unknown>(
    value: T | undefined,
    fn: (v: T) => [T | undefined, boolean],
  ): [T | undefined, boolean] => {
    return value ? fn(value) : [undefined, false];
  };

  const resetFilters = () => {
    updateMultipleUrlParams(props, [
      ['genres', undefined],
      ['networks', undefined],
      ['sort', undefined],
      ['type', undefined],
      ['ry_min', undefined],
      ['ry_max', undefined],
    ]);

    props.updateFilters(DEFAULT_FILTER_PARAMS);
  };

  const resetToDefaults = () => {
    props.updateFilters(props.initialState!);
  };

  const removeFilters = (filters: {
    sort?: SortOptions;
    network?: NetworkType[];
    type?: ItemType[];
    genre?: number[];
    releaseYearMin?: true;
    releaseYearMax?: true;
    people?: string[];
  }) => {
    const [newSort, sortChanged] = applyDiffer(filters.sort, deleteSort);
    const [newNetworks, networksChanged] = applyDiffer(
      filters.network,
      deleteNetworkFilter,
    );
    const [newType, typesChanged] = applyDiffer(filters.type, deleteTypeFilter);
    const [newGenre, genreChanged] = applyDiffer(
      filters.genre,
      deleteGenreFilter,
    );
    const [newPeople, peopleChanged] = applyDiffer(
      filters.people,
      deletePersonFilter,
    );

    let releaseYearStateNew = props.filters.sliders
      ? { ...props.filters.sliders.releaseYear } || {}
      : {};

    if (filters.releaseYearMin) {
      releaseYearStateNew.min = undefined;
    }

    if (filters.releaseYearMax) {
      releaseYearStateNew.max = undefined;
    }

    let filterParams: FilterParams = {
      sortOrder: (sortChanged
        ? newSort
        : props.filters.sortOrder) as SortOptions,
      networks: (networksChanged
        ? newNetworks
        : props.filters.networks) as NetworkType[],
      itemTypes: (typesChanged
        ? newType
        : props.filters.itemTypes) as ItemType[],
      genresFilter: (genreChanged
        ? newGenre
        : props.filters.genresFilter) as number[],
      sliders: {
        ...props.filters.sliders,
        releaseYear: releaseYearStateNew,
      },
      people: peopleChanged ? newPeople : props.filters.people,
    };

    props.updateFilters(filterParams);
  };

  const mapGenre = (genre: number) => {
    const { genres } = props;
    const genreItem = genres && genres.find(obj => obj.id === genre);
    return (genreItem && genreItem.name) || '';
  };

  const {
    classes,
    isListDynamic,
    filters: { genresFilter, itemTypes, networks, sortOrder, sliders, people },
    initialState,
  } = props;

  let releaseYearMin =
    sliders && sliders.releaseYear ? sliders.releaseYear.min : undefined;
  let releaseYearMax =
    sliders && sliders.releaseYear ? sliders.releaseYear.max : undefined;

  const sortLabels = {
    added_time: 'Date Added',
    popularity: 'Popularity',
    recent: 'Release Date',
  };

  const showGenreFilters = Boolean(genresFilter && genresFilter.length > 0);
  const showNetworkFilters = Boolean(networks && networks.length > 0);
  const showTypeFilters = Boolean(itemTypes && itemTypes.length > 0);
  const showSort = Boolean(
    !(
      (isListDynamic && sortOrder === 'popularity') ||
      (!isListDynamic && sortOrder === 'added_time') ||
      sortOrder === 'default' ||
      sortOrder === undefined
    ),
  );

  const showPersonFilters = Boolean(people && people.length > 0);

  const showReleaseYearSlider = Boolean(
    sliders &&
      sliders.releaseYear &&
      (sliders.releaseYear.min || sliders.releaseYear.max),
  );

  const showReset = Boolean(
    showSort || showGenreFilters || showNetworkFilters || showTypeFilters,
  );

  const showResetDefaults = Boolean(
    initialState && !filterParamsEqual(initialState, props.filters),
  );

  return (
    <div className={classes.activeFiltersContainer}>
      {showGenreFilters
        ? genresFilter &&
          genresFilter.map((genre: number) => (
            <Chip
              key={genre}
              className={classes.networkChip}
              label={mapGenre(Number(genre))}
              onDelete={() => removeFilters({ genre: [genre] })}
              variant="outlined"
            />
          ))
        : null}
      {showNetworkFilters
        ? networks &&
          networks.map((network: NetworkType) => (
            <Chip
              key={network}
              icon={
                <img
                  className={classes.networkIcon}
                  src={`/images/logos/${network}/icon.jpg`}
                />
              }
              className={classes.networkChip}
              label={networkToPrettyName[network]}
              onDelete={() => removeFilters({ network: [network] })}
              variant="outlined"
            />
          ))
        : null}
      {showTypeFilters
        ? itemTypes &&
          itemTypes.map((type: ItemType) => (
            <Chip
              key={type}
              label={`Type: ${prettyItemType(type)}`}
              className={classes.networkChip}
              onDelete={() => removeFilters({ type: [type] })}
              variant="outlined"
            />
          ))
        : null}
      {showSort ? (
        <Chip
          key={sortOrder}
          label={`Sort by: ${sortLabels[sortOrder]}`}
          className={classes.networkChip}
          onDelete={() => removeFilters({ sort: 'default' })}
          variant="outlined"
        />
      ) : null}
      {showReleaseYearSlider ? (
        <React.Fragment>
          {releaseYearMin ? (
            <Chip
              key={releaseYearMin}
              label={'Released since: ' + releaseYearMin}
              className={classes.networkChip}
              onDelete={() => removeFilters({ releaseYearMin: true })}
              variant="outlined"
            />
          ) : null}
          {releaseYearMax ? (
            <Chip
              key={releaseYearMax}
              label={'Released before: ' + (releaseYearMax + 1)}
              onDelete={() => removeFilters({ releaseYearMax: true })}
              className={classes.networkChip}
              variant="outlined"
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
                className={classes.networkChip}
                onDelete={() => removeFilters({ people: [person] })}
                variant="outlined"
              />
            ) : null,
          )
        : null}
      {showReset ? (
        <Chip
          key="Reset"
          className={classes.networkChip}
          label="Reset All"
          variant="outlined"
          color="secondary"
          onClick={resetFilters}
        />
      ) : null}
      {showResetDefaults ? (
        <Chip
          key="Reset_default"
          className={classes.networkChip}
          label="Reset to Default"
          variant="outlined"
          color="secondary"
          onClick={resetFilters}
        />
      ) : null}
    </div>
  );
}

export default withStyles(styles)(withRouter(ActiveFilters));
