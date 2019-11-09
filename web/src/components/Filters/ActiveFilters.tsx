import React from 'react';
import { Chip, createStyles, withStyles, WithStyles } from '@material-ui/core';
import { RouteComponentProps, withRouter } from 'react-router-dom';
import { Genre, ItemType, ListSortOptions, NetworkType } from '../../types';
import { updateMultipleUrlParams } from '../../utils/urlHelper';
import _ from 'lodash';
import { DEFAULT_FILTER_PARAMS, FilterParams } from '../../utils/searchFilters';
import { setsEqual } from '../../utils/sets';

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
}

interface RouteParams {
  id: string;
}

type Props = OwnProps &
  WithStyles<typeof styles> &
  RouteComponentProps<RouteParams>;

function ActiveFilters(props: Props) {
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
    sort: ListSortOptions,
  ): [ListSortOptions | undefined, boolean] => {
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

  const removeFilters = (filters: {
    sort?: ListSortOptions;
    network?: NetworkType[];
    type?: ItemType[];
    genre?: number[];
    releaseYearMin?: true;
    releaseYearMax?: true;
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

    let paramUpdates: [string, any | undefined][] = [];
    // Specified a genre to remove and actually changed the value.
    if (genreChanged) {
      paramUpdates.push(['genres', newGenre]);
    }

    if (networksChanged) {
      paramUpdates.push(['networks', newNetworks]);
    }

    if (sortChanged) {
      paramUpdates.push(['sort', newSort]);
    }

    if (typesChanged) {
      paramUpdates.push(['type', newType]);
    }

    if (filters.releaseYearMin) {
      paramUpdates.push(['ry_min', undefined]);
    }

    if (filters.releaseYearMin) {
      paramUpdates.push(['ry_max', undefined]);
    }

    updateMultipleUrlParams(props, paramUpdates);

    let releaseYearStateNew = props.filters.sliders
      ? props.filters.sliders.releaseYear || {}
      : {};
    if (filters.releaseYearMin) {
      delete releaseYearStateNew.min;
    }

    if (filters.releaseYearMax) {
      delete releaseYearStateNew.max;
    }

    let filterParams: FilterParams = {
      sortOrder: (sortChanged
        ? newSort
        : props.filters.sortOrder) as ListSortOptions,
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
    filters: { genresFilter, itemTypes, networks, sortOrder, sliders },
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

  const showReleaseYearSlider = Boolean(
    sliders &&
      sliders.releaseYear &&
      (sliders.releaseYear.min || sliders.releaseYear.max),
  );

  const showReset = Boolean(
    showSort || showGenreFilters || showNetworkFilters || showTypeFilters,
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
              label={network}
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
              label={type}
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
    </div>
  );
}

export default withStyles(styles)(withRouter(ActiveFilters));
