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

interface State {
  sortOrder: ListSortOptions;
}

class ActiveFilters extends React.PureComponent<Props> {
  deleteNetworkFilter = (
    network?: NetworkType[],
  ): [NetworkType[] | undefined, boolean] => {
    let {
      filters: { networks },
    } = this.props;
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
      setsEqual(networkDiff, networks),
    ];
  };

  deleteTypeFilter = (type?: ItemType[]): [ItemType[] | undefined, boolean] => {
    let {
      filters: { itemTypes },
    } = this.props;
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

  deleteGenreFilter = (
    genresToRemove: number[],
  ): [number[] | undefined, boolean] => {
    const {
      filters: { genresFilter },
    } = this.props;

    // If there are set genres, remove them. Then return the new set.
    if (genresFilter && genresFilter.length > 0) {
      let diff = _.difference(genresFilter, genresToRemove);
      return [diff, !setsEqual(diff, genresFilter)];
    }

    // Nothing changed.
    return [undefined, false];
  };

  deleteSort = (
    sort: ListSortOptions,
  ): [ListSortOptions | undefined, boolean] => {
    const {
      filters: { sortOrder },
    } = this.props;

    const cleanSort = sort === 'default' ? undefined : sort;

    if (sort !== sortOrder) {
      return [cleanSort, true];
    }

    return [sort, false];
  };

  applyDiffer = <T extends unknown>(
    value: T | undefined,
    fn: (v: T) => [T | undefined, boolean],
  ): [T | undefined, boolean] => {
    return value ? fn(value) : [undefined, false];
  };

  resetFilters = () => {
    updateMultipleUrlParams(this.props, [
      ['genres', undefined],
      ['networks', undefined],
      ['sort', undefined],
      ['type', undefined],
      ['ry_min', undefined],
      ['ry_max', undefined],
    ]);

    this.props.updateFilters(DEFAULT_FILTER_PARAMS);
  };

  removeFilters = (filters: {
    sort?: ListSortOptions;
    network?: NetworkType[];
    type?: ItemType[];
    genre?: number[];
    releaseYearMin?: true;
    releaseYearMax?: true;
  }) => {
    const [newSort, sortChanged] = this.applyDiffer(
      filters.sort,
      this.deleteSort,
    );
    const [newNetworks, networksChanged] = this.applyDiffer(
      filters.network,
      this.deleteNetworkFilter,
    );
    const [newType, typesChanged] = this.applyDiffer(
      filters.type,
      this.deleteTypeFilter,
    );
    const [newGenre, genreChanged] = this.applyDiffer(
      filters.genre,
      this.deleteGenreFilter,
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

    updateMultipleUrlParams(this.props, paramUpdates);

    let releaseYearStateNew = this.props.filters.sliders
      ? this.props.filters.sliders.releaseYear || {}
      : {};
    if (filters.releaseYearMin) {
      delete releaseYearStateNew.min;
    }

    if (filters.releaseYearMax) {
      delete releaseYearStateNew.max;
    }

    let filterParams: FilterParams = {
      sortOrder: newSort as ListSortOptions,
      networks: newNetworks as NetworkType[],
      itemTypes: newType as ItemType[],
      genresFilter: newGenre as number[],
      sliders: {
        ...this.props.filters.sliders,
        releaseYear: releaseYearStateNew,
      },
    };

    this.props.updateFilters(filterParams);
  };

  mapGenre = (genre: number) => {
    const { genres } = this.props;
    const genreItem = genres && genres.find(obj => obj.id === genre);
    return (genreItem && genreItem.name) || '';
  };

  render() {
    const {
      classes,
      isListDynamic,
      filters: { genresFilter, itemTypes, networks, sortOrder, sliders },
    } = this.props;

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
        sortOrder === 'default'
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
                label={this.mapGenre(Number(genre))}
                onDelete={() => this.removeFilters({ genre: [genre] })}
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
                onDelete={() => this.removeFilters({ network: [network] })}
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
                onDelete={() => this.removeFilters({ type: [type] })}
                variant="outlined"
              />
            ))
          : null}
        {showSort ? (
          <Chip
            key={sortOrder}
            label={`Sort by: ${sortLabels[sortOrder]}`}
            className={classes.networkChip}
            onDelete={() => this.removeFilters({ sort: 'default' })}
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
                onDelete={() => this.removeFilters({ releaseYearMin: true })}
                variant="outlined"
              />
            ) : null}
            {releaseYearMax ? (
              <Chip
                key={releaseYearMax}
                label={'Released before: ' + (releaseYearMax + 1)}
                onDelete={() => this.removeFilters({ releaseYearMax: true })}
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
            onClick={this.resetFilters}
          />
        ) : null}
      </div>
    );
  }
}

export default withStyles(styles)(withRouter(ActiveFilters));
