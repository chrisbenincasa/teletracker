import { Chip, createStyles, withStyles, WithStyles } from '@material-ui/core';
import { withRouter, RouteComponentProps } from 'react-router-dom';
import { Genre, ListSortOptions, ItemTypes, NetworkTypes } from '../../types';
import React, { Component } from 'react';
import { updateURLParameters } from '../../utils/urlHelper';
import _ from 'lodash';

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
  updateFilters: (
    sortOrder: ListSortOptions,
    networkTypes?: NetworkTypes[],
    type?: ItemTypes[],
    genres?: number[],
  ) => void;
  genresFilter?: number[];
  genres?: Genre[];
  itemTypes?: ItemTypes[];
  isListDynamic?: boolean;
  networks?: NetworkTypes[];
  sortOrder: ListSortOptions;
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

class ActiveFilters extends Component<Props, State> {
  deleteNetworkFilter = (network?: NetworkTypes[], reset?: boolean) => {
    let { networks } = this.props;
    let networkList: NetworkTypes[] = [
      'hbo-go',
      'hbo-now',
      'netflix',
      'netflix-kids',
      'hulu',
    ];

    if (!networks) {
      networks = networkList;
    }

    if (reset) {
      updateURLParameters(this.props, 'networks', undefined);
    }

    if (network && network === networks) {
      return network;
    } else if (network && network !== networks) {
      const networkDiff = _.difference(networks, network);

      updateURLParameters(this.props, 'networks', networkDiff);
      return networkDiff;
    } else {
      updateURLParameters(this.props, 'networks', undefined);
      return undefined;
    }
  };

  deleteTypeFilter = (type?: ItemTypes[], reset?: boolean) => {
    let { itemTypes } = this.props;
    let typeList: ItemTypes[] = ['movie', 'show'];

    if (!itemTypes) {
      itemTypes = typeList;
    }

    if (reset) {
      return undefined;
    }

    if (type && type === itemTypes) {
      return type;
    } else if (type && type !== itemTypes) {
      const typeDiff = _.difference(itemTypes, type);
      updateURLParameters(this.props, 'type', typeDiff);
      return typeDiff;
    } else {
      updateURLParameters(this.props, 'type', undefined);
      return undefined;
    }
  };

  deleteGenreFilter = (genre: number[], reset?: boolean) => {
    const { genresFilter } = this.props;

    if (genre === genresFilter) {
      return genre;
    }

    if (!reset && genresFilter && genresFilter.length > 0) {
      const genreDiff = _.difference(genresFilter, genre);
      updateURLParameters(this.props, 'genres', genreDiff);
      return genreDiff;
    }
    return genre;
  };

  deleteSort = (sort: ListSortOptions, reset?: boolean) => {
    const { sortOrder } = this.props;
    const cleanSort = sort === 'default' ? undefined : sort;

    if (!reset && sort !== sortOrder) {
      updateURLParameters(this.props, 'sort', cleanSort);
    }
    return sort;
  };

  removeFilters = (
    sort: ListSortOptions,
    network?: NetworkTypes[],
    type?: ItemTypes[],
    genre?: number[],
    reset?: boolean,
  ) => {
    const { location } = this.props;
    const newSort = sort && this.deleteSort(sort, reset);
    const newNetworks = network && this.deleteNetworkFilter(network, reset);
    const newType = type && this.deleteTypeFilter(type, reset);
    const newGenre = genre && this.deleteGenreFilter(genre, reset);
    console.log(sort, network, type, genre);
    /*
    The reset parameter is a temporary workaround to a larger problem.  updateURLParameters helper does not currently support updating the history concurrently.  So when the above three functions run, only the last one actually applies the new parameter updates to the URL.
    */
    if (reset) {
      let params = new URLSearchParams(location.search);
      params.delete('sort');
      params.delete('type');
      params.delete('networks');
      params.delete('genres');

      this.props.history.replace(`?${params}`);
    }

    this.props.updateFilters(newSort, newNetworks, newType, newGenre);
  };

  mapGenre = (genre: number) => {
    const { genres } = this.props;
    const genreItem = genres && genres.find(obj => obj.id === genre);
    const genreName = (genreItem && genreItem.name) || '';

    return genreName;
  };

  render() {
    const {
      classes,
      genresFilter,
      itemTypes,
      isListDynamic,
      networks,
      sortOrder,
    } = this.props;

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
                onDelete={() =>
                  this.removeFilters(
                    sortOrder,
                    networks,
                    itemTypes,
                    [genre],
                    false,
                  )
                }
                variant="outlined"
              />
            ))
          : null}
        {showNetworkFilters
          ? networks &&
            networks.map((network: NetworkTypes) => (
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
                onDelete={() =>
                  this.removeFilters(
                    sortOrder,
                    [network],
                    itemTypes,
                    genresFilter,
                    false,
                  )
                }
                variant="outlined"
              />
            ))
          : null}
        {showTypeFilters
          ? itemTypes &&
            itemTypes.map((type: ItemTypes) => (
              <Chip
                key={type}
                label={type}
                className={classes.networkChip}
                onDelete={() =>
                  this.removeFilters(
                    sortOrder,
                    networks,
                    [type],
                    genresFilter,
                    false,
                  )
                }
                variant="outlined"
              />
            ))
          : null}
        {showSort ? (
          <Chip
            key={sortOrder}
            label={`Sort by: ${sortLabels[sortOrder]}`}
            className={classes.networkChip}
            onDelete={() =>
              this.removeFilters(
                'default',
                networks,
                itemTypes,
                genresFilter,
                false,
              )
            }
            variant="outlined"
          />
        ) : null}
        {showReset ? (
          <Chip
            key="Reset"
            className={classes.networkChip}
            label="Reset All"
            variant="outlined"
            color="secondary"
            onClick={() =>
              this.removeFilters(
                'default',
                undefined,
                undefined,
                undefined,
                true,
              )
            }
          />
        ) : null}
      </div>
    );
  }
}

export default withStyles(styles)(withRouter(ActiveFilters));
